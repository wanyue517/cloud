package com.wanyue.cloud.modules.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wanyue.cloud.modules.auth.dataobject.RecoveryCodeDO;
import org.apache.ibatis.annotations.Mapper;

/** 多因素认证一次性恢复码表 Mapper。 */
@Mapper
public interface RecoveryCodeMapper extends BaseMapper<RecoveryCodeDO> {
}

