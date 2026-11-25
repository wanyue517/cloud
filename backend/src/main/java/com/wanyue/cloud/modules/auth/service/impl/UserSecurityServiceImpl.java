package com.wanyue.cloud.modules.auth.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.wanyue.cloud.common.exception.BizException;
import com.wanyue.cloud.common.result.PageResult;
import com.wanyue.cloud.modules.auth.dataobject.UserSecurityDO;
import com.wanyue.cloud.modules.auth.mapper.UserSecurityMapper;
import com.wanyue.cloud.modules.auth.service.UserSecurityService;
import com.wanyue.cloud.modules.auth.vo.UserSecurityCreateReqVO;
import com.wanyue.cloud.modules.auth.vo.UserSecurityUpdateReqVO;
import com.wanyue.cloud.modules.auth.vo.UserSecurityPageReqVO;
import com.wanyue.cloud.modules.auth.vo.UserSecurityRespVO;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 用户登录安全状态表单表 CRUD；生成后需补充业务规则及关联数据处理。 */
@Service
public class UserSecurityServiceImpl extends ServiceImpl<UserSecurityMapper, UserSecurityDO>
        implements UserSecurityService {

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(UserSecurityCreateReqVO request) {
        UserSecurityDO entity = new UserSecurityDO();
        BeanUtils.copyProperties(request, entity);
        if (!save(entity)) {
            throw BizException.conflict("新增失败");
        }
        return entity.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, UserSecurityUpdateReqVO request) {
        UserSecurityDO entity = getById(id);
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
    public UserSecurityRespVO get(Long id) {
        UserSecurityDO entity = getById(id);
        if (entity == null) {
            throw BizException.notFound("记录不存在");
        }
        return toResp(entity);
    }

    @Override
    public PageResult<UserSecurityRespVO> page(UserSecurityPageReqVO request) {
        IPage<UserSecurityDO> result = super.page(
                new Page<>(request.getPageNo(), request.getPageSize()),
                Wrappers.<UserSecurityDO>lambdaQuery()
                        .orderByDesc(UserSecurityDO::getCreatedAt)
        );
        return PageResult.of(
                result.getRecords().stream().map(this::toResp).toList(),
                result.getTotal(), result.getCurrent(), result.getSize()
        );
    }

    private UserSecurityRespVO toResp(UserSecurityDO entity) {
        UserSecurityRespVO vo = new UserSecurityRespVO();
        BeanUtils.copyProperties(entity, vo);
        return vo;
    }
}

