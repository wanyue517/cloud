package com.wanyue.cloud.modules.sys.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.wanyue.cloud.common.result.PageResult;
import com.wanyue.cloud.modules.sys.vo.UserProfileUpdateReqVO;
import com.wanyue.cloud.modules.sys.dataobject.UserProfileDO;
import com.wanyue.cloud.modules.sys.vo.UserProfileCreateReqVO;
import com.wanyue.cloud.modules.sys.vo.UserProfilePageReqVO;
import com.wanyue.cloud.modules.sys.vo.UserProfileRespVO;

/** 用户扩展资料表基础业务接口。 */
public interface UserProfileService extends IService<UserProfileDO> {
    Long create(UserProfileCreateReqVO request);
    void update(Long id, UserProfileUpdateReqVO request);
    void delete(Long id);
    UserProfileRespVO get(Long id);
    PageResult<UserProfileRespVO> page(UserProfilePageReqVO request);
}

