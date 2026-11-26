package com.wanyue.cloud.modules.space.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wanyue.cloud.modules.space.dataobject.UserPreferenceDO;
import org.apache.ibatis.annotations.Mapper;

/** 用户空间导航偏好；默认入口仍存sys_user_setting Mapper。 */
@Mapper
public interface UserPreferenceMapper extends BaseMapper<UserPreferenceDO> {
}

