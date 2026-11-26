CREATE TABLE system_bootstrap (id INT PRIMARY KEY, admin_claimed BOOLEAN NOT NULL);
INSERT INTO system_bootstrap SELECT 1, EXISTS(SELECT 1 FROM sys_user WHERE deleted=0);
