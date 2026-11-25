package com.wanyue.cloud.modules.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wanyue.cloud.modules.auth.dataobject.PermissionDO;
import org.apache.ibatis.annotations.Mapper;

/** 系统级权限定义表；空间资源权限另行建模 Mapper。 */
@Mapper
public interface PermissionMapper extends BaseMapper<PermissionDO> {
}

