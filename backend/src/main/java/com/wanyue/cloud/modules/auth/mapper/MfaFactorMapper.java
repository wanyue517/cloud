package com.wanyue.cloud.modules.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wanyue.cloud.modules.auth.dataobject.MfaFactorDO;
import org.apache.ibatis.annotations.Mapper;

/** 用户多因素认证器表 Mapper。 */
@Mapper
public interface MfaFactorMapper extends BaseMapper<MfaFactorDO> {
}

