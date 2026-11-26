package com.wanyue.cloud.modules.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wanyue.cloud.modules.auth.dataobject.UserSecurityDO;
import org.apache.ibatis.annotations.Mapper;

/** 用户登录安全状态表 Mapper。 */
@Mapper
public interface UserSecurityMapper extends BaseMapper<UserSecurityDO> {
}

