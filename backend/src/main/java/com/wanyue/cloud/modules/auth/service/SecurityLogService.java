package com.wanyue.cloud.modules.auth.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.wanyue.cloud.common.result.PageResult;
import com.wanyue.cloud.modules.auth.dataobject.SecurityLogDO;
import com.wanyue.cloud.modules.auth.vo.SecurityLogCreateReqVO;
import com.wanyue.cloud.modules.auth.vo.SecurityLogUpdateReqVO;
import com.wanyue.cloud.modules.auth.vo.SecurityLogPageReqVO;
import com.wanyue.cloud.modules.auth.vo.SecurityLogRespVO;

/** 账号安全事件审计日志基础业务接口。 */
public interface SecurityLogService extends IService<SecurityLogDO> {
    Long create(SecurityLogCreateReqVO request);
    void update(Long id, SecurityLogUpdateReqVO request);
    void delete(Long id);
    SecurityLogRespVO get(Long id);
    PageResult<SecurityLogRespVO> page(SecurityLogPageReqVO request);

    /**
     * 记录注册日志
     * @param id
     * @param code
     */
    void recordRegisterSuccess(Long id, String code);
}

