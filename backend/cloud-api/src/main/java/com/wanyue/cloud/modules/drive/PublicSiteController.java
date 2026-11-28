package com.wanyue.cloud.modules.drive;

import com.wanyue.cloud.common.result.R;
import com.wanyue.cloud.modules.ops.SystemPolicyService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/public")
public class PublicSiteController {
    private final SystemPolicyService policy;
    private final JdbcTemplate db;

    public PublicSiteController(SystemPolicyService policy, JdbcTemplate db) {
        this.policy = policy;
        this.db = db;
    }

    @GetMapping("/site-config")
    public R<Map<String, Object>> siteConfig() {
        var oauthProviders = db.queryForList(
                "SELECT provider_code providerCode, display_name displayName "
                        + "FROM sys_oauth_provider WHERE enabled=1 ORDER BY provider_code");
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("siteName", policy.getString("site_name", "Cloudlane"));
        data.put("siteDescription", policy.getString("site_description", "个人云端工作台"));
        data.put("siteLogoUrl", policy.getString("site_logo_url", ""));
        data.put("siteAnnouncement", policy.getString("site_announcement", ""));
        data.put("allowRegister", policy.allowRegister());
        data.put("requireEmailVerify", policy.requireEmailVerify());
        data.put("allowPasswordLogin", policy.allowPasswordLogin());
        data.put("uploadChunkSizeMb", policy.uploadChunkSizeMb());
        data.put("uploadMaxFileMb", policy.uploadMaxFileMb());
        data.put("oauthProviders", oauthProviders);
        return R.ok(data);
    }
}
