package com.wanyue.cloud.modules.auth.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.wanyue.cloud.common.result.PageResult;
import com.wanyue.cloud.modules.auth.dataobject.PasswordCredentialDO;
import com.wanyue.cloud.modules.auth.vo.PasswordCredentialCreateReqVO;
import com.wanyue.cloud.modules.auth.vo.PasswordCredentialUpdateReqVO;
import com.wanyue.cloud.modules.auth.vo.PasswordCredentialPageReqVO;
import com.wanyue.cloud.modules.auth.vo.PasswordCredentialRespVO;

/** 用户密码凭证表：每用户最多一份有效密码基础业务接口。 */
public interface PasswordCredentialService extends IService<PasswordCredentialDO> {
    /** 根据用户ID查询有效凭证，凭证主键与用户ID相互独立。 */
    PasswordCredentialDO getByUserId(Long userId);
    Long create(PasswordCredentialCreateReqVO request);
    void update(Long id, PasswordCredentialUpdateReqVO request);
    void delete(Long id);
    PasswordCredentialRespVO get(Long id);
    PageResult<PasswordCredentialRespVO> page(PasswordCredentialPageReqVO request);
}

