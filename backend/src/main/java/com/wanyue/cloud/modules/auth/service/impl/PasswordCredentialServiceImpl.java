package com.wanyue.cloud.modules.auth.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.wanyue.cloud.common.exception.BizException;
import com.wanyue.cloud.common.result.PageResult;
import com.wanyue.cloud.modules.auth.dataobject.PasswordCredentialDO;
import com.wanyue.cloud.modules.auth.mapper.PasswordCredentialMapper;
import com.wanyue.cloud.modules.auth.service.PasswordCredentialService;
import com.wanyue.cloud.modules.auth.vo.PasswordCredentialCreateReqVO;
import com.wanyue.cloud.modules.auth.vo.PasswordCredentialUpdateReqVO;
import com.wanyue.cloud.modules.auth.vo.PasswordCredentialPageReqVO;
import com.wanyue.cloud.modules.auth.vo.PasswordCredentialRespVO;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 用户密码凭证表：每用户最多一份有效密码单表 CRUD；生成后需补充业务规则及关联数据处理。 */
@Service
public class PasswordCredentialServiceImpl extends ServiceImpl<PasswordCredentialMapper, PasswordCredentialDO>
        implements PasswordCredentialService {

    @Override
    public PasswordCredentialDO getByUserId(Long userId) {
        return getOne(Wrappers.<PasswordCredentialDO>lambdaQuery()
                .eq(PasswordCredentialDO::getUserId, userId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(PasswordCredentialCreateReqVO request) {
        PasswordCredentialDO entity = new PasswordCredentialDO();
        BeanUtils.copyProperties(request, entity);
        if (!save(entity)) {
            throw BizException.conflict("新增失败");
        }
        return entity.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, PasswordCredentialUpdateReqVO request) {
        PasswordCredentialDO entity = getById(id);
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
    public PasswordCredentialRespVO get(Long id) {
        PasswordCredentialDO entity = getById(id);
        if (entity == null) {
            throw BizException.notFound("记录不存在");
        }
        return toResp(entity);
    }

    @Override
    public PageResult<PasswordCredentialRespVO> page(PasswordCredentialPageReqVO request) {
        IPage<PasswordCredentialDO> result = super.page(
                new Page<>(request.getPageNo(), request.getPageSize()),
                Wrappers.<PasswordCredentialDO>lambdaQuery()
                        .orderByDesc(PasswordCredentialDO::getCreatedAt)
        );
        return PageResult.of(
                result.getRecords().stream().map(this::toResp).toList(),
                result.getTotal(), result.getCurrent(), result.getSize()
        );
    }

    private PasswordCredentialRespVO toResp(PasswordCredentialDO entity) {
        PasswordCredentialRespVO vo = new PasswordCredentialRespVO();
        BeanUtils.copyProperties(entity, vo);
        return vo;
    }
}

