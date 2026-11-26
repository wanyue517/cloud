package com.wanyue.cloud.modules.sys.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.wanyue.cloud.common.result.PageResult;
import com.wanyue.cloud.modules.sys.vo.UserSettingUpdateReqVO;
import com.wanyue.cloud.modules.sys.dataobject.UserSettingDO;
import com.wanyue.cloud.modules.sys.vo.UserSettingCreateReqVO;
import com.wanyue.cloud.modules.sys.vo.UserSettingPageReqVO;
import com.wanyue.cloud.modules.sys.vo.UserSettingRespVO;

/** 用户个性化设置表基础业务接口。 */
public interface UserSettingService extends IService<UserSettingDO> {
    Long create(UserSettingCreateReqVO request);
    void update(Long id, UserSettingUpdateReqVO request);
    void delete(Long id);
    UserSettingRespVO get(Long id);
    PageResult<UserSettingRespVO> page(UserSettingPageReqVO request);
}

