package com.wanyue.cloud.modules.ops;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.wanyue.cloud.modules.auth.support.JwtTokenProvider;
import com.wanyue.cloud.modules.drive.DriveService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 需求对照与去硬编码专项测试：
 * 验证动态存储路由热切换（LOCAL <-> DB）、动态上传/后缀黑名单/深度策略实时生效、
 * 以及后台认证策略（关闭注册、关闭密码登录）实时拦截生效。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class DynamicPolicyAndStorageRoutingTest {
    @MockitoBean com.wanyue.cloud.common.config.RedisStartupCheck redisStartupCheck;
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate db;
    @Autowired JwtTokenProvider jwt;
    @Autowired SystemPolicyService policyService;
    @Autowired DriveService driveService;

    long adminId;
    long userId;
    long spaceId;
    String adminToken;
    String userToken;

    @BeforeEach
    void setup() {
        policyService.invalidateCache();
        adminId = IdWorker.getId();
        userId = IdWorker.getId();
        spaceId = IdWorker.getId();
        db.update("INSERT INTO sys_user(id,display_name,status,deleted) VALUES (?,'策略超管',1,0)", adminId);
        db.update("INSERT INTO auth_user_security(id,user_id) VALUES (?,?)", IdWorker.getId(), adminId);
        db.update("INSERT INTO auth_user_role(id,user_id,role_id) VALUES (?,?,1)", IdWorker.getId(), adminId);

        db.update("INSERT INTO sys_user(id,display_name,status,deleted) VALUES (?,'普通成员',1,0)", userId);
        db.update("INSERT INTO auth_user_security(id,user_id) VALUES (?,?)", IdWorker.getId(), userId);
        db.update("INSERT INTO space(id,owner_user_id,kind,name,description,icon,color,sort_order,created_by) VALUES (?,?,'PERSONAL','动态路由空间','','','#6366f1',0,?)",
                spaceId, userId, userId);

        adminToken = jwt.createAccessToken(adminId, 0);
        userToken = jwt.createAccessToken(userId, 0);
    }

    @Test
    void switchingDefaultStorageToDbRoutesNewUploadsAndPreservesHistoricalReads() throws Exception {
        // 1. 先在默认 LOCAL (id=1) 上传旧文件
        MockMultipartFile legacy = new MockMultipartFile("file", "legacy.txt", "text/plain",
                "legacy-local-content".getBytes(StandardCharsets.UTF_8));
        String legacyResp = mvc.perform(multipart("/api/v1/spaces/" + spaceId + "/files").file(legacy)
                        .param("parentId", "0").header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String legacyNodeId = new tools.jackson.databind.json.JsonMapper().readTree(legacyResp).get("data").get("id").asString();

        // 2. 管理员创建 DB 存储实例并切换为默认存储
        String dbCreated = mvc.perform(post("/api/v1/admin/storage-profiles").header("Authorization", "Bearer " + adminToken)
                        .contentType("application/json")
                        .content("{\"name\":\"动态DB存储\",\"provider\":\"DB\",\"maxFileBytes\":2097152}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String dbProfileId = new tools.jackson.databind.json.JsonMapper().readTree(dbCreated).get("data").asString();

        mvc.perform(put("/api/v1/admin/storage-profiles/" + dbProfileId + "/default").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        // 3. 新上传文件自动路由到 DB 存储实例，且 blob_object.storage_profile_id == dbProfileId
        MockMultipartFile dbFile = new MockMultipartFile("file", "in-db.txt", "text/plain",
                "stored-in-mysql-blob".getBytes(StandardCharsets.UTF_8));
        String dbNodeResp = mvc.perform(multipart("/api/v1/spaces/" + spaceId + "/files").file(dbFile)
                        .param("parentId", "0").header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String dbNodeId = new tools.jackson.databind.json.JsonMapper().readTree(dbNodeResp).get("data").get("id").asString();

        Long recordedProfileId = db.queryForObject(
                "SELECT b.storage_profile_id FROM file_node n JOIN file_version v ON v.id=n.current_version_id JOIN blob_object b ON b.id=v.blob_id WHERE n.id=?",
                Long.class, Long.parseLong(dbNodeId));
        assertEquals(Long.parseLong(dbProfileId), recordedProfileId);

        // 4. 新旧两个存储实例上的文件均可正常在线读取内容
        mvc.perform(get("/api/v1/spaces/" + spaceId + "/nodes/" + legacyNodeId + "/text").header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").value("legacy-local-content"));
        mvc.perform(get("/api/v1/spaces/" + spaceId + "/nodes/" + dbNodeId + "/text").header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").value("stored-in-mysql-blob"));
    }

    @Test
    void dynamicFilePoliciesAndAuthPoliciesTakeEffectImmediately() throws Exception {
        // 1. 后台修改禁止上传后缀为 exe,bat，并调整分片大小为 8MB
        mvc.perform(put("/api/v1/admin/settings/files").header("Authorization", "Bearer " + adminToken)
                        .contentType("application/json")
                        .content("{\"maxFileVersions\":15,\"uploadChunkSizeMb\":8,\"uploadMaxFileMb\":512,\"blockedExtensions\":\"exe,bat\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.uploadChunkSizeMb").value(8))
                .andExpect(jsonPath("$.data.blockedExtensions").value("exe,bat"));

        // 2. 上传 .exe 文件应立即被策略拦截（403）
        MockMultipartFile blocked = new MockMultipartFile("file", "malware.exe", "application/octet-stream",
                "MZ".getBytes(StandardCharsets.UTF_8));
        mvc.perform(multipart("/api/v1/spaces/" + spaceId + "/files").file(blocked)
                        .param("parentId", "0").header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden());

        // 3. 公开站点配置反映最新策略
        mvc.perform(get("/api/v1/public/site-config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.uploadChunkSizeMb").value(8));

        // 4. 后台关闭公开注册与密码登录 -> 对应匿名接口立即返回 403
        mvc.perform(put("/api/v1/admin/settings/auth").header("Authorization", "Bearer " + adminToken)
                        .contentType("application/json")
                        .content("{\"allowRegister\":false,\"requireEmailVerify\":true,\"allowPasswordLogin\":false,\"smtpHost\":\"\",\"smtpPort\":587,\"smtpUsername\":\"\",\"smtpFrom\":\"\"}"))
                .andExpect(status().isOk());

        mvc.perform(post("/api/admin/auth/register/send-email-code").param("email", "newuser@example.com"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/auth/login/email").contentType("application/json")
                        .content("{\"email\":\"someone@example.com\",\"password\":\"Password123!\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void s3SigV4RoundTripUploadReadAndScopedSearchRebuildAndAccountQuota() throws Exception {
        // 启动本机内存 Mock S3 服务验证真实 AWS SigV4 PUT/GET/HEAD/DELETE 全链路
        java.util.concurrent.ConcurrentHashMap<String, byte[]> store = new java.util.concurrent.ConcurrentHashMap<>();
        com.sun.net.httpserver.HttpServer mockS3 = com.sun.net.httpserver.HttpServer.create(
                new java.net.InetSocketAddress("127.0.0.1", 0), 0);
        mockS3.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            String method = exchange.getRequestMethod();
            String auth = exchange.getRequestHeaders().getFirst("Authorization");
            if (auth == null || !auth.startsWith("AWS4-HMAC-SHA256 ")) {
                exchange.sendResponseHeaders(403, -1);
                exchange.close();
                return;
            }
            if ("HEAD".equals(method)) {
                exchange.sendResponseHeaders(200, -1);
            } else if ("PUT".equals(method)) {
                byte[] body = exchange.getRequestBody().readAllBytes();
                store.put(path, body);
                exchange.sendResponseHeaders(200, -1);
            } else if ("GET".equals(method)) {
                byte[] body = store.get(path);
                if (body == null) {
                    exchange.sendResponseHeaders(404, -1);
                } else {
                    exchange.sendResponseHeaders(200, body.length);
                    exchange.getResponseBody().write(body);
                }
            } else if ("DELETE".equals(method)) {
                store.remove(path);
                exchange.sendResponseHeaders(204, -1);
            } else {
                exchange.sendResponseHeaders(405, -1);
            }
            exchange.close();
        });
        mockS3.start();
        try {
            String endpoint = "http://127.0.0.1:" + mockS3.getAddress().getPort();
            String s3Created = mvc.perform(post("/api/v1/admin/storage-profiles").header("Authorization", "Bearer " + adminToken)
                            .contentType("application/json")
                            .content("{\"name\":\"MockS3\",\"provider\":\"S3\",\"endpoint\":\"" + endpoint
                                    + "\",\"bucket\":\"demo\",\"region\":\"us-east-1\",\"accessKey\":\"ak\",\"secretKey\":\"sk\"}"))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            String s3Id = new tools.jackson.databind.json.JsonMapper().readTree(s3Created).get("data").asString();

            // 连接测试通过
            mvc.perform(post("/api/v1/admin/storage-profiles/" + s3Id + "/test").header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.ok").value(true));

            // 切换默认存储为 S3 并真实上传文件
            mvc.perform(put("/api/v1/admin/storage-profiles/" + s3Id + "/default").header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isOk());

            MockMultipartFile s3File = new MockMultipartFile("file", "s3-hello.txt", "text/plain",
                    "hello-from-sigv4-s3".getBytes(StandardCharsets.UTF_8));
            String uploadResp = mvc.perform(multipart("/api/v1/spaces/" + spaceId + "/files").file(s3File)
                            .param("parentId", "0").header("Authorization", "Bearer " + userToken))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            String nodeId = new tools.jackson.databind.json.JsonMapper().readTree(uploadResp).get("data").get("id").asString();
            assertFalse(store.isEmpty(), "S3 Mock 桶中应写入对象");

            // 通过 SigV4 GET 拉取内容并在线预览
            mvc.perform(get("/api/v1/spaces/" + spaceId + "/nodes/" + nodeId + "/text").header("Authorization", "Bearer " + userToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.content").value("hello-from-sigv4-s3"));

            // 单文件 & 单空间重建索引（需求 5.9）
            mvc.perform(post("/api/v1/admin/search/rebuild?nodeId=" + nodeId).header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.scope").value("NODE"));
            mvc.perform(post("/api/v1/admin/search/rebuild?spaceId=" + spaceId).header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.scope").value("SPACE"));

            // 个人中心动态配额查询与回收站清理接口
            mvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + userToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.usedBytes").isNumber())
                    .andExpect(jsonPath("$.data.quotaBytes").isNumber());
            mvc.perform(post("/api/v1/admin/storage/purge-trash").header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.retentionDays").isNumber());
        } finally {
            mockS3.stop(0);
        }
    }

    @Test
    void trashPermanentDeleteEmptyAndRecursiveDirectoryStats() throws Exception {
        // 创建子目录并上传 2 个文件，验证 includeSubdirs=true 递归统计
        String folderResp = mvc.perform(post("/api/v1/spaces/" + spaceId + "/folders").header("Authorization", "Bearer " + userToken)
                        .contentType("application/json").content("{\"parentId\":0,\"name\":\"sub\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String subId = new tools.jackson.databind.json.JsonMapper().readTree(folderResp).get("data").get("id").asString();

        MockMultipartFile f1 = new MockMultipartFile("file", "a.txt", "text/plain", "a".getBytes(StandardCharsets.UTF_8));
        MockMultipartFile f2 = new MockMultipartFile("file", "b.md", "text/plain", "b".getBytes(StandardCharsets.UTF_8));
        String n1Resp = mvc.perform(multipart("/api/v1/spaces/" + spaceId + "/files").file(f1)
                        .param("parentId", subId).header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String n1Id = new tools.jackson.databind.json.JsonMapper().readTree(n1Resp).get("data").get("id").asString();
        String n2Resp = mvc.perform(multipart("/api/v1/spaces/" + spaceId + "/files").file(f2)
                        .param("parentId", "0").header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String n2Id = new tools.jackson.databind.json.JsonMapper().readTree(n2Resp).get("data").get("id").asString();

        // 根目录直属仅 1 文件，包含子目录时为 2 文件
        mvc.perform(get("/api/v1/spaces/" + spaceId + "/stats?parentId=0&includeSubdirs=false").header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.files").value(1));
        mvc.perform(get("/api/v1/spaces/" + spaceId + "/stats?parentId=0&includeSubdirs=true").header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.files").value(2));

        // 移入回收站 -> 单条永久删除 n1 -> 一键清空剩余回收站
        mvc.perform(delete("/api/v1/spaces/" + spaceId + "/nodes/" + n1Id + "?revision=0").header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk());
        mvc.perform(delete("/api/v1/spaces/" + spaceId + "/nodes/" + n2Id + "?revision=0").header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk());

        mvc.perform(delete("/api/v1/spaces/" + spaceId + "/trash/" + n1Id).header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(1));
        mvc.perform(delete("/api/v1/spaces/" + spaceId + "/trash").header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(1));
    }

    @Test
    void thumbnailGenerationS3PresignAndOAuthAuthorizeFlow() throws Exception {
        // 1. 生成一张真实 8x8 PNG 图片上传并请求服务端缩略图
        java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(8, 8, java.awt.image.BufferedImage.TYPE_INT_RGB);
        java.io.ByteArrayOutputStream pngOut = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(img, "png", pngOut);
        MockMultipartFile png = new MockMultipartFile("file", "photo.png", "image/png", pngOut.toByteArray());
        String uploaded = mvc.perform(multipart("/api/v1/spaces/" + spaceId + "/files").file(png)
                        .param("parentId", "0").header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String photoId = new tools.jackson.databind.json.JsonMapper().readTree(uploaded).get("data").get("id").asString();

        mvc.perform(get("/api/v1/spaces/" + spaceId + "/nodes/" + photoId + "/preview/thumbnail?width=240")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/jpeg"));

        // 2. 创建 S3 存储并设为默认 -> 创建上传会话 -> 获取 AWS SigV4 预签名直传 URL
        String s3Created = mvc.perform(post("/api/v1/admin/storage-profiles").header("Authorization", "Bearer " + adminToken)
                        .contentType("application/json")
                        .content("{\"name\":\"PresignS3\",\"provider\":\"S3\",\"endpoint\":\"https://s3.example.com\",\"bucket\":\"cloud\",\"region\":\"us-east-1\",\"accessKey\":\"ak1\",\"secretKey\":\"sk1\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String s3Id = new tools.jackson.databind.json.JsonMapper().readTree(s3Created).get("data").asString();
        mvc.perform(put("/api/v1/admin/storage-profiles/" + s3Id + "/default").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        String sessionResp = mvc.perform(post("/api/v1/uploads").header("Authorization", "Bearer " + userToken)
                        .contentType("application/json")
                        .content("{\"spaceId\":" + spaceId + ",\"parentId\":0,\"name\":\"big.bin\",\"size\":1024}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String uploadId = new tools.jackson.databind.json.JsonMapper().readTree(sessionResp).get("data").get("id").asString();

        mvc.perform(get("/api/v1/uploads/" + uploadId + "/presign?expiresSeconds=600").header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mode").value("S3_PRESIGNED_PUT"))
                .andExpect(jsonPath("$.data.uploadUrl").value(org.hamcrest.Matchers.containsString("X-Amz-Signature=")));

        // 3. 启用 GitHub OAuth 提供商 -> 公开站点配置可见且 authorize 返回 302
        mvc.perform(put("/api/v1/admin/oauth-providers/github").header("Authorization", "Bearer " + adminToken)
                        .contentType("application/json")
                        .content("{\"enabled\":true,\"clientId\":\"gh-cid\",\"clientSecret\":\"gh-sec\",\"authorizeUrl\":\"https://github.com/login/oauth/authorize\",\"tokenUrl\":\"https://github.com/login/oauth/access_token\"}"))
                .andExpect(status().isOk());

        mvc.perform(get("/api/v1/public/site-config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.oauthProviders[0].providerCode").value("github"));

        mvc.perform(get("/api/v1/auth/oauth/github/authorize"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", org.hamcrest.Matchers.containsString("client_id=gh-cid")));
    }
}
