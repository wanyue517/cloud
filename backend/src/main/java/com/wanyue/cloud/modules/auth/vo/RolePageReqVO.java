package com.wanyue.cloud.modules.auth.vo;

import com.wanyue.cloud.common.page.PageParam;
import lombok.Getter;
import lombok.Setter;

/** 系统级角色定义表；不用于空间资源ACL分页查询请求；筛选字段由业务自行增加。 */
@Getter
@Setter
public class RolePageReqVO extends PageParam {
}

