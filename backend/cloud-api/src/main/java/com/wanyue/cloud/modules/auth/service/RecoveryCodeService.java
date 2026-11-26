package com.wanyue.cloud.modules.auth.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.wanyue.cloud.common.result.PageResult;
import com.wanyue.cloud.modules.auth.dataobject.RecoveryCodeDO;
import com.wanyue.cloud.modules.auth.vo.RecoveryCodeCreateReqVO;
import com.wanyue.cloud.modules.auth.vo.RecoveryCodeUpdateReqVO;
import com.wanyue.cloud.modules.auth.vo.RecoveryCodePageReqVO;
import com.wanyue.cloud.modules.auth.vo.RecoveryCodeRespVO;

/** 多因素认证一次性恢复码表基础业务接口。 */
public interface RecoveryCodeService extends IService<RecoveryCodeDO> {
    Long create(RecoveryCodeCreateReqVO request);
    void update(Long id, RecoveryCodeUpdateReqVO request);
    void delete(Long id);
    RecoveryCodeRespVO get(Long id);
    PageResult<RecoveryCodeRespVO> page(RecoveryCodePageReqVO request);
}

