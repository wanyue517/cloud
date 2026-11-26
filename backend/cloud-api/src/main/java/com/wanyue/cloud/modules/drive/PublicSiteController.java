package com.wanyue.cloud.modules.drive;

import com.wanyue.cloud.common.result.R;
import com.wanyue.cloud.modules.ops.SystemPolicyService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/public")
public class PublicSiteController {
    private final SystemPolicyService policy;

    public PublicSiteController(SystemPolicyService policy) {
        this.policy = policy;
    }

    @GetMapping("/site-config")
    public R<Map<String, Object>> siteConfig() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("siteName", policy.getString("site_name", "Cloudlane"));
        data.put("siteLogoUrl", policy.getString("site_logo_url", ""));
        data.put("siteAnnouncement", policy.getString("site_announcement", ""));
        data.put("allowRegister", policy.allowRegister());
        data.put("requireEmailVerify", policy.requireEmailVerify());
        data.put("allowPasswordLogin", policy.allowPasswordLogin());
        data.put("uploadChunkSizeMb", policy.uploadChunkSizeMb());
        data.put("uploadMaxFileMb", policy.uploadMaxFileMb());
        return R.ok(data);
    }
}
