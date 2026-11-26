package com.wanyue.cloud.modules.auth.enums;

public enum VerificationSceneEnum {
    EMAIL_REGISTER(VerificationChannelEnum.EMAIL),
    EMAIL_BIND(VerificationChannelEnum.EMAIL),
    EMAIL_CHANGE(VerificationChannelEnum.EMAIL),
    EMAIL_RESET_PASSWORD(VerificationChannelEnum.EMAIL),

    SMS_REGISTER(VerificationChannelEnum.SMS),
    SMS_BIND(VerificationChannelEnum.SMS),
    SMS_LOGIN(VerificationChannelEnum.SMS),
    SMS_RESET_PASSWORD(VerificationChannelEnum.SMS);

    private final VerificationChannelEnum channel;

    VerificationSceneEnum(VerificationChannelEnum channel) {
        this.channel = channel;
    }

    public VerificationChannelEnum getChannel() {
        return channel;
    }
}
