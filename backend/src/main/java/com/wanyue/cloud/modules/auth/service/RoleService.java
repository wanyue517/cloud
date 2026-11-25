package com.wanyue.cloud.modules.auth.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.wanyue.cloud.common.result.PageResult;
import com.wanyue.cloud.modules.auth.dataobject.RoleDO;
import com.wanyue.cloud.modules.auth.vo.RoleCreateReqVO;
import com.wanyue.cloud.modules.auth.vo.RoleUpdateReqVO;
import com.wanyue.cloud.modules.auth.vo.RolePageReqVO;
import com.wanyue.cloud.modules.auth.vo.RoleRespVO;

/** 系统级角色定义表；不用于空间资源ACL基础业务接口。 */
public interface RoleService extends IService<RoleDO> {
    Long create(RoleCreateReqVO request);
    void update(Long id, RoleUpdateReqVO request);
    void delete(Long id);
    RoleRespVO get(Long id);
    PageResult<RoleRespVO> page(RolePageReqVO request);
}

