package com.wanyue.cloud.modules.auth.service;

import com.wanyue.cloud.modules.auth.enums.VerificationSceneEnum;


public interface VerificationCodeService {

    /**
     * 发送验证码
     */
    void send(VerificationSceneEnum scene, String target);

    /**
     * 原子校验并占用验证码
     * 事务提交消费，回滚释放
     */
    void reserveInTransaction(
            VerificationSceneEnum scene,
            String target,
            String code
    );

    /**
     * 取消当前验证码，可用于发送失败等场景
     * 内部需匹配验证码的签发标识
     */
    void invalidate(
            VerificationSceneEnum scene,
            String target,
            String issueId
    );
}
