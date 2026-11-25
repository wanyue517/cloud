package com.wanyue.cloud.modules.auth.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.wanyue.cloud.common.result.PageResult;
import com.wanyue.cloud.modules.auth.dataobject.RolePermissionDO;
import com.wanyue.cloud.modules.auth.vo.RolePermissionCreateReqVO;
import com.wanyue.cloud.modules.auth.vo.RolePermissionUpdateReqVO;
import com.wanyue.cloud.modules.auth.vo.RolePermissionPageReqVO;
import com.wanyue.cloud.modules.auth.vo.RolePermissionRespVO;

/** 系统角色与系统权限关联表基础业务接口。 */
public interface RolePermissionService extends IService<RolePermissionDO> {
    Long create(RolePermissionCreateReqVO request);
    void update(Long id, RolePermissionUpdateReqVO request);
    void delete(Long id);
    RolePermissionRespVO get(Long id);
    PageResult<RolePermissionRespVO> page(RolePermissionPageReqVO request);
}

