package com.wanyue.cloud.common.page;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * 所有分页请求 VO 的公共父类。页码从 1 开始，单页最多 100 条。
 */
@Getter
@Setter
public class PageParam {

    @NotNull(message = "页码不能为空")
    @Min(value = 1, message = "页码最小为 1")
    private Long pageNo = 1L;

    @NotNull(message = "每页条数不能为空")
    @Min(value = 1, message = "每页至少 1 条")
    @Max(value = 100, message = "每页最多 100 条")
    private Long pageSize = 20L;
}
