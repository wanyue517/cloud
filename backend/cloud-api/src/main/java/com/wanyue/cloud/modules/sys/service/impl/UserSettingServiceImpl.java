package com.wanyue.cloud.modules.sys.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.wanyue.cloud.common.exception.BizException;
import com.wanyue.cloud.common.result.PageResult;
import com.wanyue.cloud.modules.sys.service.UserSettingService;
import com.wanyue.cloud.modules.sys.vo.UserSettingUpdateReqVO;
import com.wanyue.cloud.modules.sys.dataobject.UserSettingDO;
import com.wanyue.cloud.modules.sys.mapper.UserSettingMapper;
import com.wanyue.cloud.modules.sys.vo.UserSettingCreateReqVO;
import com.wanyue.cloud.modules.sys.vo.UserSettingPageReqVO;
import com.wanyue.cloud.modules.sys.vo.UserSettingRespVO;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 用户个性化设置表单表 CRUD；生成后需补充业务规则及关联数据处理。 */
@Service
public class UserSettingServiceImpl extends ServiceImpl<UserSettingMapper, UserSettingDO>
        implements UserSettingService {

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(UserSettingCreateReqVO request) {
        UserSettingDO entity = new UserSettingDO();
        BeanUtils.copyProperties(request, entity);
        if (!save(entity)) {
            throw BizException.conflict("新增失败");
        }
        return entity.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, UserSettingUpdateReqVO request) {
        UserSettingDO entity = getById(id);
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
    public UserSettingRespVO get(Long id) {
        UserSettingDO entity = getById(id);
        if (entity == null) {
            throw BizException.notFound("记录不存在");
        }
        return toResp(entity);
    }

    @Override
    public PageResult<UserSettingRespVO> page(UserSettingPageReqVO request) {
        IPage<UserSettingDO> result = super.page(
                new Page<>(request.getPageNo(), request.getPageSize()),
                Wrappers.<UserSettingDO>lambdaQuery()
                        .orderByDesc(UserSettingDO::getCreatedAt)
        );
        return PageResult.of(
                result.getRecords().stream().map(this::toResp).toList(),
                result.getTotal(), result.getCurrent(), result.getSize()
        );
    }

    private UserSettingRespVO toResp(UserSettingDO entity) {
        UserSettingRespVO vo = new UserSettingRespVO();
        BeanUtils.copyProperties(entity, vo);
        return vo;
    }
}

