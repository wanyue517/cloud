package com.wanyue.cloud.modules.auth.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.wanyue.cloud.common.exception.BizException;
import com.wanyue.cloud.common.result.PageResult;
import com.wanyue.cloud.modules.auth.dataobject.RolePermissionDO;
import com.wanyue.cloud.modules.auth.mapper.RolePermissionMapper;
import com.wanyue.cloud.modules.auth.service.RolePermissionService;
import com.wanyue.cloud.modules.auth.vo.RolePermissionCreateReqVO;
import com.wanyue.cloud.modules.auth.vo.RolePermissionUpdateReqVO;
import com.wanyue.cloud.modules.auth.vo.RolePermissionPageReqVO;
import com.wanyue.cloud.modules.auth.vo.RolePermissionRespVO;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 系统角色与系统权限关联表单表 CRUD；生成后需补充业务规则及关联数据处理。 */
@Service
public class RolePermissionServiceImpl extends ServiceImpl<RolePermissionMapper, RolePermissionDO>
        implements RolePermissionService {

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(RolePermissionCreateReqVO request) {
        RolePermissionDO entity = new RolePermissionDO();
        BeanUtils.copyProperties(request, entity);
        if (!save(entity)) {
            throw BizException.conflict("新增失败");
        }
        return entity.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, RolePermissionUpdateReqVO request) {
        RolePermissionDO entity = getById(id);
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
    public RolePermissionRespVO get(Long id) {
        RolePermissionDO entity = getById(id);
        if (entity == null) {
            throw BizException.notFound("记录不存在");
        }
        return toResp(entity);
    }

    @Override
    public PageResult<RolePermissionRespVO> page(RolePermissionPageReqVO request) {
        IPage<RolePermissionDO> result = super.page(
                new Page<>(request.getPageNo(), request.getPageSize()),
                Wrappers.<RolePermissionDO>lambdaQuery()
                        .orderByDesc(RolePermissionDO::getCreatedAt)
        );
        return PageResult.of(
                result.getRecords().stream().map(this::toResp).toList(),
                result.getTotal(), result.getCurrent(), result.getSize()
        );
    }

    private RolePermissionRespVO toResp(RolePermissionDO entity) {
        RolePermissionRespVO vo = new RolePermissionRespVO();
        BeanUtils.copyProperties(entity, vo);
        return vo;
    }
}

