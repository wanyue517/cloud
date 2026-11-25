package com.wanyue.cloud.modules.auth.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.wanyue.cloud.common.result.PageResult;
import com.wanyue.cloud.modules.auth.dataobject.UserRoleDO;
import com.wanyue.cloud.modules.auth.vo.UserRoleCreateReqVO;
import com.wanyue.cloud.modules.auth.vo.UserRoleUpdateReqVO;
import com.wanyue.cloud.modules.auth.vo.UserRolePageReqVO;
import com.wanyue.cloud.modules.auth.vo.UserRoleRespVO;

/** 用户与系统角色关联表基础业务接口。 */
public interface UserRoleService extends IService<UserRoleDO> {
    Long create(UserRoleCreateReqVO request);
    void update(Long id, UserRoleUpdateReqVO request);
    void delete(Long id);
    UserRoleRespVO get(Long id);
    PageResult<UserRoleRespVO> page(UserRolePageReqVO request);

    /**
     * 绑定角色
     * @param id
     * @param code
     */
    void bindRole(Long id, String code);
}

