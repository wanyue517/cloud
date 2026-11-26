package com.wanyue.cloud.common.result;

import lombok.Getter;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 统一分页响应。列表字段使用 list；pageNo 从 1 开始。
 *
 * @param <T> 列表元素类型
 */
@Getter
public final class PageResult<T> implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private final List<T> list;
    private final long total;
    private final long pageNo;
    private final long pageSize;
    private final long pages;
    private final boolean hasNext;
    private final boolean hasPrevious;

    private PageResult(List<T> list, long total, long pageNo, long pageSize) {
        if (pageNo < 1 || pageSize < 1 || total < 0) {
            throw new IllegalArgumentException("分页参数不合法");
        }
        this.list = list == null
                ? List.of()
                : Collections.unmodifiableList(new ArrayList<>(list));
        this.total = total;
        this.pageNo = pageNo;
        this.pageSize = pageSize;
        this.pages = total / pageSize + (total % pageSize == 0 ? 0 : 1);
        this.hasPrevious = pageNo > 1 && pages > 0;
        this.hasNext = pageNo < pages;
    }

    /** 从查询结果构建分页数据。 */
    public static <T> PageResult<T> of(List<T> list, long total, long pageNo, long pageSize) {
        return new PageResult<>(list, total, pageNo, pageSize);
    }

    /** 构建空分页结果，保留请求页码。 */
    public static <T> PageResult<T> empty(long pageNo, long pageSize) {
        return new PageResult<>(List.of(), 0L, pageNo, pageSize);
    }
}
