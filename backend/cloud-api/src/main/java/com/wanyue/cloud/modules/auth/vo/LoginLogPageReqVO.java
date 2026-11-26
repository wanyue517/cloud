package com.wanyue.cloud.modules.auth.vo;

import com.wanyue.cloud.common.page.PageParam;
import lombok.Getter;
import lombok.Setter;

/** 登录审计日志：仅记录脱敏标识，不存凭据分页查询请求；筛选字段由业务自行增加。 */
@Getter
@Setter
public class LoginLogPageReqVO extends PageParam {
}

