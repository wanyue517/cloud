package com.wanyue.cloud.modules.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wanyue.cloud.modules.auth.dataobject.PasswordCredentialDO;
import org.apache.ibatis.annotations.Mapper;

/** 用户密码凭证表：每用户最多一份有效密码 Mapper。 */
@Mapper
public interface PasswordCredentialMapper extends BaseMapper<PasswordCredentialDO> {
}

