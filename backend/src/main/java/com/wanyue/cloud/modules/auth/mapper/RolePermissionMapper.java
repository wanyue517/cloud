package com.wanyue.cloud.modules.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wanyue.cloud.modules.auth.dataobject.RolePermissionDO;
import org.apache.ibatis.annotations.Mapper;

/** 系统角色与系统权限关联表 Mapper。 */
@Mapper
public interface RolePermissionMapper extends BaseMapper<RolePermissionDO> {
}

