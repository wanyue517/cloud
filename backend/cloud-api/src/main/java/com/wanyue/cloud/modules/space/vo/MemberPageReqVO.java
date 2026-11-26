package com.wanyue.cloud.modules.space.vo;

import com.wanyue.cloud.common.page.PageParam;
import lombok.Getter;
import lombok.Setter;

/** 共享空间成员关系，独立于文件夹授权分页查询请求；筛选字段由业务自行增加。 */
@Getter
@Setter
public class MemberPageReqVO extends PageParam {
}

