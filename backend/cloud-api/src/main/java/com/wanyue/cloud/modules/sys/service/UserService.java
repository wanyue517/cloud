package com.wanyue.cloud.modules.sys.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.wanyue.cloud.common.result.PageResult;
import com.wanyue.cloud.modules.sys.dataobject.UserDO;
import com.wanyue.cloud.modules.sys.vo.UserCreateReqVO;
import com.wanyue.cloud.modules.sys.vo.UserUpdateReqVO;
import com.wanyue.cloud.modules.sys.vo.UserPageReqVO;
import com.wanyue.cloud.modules.sys.vo.UserRespVO;

/** 系统用户主表基础业务接口。 */
public interface UserService extends IService<UserDO> {
    Long create(UserCreateReqVO request);
    void update(Long id, UserUpdateReqVO request);
    void delete(Long id);
    UserRespVO get(Long id);
    PageResult<UserRespVO> page(UserPageReqVO request);
}

