"""Validate space DDL in a disposable local MySQL database; never migrate cloud."""
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import uuid


ROOT = Path(__file__).resolve().parents[1]
DATABASE = "codex_space_test_" + uuid.uuid4().hex[:12]
assert re.fullmatch(r"codex_space_test_[0-9a-f]{12}", DATABASE)
config = (ROOT / "src/main/resources/application.yaml").read_text(encoding="utf-8")
datasource = config.split("  datasource:", 1)[1].split("    driver-class-name:", 1)[0]
url = re.search(r"url:\s*jdbc:mysql://([^/:]+):(\d+)/", datasource)
if not url or url[1] not in ("localhost", "127.0.0.1"):
    raise RuntimeError("This test is restricted to a loopback MySQL server")
username = re.search(r"username:\s*(.+)", datasource)[1].strip().strip("\"'")
password = re.search(r"password:\s*(.*)", datasource)[1].strip().strip("\"'")
mysql = shutil.which("mysql")
if not mysql:
    raise RuntimeError("mysql CLI is required")
environment = dict(os.environ, MYSQL_PWD=password)
checks = []


def execute(sql, database=DATABASE, expected_error=None):
    arguments = [mysql, "--host=127.0.0.1", f"--port={url[2]}", f"--user={username}",
                 "--default-character-set=utf8mb4", "--batch", "--skip-column-names"]
    if database:
        arguments.append(f"--database={database}")
    result = subprocess.run(arguments, input="SET time_zone='+00:00';\n" + sql,
                            text=True, encoding="utf-8", capture_output=True,
                            env=environment, timeout=30)
    if expected_error:
        if result.returncode == 0 or f"ERROR {expected_error} " not in result.stderr:
            raise AssertionError(f"Expected MySQL error {expected_error}: {result.stderr}")
    elif result.returncode:
        raise RuntimeError(result.stderr)
    return result.stdout.strip()


def equal(label, sql, expected):
    actual = execute(sql)
    if actual != str(expected):
        raise AssertionError(f"{label}: expected {expected!r}, got {actual!r}")
    checks.append(label)


def rejects(label, sql, error=3819):
    execute(sql, expected_error=error)
    checks.append(label)


