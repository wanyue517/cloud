package com.wanyue.cloud.modules.auth.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.wanyue.cloud.common.exception.BizException;
import com.wanyue.cloud.common.result.PageResult;
import com.wanyue.cloud.modules.auth.dataobject.RoleDO;
import com.wanyue.cloud.modules.auth.mapper.RoleMapper;
import com.wanyue.cloud.modules.auth.service.RoleService;
import com.wanyue.cloud.modules.auth.vo.RoleCreateReqVO;
import com.wanyue.cloud.modules.auth.vo.RoleUpdateReqVO;
import com.wanyue.cloud.modules.auth.vo.RolePageReqVO;
import com.wanyue.cloud.modules.auth.vo.RoleRespVO;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 系统级角色定义表；不用于空间资源ACL单表 CRUD；生成后需补充业务规则及关联数据处理。 */
@Service
public class RoleServiceImpl extends ServiceImpl<RoleMapper, RoleDO>
        implements RoleService {

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(RoleCreateReqVO request) {
        RoleDO entity = new RoleDO();
        BeanUtils.copyProperties(request, entity);
        if (!save(entity)) {
            throw BizException.conflict("新增失败");
        }
        return entity.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, RoleUpdateReqVO request) {
        RoleDO entity = getById(id);
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
    public RoleRespVO get(Long id) {
        RoleDO entity = getById(id);
        if (entity == null) {
            throw BizException.notFound("记录不存在");
        }
        return toResp(entity);
    }

    @Override
    public PageResult<RoleRespVO> page(RolePageReqVO request) {
        IPage<RoleDO> result = super.page(
                new Page<>(request.getPageNo(), request.getPageSize()),
                Wrappers.<RoleDO>lambdaQuery()
                        .orderByDesc(RoleDO::getCreatedAt)
        );
        return PageResult.of(
                result.getRecords().stream().map(this::toResp).toList(),
                result.getTotal(), result.getCurrent(), result.getSize()
        );
    }

    private RoleRespVO toResp(RoleDO entity) {
        RoleRespVO vo = new RoleRespVO();
        BeanUtils.copyProperties(entity, vo);
        return vo;
    }
}

