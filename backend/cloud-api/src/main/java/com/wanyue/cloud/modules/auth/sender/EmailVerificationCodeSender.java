package com.wanyue.cloud.modules.auth.sender;

import com.wanyue.cloud.common.mail.MailService;
import com.wanyue.cloud.modules.auth.enums.VerificationChannelEnum;
import com.wanyue.cloud.modules.auth.enums.VerificationSceneEnum;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class EmailVerificationCodeSender implements VerificationCodeSender {
    private final MailService mailService;
    private final String from;

    public EmailVerificationCodeSender(MailService mailService,
            @Value("${cloud.verification.email.from}") String from) {
        this.mailService = mailService;
        this.from = from;
    }

    @Override
    public VerificationChannelEnum channel() {
        return VerificationChannelEnum.EMAIL;
    }

    @Override
    public void send(VerificationSceneEnum scene, String target, String code, int ttlSeconds) {
        String purpose = switch (scene) {
            case EMAIL_REGISTER -> "注册账号";
            case EMAIL_BIND -> "绑定邮箱";
            case EMAIL_CHANGE -> "更换邮箱";
            case EMAIL_RESET_PASSWORD -> "重置密码";
            default -> throw new IllegalArgumentException("不支持的邮件验证码场景");
        };
        mailService.sendText(from, target, "Cloudlane 邮箱验证码",
                "您正在" + purpose + "，验证码为：" + code + "。有效期 " + ttlSeconds
                        + " 秒。请勿向他人透露；如非本人操作，请忽略此邮件。");
    }
}
