package com.wanyue.cloud.modules.auth.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.wanyue.cloud.common.result.PageResult;
import com.wanyue.cloud.modules.auth.dataobject.UserSecurityDO;
import com.wanyue.cloud.modules.auth.vo.UserSecurityCreateReqVO;
import com.wanyue.cloud.modules.auth.vo.UserSecurityUpdateReqVO;
import com.wanyue.cloud.modules.auth.vo.UserSecurityPageReqVO;
import com.wanyue.cloud.modules.auth.vo.UserSecurityRespVO;

/** 用户登录安全状态表基础业务接口。 */
public interface UserSecurityService extends IService<UserSecurityDO> {
    Long create(UserSecurityCreateReqVO request);
    void update(Long id, UserSecurityUpdateReqVO request);
    void delete(Long id);
    UserSecurityRespVO get(Long id);
    PageResult<UserSecurityRespVO> page(UserSecurityPageReqVO request);
}

