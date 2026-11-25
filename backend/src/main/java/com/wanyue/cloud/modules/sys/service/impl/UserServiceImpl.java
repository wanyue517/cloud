package com.wanyue.cloud.modules.sys.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.wanyue.cloud.common.exception.BizException;
import com.wanyue.cloud.common.result.PageResult;
import com.wanyue.cloud.modules.sys.dataobject.UserDO;
import com.wanyue.cloud.modules.sys.mapper.UserMapper;
import com.wanyue.cloud.modules.sys.service.UserService;
import com.wanyue.cloud.modules.sys.vo.UserCreateReqVO;
import com.wanyue.cloud.modules.sys.vo.UserUpdateReqVO;
import com.wanyue.cloud.modules.sys.vo.UserPageReqVO;
import com.wanyue.cloud.modules.sys.vo.UserRespVO;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 系统用户主表单表 CRUD；生成后需补充业务规则及关联数据处理。 */
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, UserDO>
        implements UserService {

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(UserCreateReqVO request) {
        UserDO entity = new UserDO();
        BeanUtils.copyProperties(request, entity);
        if (!save(entity)) {
            throw BizException.conflict("新增失败");
        }
        return entity.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, UserUpdateReqVO request) {
        UserDO entity = getById(id);
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
    public UserRespVO get(Long id) {
        UserDO entity = getById(id);
        if (entity == null) {
            throw BizException.notFound("记录不存在");
        }
        return toResp(entity);
    }

    @Override
    public PageResult<UserRespVO> page(UserPageReqVO request) {
        IPage<UserDO> result = super.page(
                new Page<>(request.getPageNo(), request.getPageSize()),
                Wrappers.<UserDO>lambdaQuery()
                        .orderByDesc(UserDO::getCreatedAt)
        );
        return PageResult.of(
                result.getRecords().stream().map(this::toResp).toList(),
                result.getTotal(), result.getCurrent(), result.getSize()
        );
    }

    private UserRespVO toResp(UserDO entity) {
        UserRespVO vo = new UserRespVO();
        BeanUtils.copyProperties(entity, vo);
        return vo;
    }
}

