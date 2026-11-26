package com.wanyue.cloud.modules.space.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wanyue.cloud.modules.space.dataobject.MemberDO;
import org.apache.ibatis.annotations.Mapper;

/** 共享空间成员关系，独立于文件夹授权 Mapper。 */
@Mapper
public interface MemberMapper extends BaseMapper<MemberDO> {
}

