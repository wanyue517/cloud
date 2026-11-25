package com.wanyue.cloud.modules.auth.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.wanyue.cloud.common.exception.BizException;
import com.wanyue.cloud.common.result.PageResult;
import com.wanyue.cloud.modules.auth.dataobject.SessionDO;
import com.wanyue.cloud.modules.auth.mapper.SessionMapper;
import com.wanyue.cloud.modules.auth.service.SessionService;
import com.wanyue.cloud.modules.auth.vo.SessionCreateReqVO;
import com.wanyue.cloud.modules.auth.vo.SessionUpdateReqVO;
import com.wanyue.cloud.modules.auth.vo.SessionPageReqVO;
import com.wanyue.cloud.modules.auth.vo.SessionRespVO;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 用户登录会话与Refresh Token表单表 CRUD；生成后需补充业务规则及关联数据处理。 */
@Service
public class SessionServiceImpl extends ServiceImpl<SessionMapper, SessionDO>
        implements SessionService {

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(SessionCreateReqVO request) {
        SessionDO entity = new SessionDO();
        BeanUtils.copyProperties(request, entity);
        if (!save(entity)) {
            throw BizException.conflict("新增失败");
        }
        return entity.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, SessionUpdateReqVO request) {
        SessionDO entity = getById(id);
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
    public SessionRespVO get(Long id) {
        SessionDO entity = getById(id);
        if (entity == null) {
            throw BizException.notFound("记录不存在");
        }
        return toResp(entity);
    }

    @Override
    public PageResult<SessionRespVO> page(SessionPageReqVO request) {
        IPage<SessionDO> result = super.page(
                new Page<>(request.getPageNo(), request.getPageSize()),
                Wrappers.<SessionDO>lambdaQuery()
                        .orderByDesc(SessionDO::getCreatedAt)
        );
        return PageResult.of(
                result.getRecords().stream().map(this::toResp).toList(),
                result.getTotal(), result.getCurrent(), result.getSize()
        );
    }

    private SessionRespVO toResp(SessionDO entity) {
        SessionRespVO vo = new SessionRespVO();
        BeanUtils.copyProperties(entity, vo);
        return vo;
    }
}

