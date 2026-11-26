package com.wanyue.cloud.modules.space.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.wanyue.cloud.common.exception.BizException;
import com.wanyue.cloud.common.result.PageResult;
import com.wanyue.cloud.modules.space.vo.UserPreferenceCreateReqVO;
import com.wanyue.cloud.modules.space.dataobject.UserPreferenceDO;
import com.wanyue.cloud.modules.space.mapper.UserPreferenceMapper;
import com.wanyue.cloud.modules.space.service.UserPreferenceService;
import com.wanyue.cloud.modules.space.vo.UserPreferenceUpdateReqVO;
import com.wanyue.cloud.modules.space.vo.UserPreferencePageReqVO;
import com.wanyue.cloud.modules.space.vo.UserPreferenceRespVO;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 用户空间导航偏好；默认入口仍存sys_user_setting单表 CRUD；生成后需补充业务规则及关联数据处理。 */
@Service
public class UserPreferenceServiceImpl extends ServiceImpl<UserPreferenceMapper, UserPreferenceDO>
        implements UserPreferenceService {

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(UserPreferenceCreateReqVO request) {
        UserPreferenceDO entity = new UserPreferenceDO();
        BeanUtils.copyProperties(request, entity);
        if (!save(entity)) {
            throw BizException.conflict("新增失败");
        }
        return entity.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, UserPreferenceUpdateReqVO request) {
        UserPreferenceDO entity = getById(id);
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
    public UserPreferenceRespVO get(Long id) {
        UserPreferenceDO entity = getById(id);
        if (entity == null) {
            throw BizException.notFound("记录不存在");
        }
        return toResp(entity);
    }

    @Override
    public PageResult<UserPreferenceRespVO> page(UserPreferencePageReqVO request) {
        IPage<UserPreferenceDO> result = super.page(
                new Page<>(request.getPageNo(), request.getPageSize()),
                Wrappers.<UserPreferenceDO>lambdaQuery()
                        .orderByDesc(UserPreferenceDO::getCreatedAt)
        );
        return PageResult.of(
                result.getRecords().stream().map(this::toResp).toList(),
                result.getTotal(), result.getCurrent(), result.getSize()
        );
    }

    private UserPreferenceRespVO toResp(UserPreferenceDO entity) {
        UserPreferenceRespVO vo = new UserPreferenceRespVO();
        BeanUtils.copyProperties(entity, vo);
        return vo;
    }
}

