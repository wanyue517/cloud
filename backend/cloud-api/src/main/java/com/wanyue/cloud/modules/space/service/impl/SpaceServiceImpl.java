package com.wanyue.cloud.modules.space.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.wanyue.cloud.common.exception.BizException;
import com.wanyue.cloud.common.result.PageResult;
import com.wanyue.cloud.modules.space.dataobject.SpaceDO;
import com.wanyue.cloud.modules.space.mapper.SpaceMapper;
import com.wanyue.cloud.modules.space.service.SpaceService;
import com.wanyue.cloud.modules.space.vo.SpaceCreateReqVO;
import com.wanyue.cloud.modules.space.vo.SpaceUpdateReqVO;
import com.wanyue.cloud.modules.space.vo.SpacePageReqVO;
import com.wanyue.cloud.modules.space.vo.SpaceRespVO;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 空间资源容器；不创建伪根目录节点单表 CRUD；生成后需补充业务规则及关联数据处理。 */
@Service
public class SpaceServiceImpl extends ServiceImpl<SpaceMapper, SpaceDO>
        implements SpaceService {

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(SpaceCreateReqVO request) {
        SpaceDO entity = new SpaceDO();
        BeanUtils.copyProperties(request, entity);
        if (!save(entity)) {
            throw BizException.conflict("新增失败");
        }
        return entity.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, SpaceUpdateReqVO request) {
        SpaceDO entity = getById(id);
        if (entity == null) {
            throw BizException.notFound("记录不存在");
        }
        // 请求 version 必须与客户端读取该记录时的版本一致。
        // BeanUtils 复制版本值后，MP 乐观锁将按该版本执行条件更新。
        BeanUtils.copyProperties(request, entity);
        if (!updateById(entity)) {
            throw BizException.conflict("记录已变更，请刷新后重试");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        if (!removeById(id)) {
            throw BizException.notFound("记录不存在");
        }
    }

    @Override
    public SpaceRespVO get(Long id) {
        SpaceDO entity = getById(id);
        if (entity == null) {
            throw BizException.notFound("记录不存在");
        }
        return toResp(entity);
    }

    @Override
    public PageResult<SpaceRespVO> page(SpacePageReqVO request) {
        IPage<SpaceDO> result = super.page(
                new Page<>(request.getPageNo(), request.getPageSize()),
                Wrappers.<SpaceDO>lambdaQuery()
                        .orderByDesc(SpaceDO::getCreatedAt)
        );
        return PageResult.of(
                result.getRecords().stream().map(this::toResp).toList(),
                result.getTotal(), result.getCurrent(), result.getSize()
        );
    }

    private SpaceRespVO toResp(SpaceDO entity) {
        SpaceRespVO vo = new SpaceRespVO();
        BeanUtils.copyProperties(entity, vo);
        return vo;
    }
}

