package com.wanyue.cloud.modules.sys.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wanyue.cloud.modules.sys.dataobject.UserProfileDO;
import org.apache.ibatis.annotations.Mapper;

/** 用户扩展资料表 Mapper。 */
@Mapper
public interface UserProfileMapper extends BaseMapper<UserProfileDO> {
}

