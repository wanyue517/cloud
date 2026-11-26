package com.wanyue.cloud.modules.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wanyue.cloud.modules.auth.dataobject.IdentityDO;
import org.apache.ibatis.annotations.Mapper;

/** 用户登录标识表：用户名、邮箱、手机号 Mapper。 */
@Mapper
public interface IdentityMapper extends BaseMapper<IdentityDO> {
}

