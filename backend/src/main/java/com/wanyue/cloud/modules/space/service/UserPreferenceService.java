package com.wanyue.cloud.modules.space.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.wanyue.cloud.common.result.PageResult;
import com.wanyue.cloud.modules.space.vo.UserPreferenceCreateReqVO;
import com.wanyue.cloud.modules.space.dataobject.UserPreferenceDO;
import com.wanyue.cloud.modules.space.vo.UserPreferenceUpdateReqVO;
import com.wanyue.cloud.modules.space.vo.UserPreferencePageReqVO;
import com.wanyue.cloud.modules.space.vo.UserPreferenceRespVO;

/** 用户空间导航偏好；默认入口仍存sys_user_setting基础业务接口。 */
public interface UserPreferenceService extends IService<UserPreferenceDO> {
    Long create(UserPreferenceCreateReqVO request);
    void update(Long id, UserPreferenceUpdateReqVO request);
    void delete(Long id);
    UserPreferenceRespVO get(Long id);
    PageResult<UserPreferenceRespVO> page(UserPreferencePageReqVO request);
}

