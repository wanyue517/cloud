package com.wanyue.cloud.modules.space.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.wanyue.cloud.common.result.PageResult;
import com.wanyue.cloud.modules.space.dataobject.SpaceDO;
import com.wanyue.cloud.modules.space.vo.SpaceCreateReqVO;
import com.wanyue.cloud.modules.space.vo.SpaceUpdateReqVO;
import com.wanyue.cloud.modules.space.vo.SpacePageReqVO;
import com.wanyue.cloud.modules.space.vo.SpaceRespVO;

/** 空间资源容器；不创建伪根目录节点基础业务接口。 */
public interface SpaceService extends IService<SpaceDO> {
    Long create(SpaceCreateReqVO request);
    void update(Long id, SpaceUpdateReqVO request);
    void delete(Long id);
    SpaceRespVO get(Long id);
    PageResult<SpaceRespVO> page(SpacePageReqVO request);
}

