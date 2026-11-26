package com.wanyue.cloud.modules.auth.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.wanyue.cloud.common.exception.BizException;
import com.wanyue.cloud.common.result.PageResult;
import com.wanyue.cloud.modules.auth.dataobject.PermissionDO;
import com.wanyue.cloud.modules.auth.mapper.PermissionMapper;
import com.wanyue.cloud.modules.auth.service.PermissionService;
import com.wanyue.cloud.modules.auth.vo.PermissionCreateReqVO;
import com.wanyue.cloud.modules.auth.vo.PermissionUpdateReqVO;
import com.wanyue.cloud.modules.auth.vo.PermissionPageReqVO;
import com.wanyue.cloud.modules.auth.vo.PermissionRespVO;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 系统级权限定义表；空间资源权限另行建模单表 CRUD；生成后需补充业务规则及关联数据处理。 */
@Service
public class PermissionServiceImpl extends ServiceImpl<PermissionMapper, PermissionDO>
        implements PermissionService {

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(PermissionCreateReqVO request) {
        PermissionDO entity = new PermissionDO();
        BeanUtils.copyProperties(request, entity);
        if (!save(entity)) {
            throw BizException.conflict("新增失败");
        }
        return entity.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, PermissionUpdateReqVO request) {
        PermissionDO entity = getById(id);
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
    public PermissionRespVO get(Long id) {
        PermissionDO entity = getById(id);
        if (entity == null) {
            throw BizException.notFound("记录不存在");
        }
        return toResp(entity);
    }

    @Override
    public PageResult<PermissionRespVO> page(PermissionPageReqVO request) {
        IPage<PermissionDO> result = super.page(
                new Page<>(request.getPageNo(), request.getPageSize()),
                Wrappers.<PermissionDO>lambdaQuery()
                        .orderByDesc(PermissionDO::getCreatedAt)
        );
        return PageResult.of(
                result.getRecords().stream().map(this::toResp).toList(),
                result.getTotal(), result.getCurrent(), result.getSize()
        );
    }

    private PermissionRespVO toResp(PermissionDO entity) {
        PermissionRespVO vo = new PermissionRespVO();
        BeanUtils.copyProperties(entity, vo);
        return vo;
    }
}

