package com.wanyue.cloud.modules.auth.vo;

import com.wanyue.cloud.common.page.PageParam;
import lombok.Getter;
import lombok.Setter;

/** 用户登录标识表：用户名、邮箱、手机号分页查询请求；筛选字段由业务自行增加。 */
@Getter
@Setter
public class IdentityPageReqVO extends PageParam {
}

