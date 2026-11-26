package com.wanyue.cloud.modules.sys.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.wanyue.cloud.common.exception.BizException;
import com.wanyue.cloud.common.result.PageResult;
import com.wanyue.cloud.modules.sys.service.UserProfileService;
import com.wanyue.cloud.modules.sys.vo.UserProfileUpdateReqVO;
import com.wanyue.cloud.modules.sys.dataobject.UserProfileDO;
import com.wanyue.cloud.modules.sys.mapper.UserProfileMapper;
import com.wanyue.cloud.modules.sys.vo.UserProfileCreateReqVO;
import com.wanyue.cloud.modules.sys.vo.UserProfilePageReqVO;
import com.wanyue.cloud.modules.sys.vo.UserProfileRespVO;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 用户扩展资料表单表 CRUD；生成后需补充业务规则及关联数据处理。 */
@Service
public class UserProfileServiceImpl extends ServiceImpl<UserProfileMapper, UserProfileDO>
        implements UserProfileService {

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(UserProfileCreateReqVO request) {
        UserProfileDO entity = new UserProfileDO();
        BeanUtils.copyProperties(request, entity);
        if (!save(entity)) {
            throw BizException.conflict("新增失败");
        }
        return entity.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, UserProfileUpdateReqVO request) {
        UserProfileDO entity = getById(id);
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
    public UserProfileRespVO get(Long id) {
        UserProfileDO entity = getById(id);
        if (entity == null) {
            throw BizException.notFound("记录不存在");
        }
        return toResp(entity);
    }

    @Override
    public PageResult<UserProfileRespVO> page(UserProfilePageReqVO request) {
        IPage<UserProfileDO> result = super.page(
                new Page<>(request.getPageNo(), request.getPageSize()),
                Wrappers.<UserProfileDO>lambdaQuery()
                        .orderByDesc(UserProfileDO::getCreatedAt)
        );
        return PageResult.of(
                result.getRecords().stream().map(this::toResp).toList(),
                result.getTotal(), result.getCurrent(), result.getSize()
        );
    }

    private UserProfileRespVO toResp(UserProfileDO entity) {
        UserProfileRespVO vo = new UserProfileRespVO();
        BeanUtils.copyProperties(entity, vo);
        return vo;
    }
}

