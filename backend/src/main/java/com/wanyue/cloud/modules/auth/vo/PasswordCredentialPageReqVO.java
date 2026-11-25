package com.wanyue.cloud.modules.auth.vo;

import com.wanyue.cloud.common.page.PageParam;
import lombok.Getter;
import lombok.Setter;

/** 用户密码凭证表：每用户最多一份有效密码分页查询请求；筛选字段由业务自行增加。 */
@Getter
@Setter
public class PasswordCredentialPageReqVO extends PageParam {
}

