package com.wanyue.cloud.modules.auth.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.wanyue.cloud.common.result.PageResult;
import com.wanyue.cloud.modules.auth.dataobject.PermissionDO;
import com.wanyue.cloud.modules.auth.vo.PermissionCreateReqVO;
import com.wanyue.cloud.modules.auth.vo.PermissionUpdateReqVO;
import com.wanyue.cloud.modules.auth.vo.PermissionPageReqVO;
import com.wanyue.cloud.modules.auth.vo.PermissionRespVO;

/** 系统级权限定义表；空间资源权限另行建模基础业务接口。 */
public interface PermissionService extends IService<PermissionDO> {
    Long create(PermissionCreateReqVO request);
    void update(Long id, PermissionUpdateReqVO request);
    void delete(Long id);
    PermissionRespVO get(Long id);
    PageResult<PermissionRespVO> page(PermissionPageReqVO request);
}

