package com.wanyue.cloud.modules.space.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wanyue.cloud.modules.space.dataobject.SpaceDO;
import org.apache.ibatis.annotations.Mapper;

/** 空间资源容器；不创建伪根目录节点 Mapper。 */
@Mapper
public interface SpaceMapper extends BaseMapper<SpaceDO> {
}

