package com.wanyue.cloud.modules.auth.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.wanyue.cloud.common.result.PageResult;
import com.wanyue.cloud.modules.auth.dataobject.IdentityDO;
import com.wanyue.cloud.modules.auth.vo.IdentityCreateReqVO;
import com.wanyue.cloud.modules.auth.vo.IdentityUpdateReqVO;
import com.wanyue.cloud.modules.auth.vo.IdentityPageReqVO;
import com.wanyue.cloud.modules.auth.vo.IdentityRespVO;

/** 用户登录标识表：用户名、邮箱、手机号基础业务接口。 */
public interface IdentityService extends IService<IdentityDO> {
    Long create(IdentityCreateReqVO request);
    void update(Long id, IdentityUpdateReqVO request);
    void delete(Long id);
    IdentityRespVO get(Long id);
    PageResult<IdentityRespVO> page(IdentityPageReqVO request);

    /**
     * 校验邮箱是否已存在
     * @param email
     */
    Boolean checkEmail(String email);

    /**
     * 绑定邮箱
     * @param id
     * @param email
     */
    void bindEmail(Long id, String email);

    /** 在事务内将该用户的指定身份设为该类型的首选身份。 */
    void setPrimary(Long userId, Long identityId);

    /**
     * 根据邮箱查询账号
     * @param email
     * @return
     */
    IdentityDO getByEmail(String email);
}

