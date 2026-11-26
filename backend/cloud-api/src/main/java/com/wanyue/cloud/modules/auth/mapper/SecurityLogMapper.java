package com.wanyue.cloud.modules.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wanyue.cloud.modules.auth.dataobject.SecurityLogDO;
import org.apache.ibatis.annotations.Mapper;

/** 账号安全事件审计日志 Mapper。 */
@Mapper
public interface SecurityLogMapper extends BaseMapper<SecurityLogDO> {
}

