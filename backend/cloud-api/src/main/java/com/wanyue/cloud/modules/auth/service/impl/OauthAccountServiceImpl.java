package com.wanyue.cloud.modules.auth.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.wanyue.cloud.common.exception.BizException;
import com.wanyue.cloud.common.result.PageResult;
import com.wanyue.cloud.modules.auth.dataobject.OauthAccountDO;
import com.wanyue.cloud.modules.auth.mapper.OauthAccountMapper;
import com.wanyue.cloud.modules.auth.service.OauthAccountService;
import com.wanyue.cloud.modules.auth.vo.OauthAccountCreateReqVO;
import com.wanyue.cloud.modules.auth.vo.OauthAccountUpdateReqVO;
import com.wanyue.cloud.modules.auth.vo.OauthAccountPageReqVO;
import com.wanyue.cloud.modules.auth.vo.OauthAccountRespVO;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 第三方OAuth账号绑定表单表 CRUD；生成后需补充业务规则及关联数据处理。 */
@Service
public class OauthAccountServiceImpl extends ServiceImpl<OauthAccountMapper, OauthAccountDO>
        implements OauthAccountService {

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(OauthAccountCreateReqVO request) {
        OauthAccountDO entity = new OauthAccountDO();
        BeanUtils.copyProperties(request, entity);
        if (!save(entity)) {
            throw BizException.conflict("新增失败");
        }
        return entity.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, OauthAccountUpdateReqVO request) {
        OauthAccountDO entity = getById(id);
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
    public OauthAccountRespVO get(Long id) {
        OauthAccountDO entity = getById(id);
        if (entity == null) {
            throw BizException.notFound("记录不存在");
        }
        return toResp(entity);
    }

    @Override
    public PageResult<OauthAccountRespVO> page(OauthAccountPageReqVO request) {
        IPage<OauthAccountDO> result = super.page(
                new Page<>(request.getPageNo(), request.getPageSize()),
                Wrappers.<OauthAccountDO>lambdaQuery()
                        .orderByDesc(OauthAccountDO::getCreatedAt)
        );
        return PageResult.of(
                result.getRecords().stream().map(this::toResp).toList(),
                result.getTotal(), result.getCurrent(), result.getSize()
        );
    }

    private OauthAccountRespVO toResp(OauthAccountDO entity) {
        OauthAccountRespVO vo = new OauthAccountRespVO();
        BeanUtils.copyProperties(entity, vo);
        return vo;
    }
}

