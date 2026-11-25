package com.wanyue.cloud.modules.auth.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.wanyue.cloud.common.result.PageResult;
import com.wanyue.cloud.modules.auth.dataobject.SessionDO;
import com.wanyue.cloud.modules.auth.vo.SessionCreateReqVO;
import com.wanyue.cloud.modules.auth.vo.SessionUpdateReqVO;
import com.wanyue.cloud.modules.auth.vo.SessionPageReqVO;
import com.wanyue.cloud.modules.auth.vo.SessionRespVO;

/** 用户登录会话与Refresh Token表基础业务接口。 */
public interface SessionService extends IService<SessionDO> {
    Long create(SessionCreateReqVO request);
    void update(Long id, SessionUpdateReqVO request);
    void delete(Long id);
    SessionRespVO get(Long id);
    PageResult<SessionRespVO> page(SessionPageReqVO request);
}

