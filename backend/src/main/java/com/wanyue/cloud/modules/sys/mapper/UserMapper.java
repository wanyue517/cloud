package com.wanyue.cloud.modules.sys.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wanyue.cloud.modules.sys.dataobject.UserDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** 系统用户主表 Mapper。 */
@Mapper
public interface UserMapper extends BaseMapper<UserDO> {
    /** 串行化同一用户的身份绑定及首选身份变更，必须在事务内调用。 */
    @Select("SELECT * FROM sys_user WHERE id = #{userId} AND deleted = 0 FOR UPDATE")
    UserDO lockActiveById(@Param("userId") Long userId);
}

