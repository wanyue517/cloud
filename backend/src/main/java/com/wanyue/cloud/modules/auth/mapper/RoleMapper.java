package com.wanyue.cloud.modules.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wanyue.cloud.modules.auth.dataobject.RoleDO;
import org.apache.ibatis.annotations.Mapper;

/** 系统级角色定义表；不用于空间资源ACL Mapper。 */
@Mapper
public interface RoleMapper extends BaseMapper<RoleDO> {
}

