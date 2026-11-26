package com.wanyue.cloud.modules.sys.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wanyue.cloud.modules.sys.dataobject.UserSettingDO;
import org.apache.ibatis.annotations.Mapper;

/** 用户个性化设置表 Mapper。 */
@Mapper
public interface UserSettingMapper extends BaseMapper<UserSettingDO> {
}

