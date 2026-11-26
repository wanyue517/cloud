package com.wanyue.cloud.modules.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wanyue.cloud.modules.auth.dataobject.LoginLogDO;
import org.apache.ibatis.annotations.Mapper;

/** 登录审计日志：仅记录脱敏标识，不存凭据 Mapper。 */
@Mapper
public interface LoginLogMapper extends BaseMapper<LoginLogDO> {
}

