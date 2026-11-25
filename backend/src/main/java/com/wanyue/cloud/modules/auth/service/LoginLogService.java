package com.wanyue.cloud.modules.auth.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.wanyue.cloud.common.result.PageResult;
import com.wanyue.cloud.modules.auth.dataobject.LoginLogDO;
import com.wanyue.cloud.modules.auth.vo.LoginLogCreateReqVO;
import com.wanyue.cloud.modules.auth.vo.LoginLogUpdateReqVO;
import com.wanyue.cloud.modules.auth.vo.LoginLogPageReqVO;
import com.wanyue.cloud.modules.auth.vo.LoginLogRespVO;

/** 登录审计日志：仅记录脱敏标识，不存凭据基础业务接口。 */
public interface LoginLogService extends IService<LoginLogDO> {
    Long create(LoginLogCreateReqVO request);
    void update(Long id, LoginLogUpdateReqVO request);
    void delete(Long id);
    LoginLogRespVO get(Long id);
    PageResult<LoginLogRespVO> page(LoginLogPageReqVO request);
}

