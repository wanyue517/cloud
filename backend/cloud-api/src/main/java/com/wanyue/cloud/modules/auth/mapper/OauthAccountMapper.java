package com.wanyue.cloud.modules.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wanyue.cloud.modules.auth.dataobject.OauthAccountDO;
import org.apache.ibatis.annotations.Mapper;

/** 第三方OAuth账号绑定表 Mapper。 */
@Mapper
public interface OauthAccountMapper extends BaseMapper<OauthAccountDO> {
}

