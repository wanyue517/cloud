package com.wanyue.cloud.modules.auth.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.wanyue.cloud.common.result.PageResult;
import com.wanyue.cloud.modules.auth.dataobject.IdentityDO;
import com.wanyue.cloud.modules.auth.vo.*;


public interface AuthService extends IService<IdentityDO> {

    /**
     * 邮箱登录
     * @param request
     * @return
     */
    UserLoginRespVO loginWithEmail(EmailLoginReqVO request);

    /**
     * 邮箱注册
     * @param request
     * @return
     */
    Boolean registerWithEmail(EmailRegisterReqVO request);

    /**
     * 发送邮箱验证码
     * @param email
     */
    void sendEmailCode(String email);


}