created = False
try:
    execute(f"CREATE DATABASE `{DATABASE}` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;", database=None)
    created = True
    for name in ("V1__create_user_auth_schema.sql", "V2__fix_utc_and_primary_identity.sql",
                 "V3__create_space_schema.sql"):
        execute((ROOT / "src/main/resources/db/migration" / name).read_text(encoding="utf-8"))
        checks.append(f"replay {name}")
    execute((ROOT / "docs/database/space-p1-schema.sql").read_text(encoding="utf-8"))
    checks.append("replay P1 extension draft")
    equal("six space tables", "SELECT COUNT(*) FROM information_schema.TABLES "
          f"WHERE TABLE_SCHEMA='{DATABASE}' AND TABLE_NAME LIKE 'space%';", 6)
    equal("UTC database defaults", "SELECT TIMESTAMPDIFF(SECOND,UTC_TIMESTAMP(),NOW());", 0)
    execute("""
        INSERT INTO sys_user (id,display_name) VALUES (101,'Owner'),(102,'Member'),(103,'Unrelated');
        INSERT INTO space (id,owner_user_id,kind,name,creation_request_key) VALUES
          (1001,101,'PERSONAL','工作空间','REGISTER:101'),
          (1002,101,'PERSONAL','工作空间','request:deleted'),
          (1003,101,'SHARED','协作空间',NULL),
          (1004,101,'PRIVATE','私密空间',NULL);
    """)
    equal("same-owner duplicate names allowed", "SELECT COUNT(*) FROM space WHERE name='工作空间';", 2)
    rejects("unknown kind rejected", "INSERT INTO space(id,owner_user_id,kind,name) VALUES(1100,101,'FOLDER','Bad');")
    rejects("case-sensitive kind", "INSERT INTO space(id,owner_user_id,kind,name) VALUES(1100,101,'personal','Bad');")
    rejects("blank name rejected", "INSERT INTO space(id,owner_user_id,name) VALUES(1100,101,'   ');")
    rejects("nonpositive owner rejected", "INSERT INTO space(id,owner_user_id,name) VALUES(1100,0,'Bad');")
    rejects("invalid boolean rejected", "INSERT INTO space(id,owner_user_id,name,viewer_download_allowed) VALUES(1100,101,'Bad',2);")
    rejects("creation idempotency", "INSERT INTO space(id,owner_user_id,name,creation_request_key) VALUES(1100,101,'Bad','REGISTER:101');", 1062)
    execute("""
        INSERT INTO space_member(id,space_id,user_id,role) VALUES
          (2001,1003,101,'OWNER'),(2002,1003,102,'EDITOR');
    """)
    rejects("one active owner", "INSERT INTO space_member(id,space_id,user_id,role) VALUES(2100,1003,103,'OWNER');", 1062)
    rejects("one active member per user", "INSERT INTO space_member(id,space_id,user_id,role) VALUES(2100,1003,102,'VIEWER');", 1062)
    rejects("owner cannot expire", "INSERT INTO space_member(id,space_id,user_id,role,expires_at) VALUES(2100,1001,101,'OWNER',UTC_TIMESTAMP(3)+INTERVAL 1 DAY);")
    rejects("platform role is not resource role", "INSERT INTO space_member(id,space_id,user_id,role) VALUES(2100,1003,103,'SUPER_ADMIN');")
    execute("""
        UPDATE space_member SET deleted=id WHERE id=2002;
        INSERT INTO space_member(id,space_id,user_id,role) VALUES(2003,1003,102,'VIEWER');
        INSERT INTO space_user_preference(id,user_id,space_id,sort_order) VALUES
          (3001,101,1003,10),(3002,102,1003,20),(3003,103,1003,30);
    """)
    equal("removed member can rejoin", "SELECT COUNT(*) FROM space_member WHERE space_id=1003 AND user_id=102 AND deleted=0;", 1)
    equal("preferences independent per user", "SELECT GROUP_CONCAT(sort_order ORDER BY user_id) FROM space_user_preference WHERE space_id=1003;", "10,20,30")
    rejects("one preference per user-space", "INSERT INTO space_user_preference(id,user_id,space_id) VALUES(3100,102,1003);", 1062)
    visible = """
        SELECT COUNT(*) FROM space s WHERE s.deleted=0 AND s.status='ACTIVE' AND
        (s.owner_user_id={user} OR (s.kind='SHARED' AND EXISTS (
          SELECT 1 FROM space_member m WHERE m.space_id=s.id AND m.user_id={user} AND m.deleted=0
          AND (m.expires_at IS NULL OR m.expires_at>UTC_TIMESTAMP(3)))));
    """
    equal("member sees shared space", visible.format(user=102), 1)
    equal("preference alone never grants access", visible.format(user=103), 0)
    execute("UPDATE space_member SET joined_at=UTC_TIMESTAMP(3)-INTERVAL 2 DAY, expires_at=UTC_TIMESTAMP(3)-INTERVAL 1 DAY WHERE id=2003;")
    equal("expiry effective without cleanup", visible.format(user=102), 0)
    execute("""
        START TRANSACTION;
        SELECT id FROM space WHERE id=1001 FOR UPDATE;
        UPDATE space SET kind='SHARED',access_version=access_version+1,version=version+1 WHERE id=1001;
        INSERT INTO space_member(id,space_id,user_id,role) VALUES(2011,1001,101,'OWNER');
        COMMIT;
    """)
    equal("conversion preserves space identity", "SELECT CONCAT(kind,':',owner_user_id) FROM space WHERE id=1001;", "SHARED:101")
    rejects("generic logical delete cannot bypass lifecycle", "UPDATE space SET deleted=id WHERE id=1002;")
    execute("UPDATE space SET status='DELETING',delete_requested_at=UTC_TIMESTAMP(3),access_version=access_version+1 WHERE id=1002;")
    equal("deleting spaces excluded from navigation", "SELECT COUNT(*) FROM space WHERE id=1002 AND deleted=0 AND status='ACTIVE';", 0)
    execute("UPDATE space SET status='DELETED',deleted=id,deleted_at=UTC_TIMESTAMP(3),purge_after=UTC_TIMESTAMP(3)+INTERVAL 30 DAY WHERE id=1002;")
    equal("completed deletion uses row id", "SELECT deleted FROM space WHERE id=1002;", 1002)
    rejects("deleted creation key cannot be replayed", "INSERT INTO space(id,owner_user_id,name,creation_request_key) VALUES(1100,101,'Bad','request:deleted');", 1062)
    execute("UPDATE space SET status='ACTIVE',deleted=0,delete_requested_at=NULL,deleted_at=NULL,purge_after=NULL,access_version=access_version+1 WHERE id=1002;")
    equal("consistent recovery accepted", "SELECT COUNT(*) FROM space WHERE id=1002 AND status='ACTIVE' AND deleted=0;", 1)
    execute("""
        INSERT INTO space_invitation(id,space_id,inviter_user_id,invitee_email,role,token_hash,expires_at)
        VALUES(4001,1003,101,'member@example.com','EDITOR',REPEAT('a',64),UTC_TIMESTAMP(3)+INTERVAL 1 DAY);
    """)
    rejects("pending invitation unique by normalized email", "INSERT INTO space_invitation(id,space_id,inviter_user_id,invitee_email,role,token_hash,expires_at) VALUES(4100,1003,101,'MEMBER@example.com','EDITOR',REPEAT('b',64),UTC_TIMESTAMP(3)+INTERVAL 1 DAY);", 1062)
    rejects("accepted invitation needs accepting user", "UPDATE space_invitation SET status='ACCEPTED',processed_at=UTC_TIMESTAMP(3) WHERE id=4001;")
    rejects("invitation cannot grant ownership", "UPDATE space_invitation SET role='OWNER' WHERE id=4001;")
    execute("""
        UPDATE space_invitation SET status='ACCEPTED',processed_at=UTC_TIMESTAMP(3),accepted_by_user_id=102 WHERE id=4001;
        INSERT INTO space_invitation(id,space_id,inviter_user_id,invitee_email,role,token_hash,expires_at)
        VALUES(4002,1003,101,'member@example.com','VIEWER',REPEAT('b',64),UTC_TIMESTAMP(3)+INTERVAL 1 DAY);
        INSERT INTO space_private_security(id,space_id,password_hash) VALUES(5001,1004,'test-adaptive-hash');
    """)
    equal("terminal invitation releases pending key", "SELECT COUNT(*) FROM space_invitation WHERE space_id=1003;", 2)
    rejects("one active private configuration", "INSERT INTO space_private_security(id,space_id,password_hash) VALUES(5100,1004,'hash');", 1062)
    rejects("idle timeout cannot exceed absolute maximum", "UPDATE space_private_security SET idle_timeout_seconds=7200,max_session_seconds=3600 WHERE id=5001;")
    execute("""
        INSERT INTO auth_session(id,user_id,refresh_token_hash,refresh_expires_at)
        VALUES(8001,101,REPEAT('e',64),UTC_TIMESTAMP(3)+INTERVAL 1 DAY);
        INSERT INTO space_unlock_session(id,space_id,user_id,auth_session_id,token_hash,credential_version,
          space_access_version,last_used_at,idle_expires_at,expires_at)
        VALUES(6001,1004,101,8001,REPEAT('c',64),1,0,UTC_TIMESTAMP(3),
          UTC_TIMESTAMP(3)+INTERVAL 15 MINUTE,UTC_TIMESTAMP(3)+INTERVAL 1 HOUR);
    """)
    rejects("idle deadline cannot exceed absolute expiry", "UPDATE space_unlock_session SET idle_expires_at=expires_at+INTERVAL 1 SECOND WHERE id=6001;")
    rejects("revocation needs reason", "UPDATE space_unlock_session SET revoked_at=UTC_TIMESTAMP(3) WHERE id=6001;")
    authorized = """
        SELECT COUNT(*) FROM space_unlock_session us
        JOIN space s ON s.id=us.space_id AND s.owner_user_id=us.user_id AND s.status='ACTIVE' AND s.deleted=0 AND s.kind='PRIVATE'
        JOIN space_private_security ps ON ps.space_id=s.id AND ps.deleted=0 AND ps.credential_version=us.credential_version
        JOIN auth_session a ON a.id=us.auth_session_id AND a.user_id=us.user_id AND a.deleted=0 AND a.revoked_at IS NULL
          AND a.refresh_expires_at>UTC_TIMESTAMP(3)
        WHERE us.id=6001 AND us.deleted=0 AND us.revoked_at IS NULL AND us.expires_at>UTC_TIMESTAMP(3)
          AND us.idle_expires_at>UTC_TIMESTAMP(3) AND us.space_access_version=s.access_version;
    """
    equal("matching private grant is usable", authorized, 1)
    execute("UPDATE space_private_security SET credential_version=credential_version+1 WHERE id=5001;")
    equal("credential rotation invalidates old grants immediately", authorized, 0)
    execute("UPDATE space_private_security SET credential_version=1 WHERE id=5001; UPDATE auth_session SET revoked_at=UTC_TIMESTAMP(3) WHERE id=8001;")
    equal("account logout invalidates private grant", authorized, 0)
    execute("UPDATE auth_session SET revoked_at=NULL WHERE id=8001; UPDATE space SET access_version=access_version+1 WHERE id=1004;")
    equal("space authorization version invalidates private grant", authorized, 0)
finally:
    if created:
        # Only the exact database name generated and validated above can be dropped.
        execute(f"DROP DATABASE `{DATABASE}`;", database=None)

report = {"status": "passed", "checks": len(checks), "database": DATABASE,
          "temporary_database_removed": True, "current_cloud_database_modified": False,
          "details": checks}
report_path = ROOT / "target/space-schema-check.json"
report_path.parent.mkdir(parents=True, exist_ok=True)
report_path.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
print(json.dumps({k: v for k, v in report.items() if k != "details"}, ensure_ascii=False))
