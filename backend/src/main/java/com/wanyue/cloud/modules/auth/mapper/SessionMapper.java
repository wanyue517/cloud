package com.wanyue.cloud.modules.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wanyue.cloud.modules.auth.dataobject.SessionDO;
import org.apache.ibatis.annotations.Mapper;

/** 用户登录会话与Refresh Token表 Mapper。 */
@Mapper
public interface SessionMapper extends BaseMapper<SessionDO> {
}

