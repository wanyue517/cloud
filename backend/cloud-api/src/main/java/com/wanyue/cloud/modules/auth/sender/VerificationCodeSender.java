package com.wanyue.cloud.modules.auth.sender;

import com.wanyue.cloud.modules.auth.enums.VerificationChannelEnum;
import com.wanyue.cloud.modules.auth.enums.VerificationSceneEnum;

public interface VerificationCodeSender {
    VerificationChannelEnum channel();

    void send(VerificationSceneEnum scene, String target, String code, int ttlSeconds);
}
