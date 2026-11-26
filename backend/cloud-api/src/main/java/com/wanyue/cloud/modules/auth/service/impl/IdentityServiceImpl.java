package com.wanyue.cloud.modules.auth.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.wanyue.cloud.common.exception.BizException;
import com.wanyue.cloud.common.result.PageResult;
import com.wanyue.cloud.modules.auth.dataobject.IdentityDO;
import com.wanyue.cloud.modules.auth.enums.IdentityTypeEnum;
import com.wanyue.cloud.modules.auth.mapper.IdentityMapper;
import com.wanyue.cloud.modules.sys.mapper.UserMapper;
import com.wanyue.cloud.modules.auth.service.IdentityService;
import com.wanyue.cloud.modules.auth.vo.IdentityCreateReqVO;
import com.wanyue.cloud.modules.auth.vo.IdentityUpdateReqVO;
import com.wanyue.cloud.modules.auth.vo.IdentityPageReqVO;
import com.wanyue.cloud.modules.auth.vo.IdentityRespVO;
import jakarta.annotation.Resource;
import com.wanyue.cloud.modules.auth.support.EmailNormalizer;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Objects;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 用户登录标识表：用户名、邮箱、手机号单表 CRUD；生成后需补充业务规则及关联数据处理。 */
@Service
public class IdentityServiceImpl extends ServiceImpl<IdentityMapper, IdentityDO>
        implements IdentityService {

    @Resource
    private UserMapper userMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(IdentityCreateReqVO request) {
        IdentityDO entity = new IdentityDO();
        BeanUtils.copyProperties(request, entity);
        lockUser(entity.getUserId());
        preparePrimary(entity);
        if (!save(entity)) {
            throw BizException.conflict("新增失败");
        }
        return entity.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, IdentityUpdateReqVO request) {
        IdentityDO entity = getById(id);
        if (entity == null) {
            throw BizException.notFound("记录不存在");
        }
        lockUser(entity.getUserId());
        entity = lockedIdentity(id);
        if (entity == null) {
            throw BizException.notFound("记录不存在");
        }
        if (!Objects.equals(request.getVersion(), entity.getVersion())) {
            throw BizException.conflict("记录已变更，请刷新后重试");
        }
        // 身份归属和类型不允许通过修改迁移，需解绑后重新绑定。
        if ((request.getUserId() != null && !Objects.equals(request.getUserId(), entity.getUserId()))
                || (request.getIdentityType() != null
                    && !Objects.equals(request.getIdentityType(), entity.getIdentityType()))) {
            throw BizException.conflict("不能修改身份所属用户或类型");
        }
        if (Boolean.TRUE.equals(entity.getIsPrimary()) && Boolean.FALSE.equals(request.getIsPrimary())) {
            throw BizException.conflict("请将另一身份设为首选，不能直接取消首选身份");
        }
        // 请求 version 必须与客户端读取该记录时的版本一致。
        // BeanUtils 复制版本值后，MP 乐观锁将按该版本执行条件更新。
        BeanUtils.copyProperties(request, entity, "userId", "identityType", "isPrimary", "identifier");
        if (request.getIdentifier() != null) {
            entity.setIdentifier(request.getIdentifier());
        }
        if (request.getIsPrimary() != null) {
            entity.setIsPrimary(request.getIsPrimary());
        }
        preparePrimary(entity);
        if (!updateById(entity)) {
            throw BizException.conflict("记录已变更，请刷新后重试");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        IdentityDO entity = getById(id);
        if (entity == null) {
            throw BizException.notFound("记录不存在");
        }
        lockUser(entity.getUserId());
        entity = lockedIdentity(id);
        if (entity == null) {
            throw BizException.notFound("记录不存在");
        }
        if (!removeById(id)) {
            throw BizException.notFound("记录不存在");
        }
        if (Boolean.TRUE.equals(entity.getIsPrimary())) {
            IdentityDO next = getOne(Wrappers.<IdentityDO>lambdaQuery()
                    .eq(IdentityDO::getUserId, entity.getUserId())
                    .eq(IdentityDO::getIdentityType, entity.getIdentityType())
                    .orderByAsc(IdentityDO::getCreatedAt, IdentityDO::getId).last("LIMIT 1 FOR UPDATE"));
            if (next != null) {
                next.setIsPrimary(true);
                if (!updateById(next)) {
                    throw BizException.conflict("首选身份已变更，请重试");
                }
            }
        }
    }

    @Override
    public IdentityRespVO get(Long id) {
        IdentityDO entity = getById(id);
        if (entity == null) {
            throw BizException.notFound("记录不存在");
        }
        return toResp(entity);
    }

    @Override
    public PageResult<IdentityRespVO> page(IdentityPageReqVO request) {
        IPage<IdentityDO> result = super.page(
                new Page<>(request.getPageNo(), request.getPageSize()),
                Wrappers.<IdentityDO>lambdaQuery()
                        .orderByDesc(IdentityDO::getCreatedAt)
        );
        return PageResult.of(
                result.getRecords().stream().map(this::toResp).toList(),
                result.getTotal(), result.getCurrent(), result.getSize()
        );
    }

    @Override
    public Boolean checkEmail(String email) {
        IdentityDO identity = this.getOne(Wrappers.<IdentityDO>lambdaQuery()
                .eq(IdentityDO::getIdentityType, IdentityTypeEnum.EMAIL.getCode())
                .eq(IdentityDO::getIdentifier, EmailNormalizer.normalize(email))
        );
        if (identity == null) {
            return false;
        }
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void bindEmail(Long id, String email) {
        lockUser(id);
        IdentityDO identity = new IdentityDO();
        identity.setIdentityType(IdentityTypeEnum.EMAIL.getCode());
        identity.setIdentifier(EmailNormalizer.normalize(email));
        identity.setVerifiedAt(LocalDateTime.now(ZoneOffset.UTC));
        identity.setUserId(id);
        preparePrimary(identity);
        if (!save(identity)) {
            throw BizException.conflict("绑定邮箱失败");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void setPrimary(Long userId, Long identityId) {
        lockUser(userId);
        IdentityDO identity = lockedIdentity(identityId);
        if (identity == null || !Objects.equals(identity.getUserId(), userId)) {
            throw BizException.notFound("身份不存在");
        }
        identity.setIsPrimary(true);
        preparePrimary(identity);
        if (!updateById(identity)) {
            throw BizException.conflict("首选身份已变更，请重试");
        }
    }

    private void lockUser(Long userId) {
        if (userId == null || userMapper.lockActiveById(userId) == null) {
            throw BizException.notFound("用户不存在");
        }
    }

    private IdentityDO lockedIdentity(Long id) {
        return getOne(Wrappers.<IdentityDO>lambdaQuery()
                .eq(IdentityDO::getId, id).last("FOR UPDATE"));
    }

    /** 调用前持有用户行锁；数据库唯一索引另行兜底并发/直接SQL写入。 */
    private void preparePrimary(IdentityDO identity) {
        if (identity.getIdentityType() == null || identity.getIdentityType().isBlank()) {
            throw BizException.conflict("身份类型不能为空");
        }
        if (IdentityTypeEnum.EMAIL.getCode().equals(identity.getIdentityType())) {
            identity.setIdentifier(EmailNormalizer.normalize(identity.getIdentifier()));
        }
        if (Boolean.TRUE.equals(identity.getIsPrimary())) {
            update(Wrappers.<IdentityDO>lambdaUpdate()
                    .eq(IdentityDO::getUserId, identity.getUserId())
                    .eq(IdentityDO::getIdentityType, identity.getIdentityType())
                    .eq(IdentityDO::getIsPrimary, true)
                    .ne(identity.getId() != null, IdentityDO::getId, identity.getId())
                    .set(IdentityDO::getIsPrimary, false)
                    .set(IdentityDO::getUpdatedAt, LocalDateTime.now(ZoneOffset.UTC))
                    .setSql("version = version + 1"));
        } else {
            boolean hasPrimary = getOne(Wrappers.<IdentityDO>lambdaQuery()
                    .eq(IdentityDO::getUserId, identity.getUserId())
                    .eq(IdentityDO::getIdentityType, identity.getIdentityType())
                    .eq(IdentityDO::getIsPrimary, true).last("FOR UPDATE")) != null;
            identity.setIsPrimary(!hasPrimary);
        }
    }

    @Override
    public IdentityDO getByEmail(String email) {
        return this.getOne(Wrappers.<IdentityDO>lambdaQuery()
                .eq(IdentityDO::getIdentityType, IdentityTypeEnum.EMAIL.getCode())
                .eq(IdentityDO::getIdentifier, EmailNormalizer.normalize(email)));
    }

    private IdentityRespVO toResp(IdentityDO entity) {
        IdentityRespVO vo = new IdentityRespVO();
        BeanUtils.copyProperties(entity, vo);
        return vo;
    }
}

