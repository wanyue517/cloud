package com.wanyue.cloud.modules.auth.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.wanyue.cloud.common.result.PageResult;
import com.wanyue.cloud.modules.auth.dataobject.OauthAccountDO;
import com.wanyue.cloud.modules.auth.vo.OauthAccountCreateReqVO;
import com.wanyue.cloud.modules.auth.vo.OauthAccountUpdateReqVO;
import com.wanyue.cloud.modules.auth.vo.OauthAccountPageReqVO;
import com.wanyue.cloud.modules.auth.vo.OauthAccountRespVO;

/** 第三方OAuth账号绑定表基础业务接口。 */
public interface OauthAccountService extends IService<OauthAccountDO> {
    Long create(OauthAccountCreateReqVO request);
    void update(Long id, OauthAccountUpdateReqVO request);
    void delete(Long id);
    OauthAccountRespVO get(Long id);
    PageResult<OauthAccountRespVO> page(OauthAccountPageReqVO request);
}

