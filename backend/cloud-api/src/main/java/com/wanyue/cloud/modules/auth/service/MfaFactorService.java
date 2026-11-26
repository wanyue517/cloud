package com.wanyue.cloud.modules.auth.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.wanyue.cloud.common.result.PageResult;
import com.wanyue.cloud.modules.auth.dataobject.MfaFactorDO;
import com.wanyue.cloud.modules.auth.vo.MfaFactorCreateReqVO;
import com.wanyue.cloud.modules.auth.vo.MfaFactorUpdateReqVO;
import com.wanyue.cloud.modules.auth.vo.MfaFactorPageReqVO;
import com.wanyue.cloud.modules.auth.vo.MfaFactorRespVO;

/** 用户多因素认证器表基础业务接口。 */
public interface MfaFactorService extends IService<MfaFactorDO> {
    Long create(MfaFactorCreateReqVO request);
    void update(Long id, MfaFactorUpdateReqVO request);
    void delete(Long id);
    MfaFactorRespVO get(Long id);
    PageResult<MfaFactorRespVO> page(MfaFactorPageReqVO request);
}

