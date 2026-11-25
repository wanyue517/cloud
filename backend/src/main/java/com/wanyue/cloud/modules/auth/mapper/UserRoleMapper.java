package com.wanyue.cloud.modules.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wanyue.cloud.modules.auth.dataobject.UserRoleDO;
import org.apache.ibatis.annotations.Mapper;

/** 用户与系统角色关联表 Mapper。 */
@Mapper
public interface UserRoleMapper extends BaseMapper<UserRoleDO> {
}

