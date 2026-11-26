package com.wanyue.cloud.modules.auth.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.wanyue.cloud.common.exception.BizException;
import com.wanyue.cloud.common.result.PageResult;
import com.wanyue.cloud.modules.auth.dataobject.SecurityLogDO;
import com.wanyue.cloud.modules.auth.enums.SecurityLogEventTypeEnum;
import com.wanyue.cloud.modules.auth.mapper.SecurityLogMapper;
import com.wanyue.cloud.modules.auth.service.SecurityLogService;
import com.wanyue.cloud.modules.auth.vo.SecurityLogCreateReqVO;
import com.wanyue.cloud.modules.auth.vo.SecurityLogUpdateReqVO;
import com.wanyue.cloud.modules.auth.vo.SecurityLogPageReqVO;
import com.wanyue.cloud.modules.auth.vo.SecurityLogRespVO;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 账号安全事件审计日志单表 CRUD；生成后需补充业务规则及关联数据处理。
 */
@Service
public class SecurityLogServiceImpl extends ServiceImpl<SecurityLogMapper, SecurityLogDO>
        implements SecurityLogService {

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(SecurityLogCreateReqVO request) {
        SecurityLogDO entity = new SecurityLogDO();
        BeanUtils.copyProperties(request, entity);
        if (!save(entity)) {
            throw BizException.conflict("新增失败");
        }
        return entity.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, SecurityLogUpdateReqVO request) {
        SecurityLogDO entity = getById(id);
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
    public SecurityLogRespVO get(Long id) {
        SecurityLogDO entity = getById(id);
        if (entity == null) {
            throw BizException.notFound("记录不存在");
        }
        return toResp(entity);
    }

    @Override
    public PageResult<SecurityLogRespVO> page(SecurityLogPageReqVO request) {
        IPage<SecurityLogDO> result = super.page(
                new Page<>(request.getPageNo(), request.getPageSize()),
                Wrappers.<SecurityLogDO>lambdaQuery()
                        .orderByDesc(SecurityLogDO::getCreatedAt)
        );
        return PageResult.of(
                result.getRecords().stream().map(this::toResp).toList(),
                result.getTotal(), result.getCurrent(), result.getSize()
        );
    }

    @Override
    public void recordRegisterSuccess(Long id, String code) {
        SecurityLogDO securityLog = new SecurityLogDO();
        securityLog.setUserId(id);
        securityLog.setEventType(SecurityLogEventTypeEnum.EMAIL_REGISTER.getCode());
        securityLog.setEventResult(1);
        this.save(securityLog);
    }

    private SecurityLogRespVO toResp(SecurityLogDO entity) {
        SecurityLogRespVO vo = new SecurityLogRespVO();
        BeanUtils.copyProperties(entity, vo);
        return vo;
    }
}

