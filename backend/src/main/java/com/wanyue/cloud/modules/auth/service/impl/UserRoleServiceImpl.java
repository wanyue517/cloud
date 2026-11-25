package com.wanyue.cloud.modules.auth.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.wanyue.cloud.common.exception.BizException;
import com.wanyue.cloud.common.result.PageResult;
import com.wanyue.cloud.modules.auth.dataobject.RoleDO;
import com.wanyue.cloud.modules.auth.dataobject.UserRoleDO;
import com.wanyue.cloud.modules.auth.mapper.UserRoleMapper;
import com.wanyue.cloud.modules.auth.service.RoleService;
import com.wanyue.cloud.modules.auth.service.UserRoleService;
import com.wanyue.cloud.modules.auth.vo.UserRoleCreateReqVO;
import com.wanyue.cloud.modules.auth.vo.UserRoleUpdateReqVO;
import com.wanyue.cloud.modules.auth.vo.UserRolePageReqVO;
import com.wanyue.cloud.modules.auth.vo.UserRoleRespVO;
import jakarta.annotation.Resource;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 用户与系统角色关联表单表 CRUD；生成后需补充业务规则及关联数据处理。 */
@Service
public class UserRoleServiceImpl extends ServiceImpl<UserRoleMapper, UserRoleDO>
        implements UserRoleService {

    @Resource
    private RoleService roleService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(UserRoleCreateReqVO request) {
        UserRoleDO entity = new UserRoleDO();
        BeanUtils.copyProperties(request, entity);
        if (!save(entity)) {
            throw BizException.conflict("新增失败");
        }
        return entity.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, UserRoleUpdateReqVO request) {
        UserRoleDO entity = getById(id);
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
    public UserRoleRespVO get(Long id) {
        UserRoleDO entity = getById(id);
        if (entity == null) {
            throw BizException.notFound("记录不存在");
        }
        return toResp(entity);
    }

    @Override
    public PageResult<UserRoleRespVO> page(UserRolePageReqVO request) {
        IPage<UserRoleDO> result = super.page(
                new Page<>(request.getPageNo(), request.getPageSize()),
                Wrappers.<UserRoleDO>lambdaQuery()
                        .orderByDesc(UserRoleDO::getCreatedAt)
        );
        return PageResult.of(
                result.getRecords().stream().map(this::toResp).toList(),
                result.getTotal(), result.getCurrent(), result.getSize()
        );
    }

    @Override
    public void bindRole(Long id, String code) {
        RoleDO role = roleService.getOne(Wrappers.<RoleDO>lambdaQuery().eq(RoleDO::getRoleCode, code));
        if(role == null){
            throw BizException.notFound("角色不存在");
        }
        UserRoleDO userRole = new UserRoleDO();
        userRole.setUserId(id);
        userRole.setRoleId(role.getId());
        this.save(userRole);
    }

    private UserRoleRespVO toResp(UserRoleDO entity) {
        UserRoleRespVO vo = new UserRoleRespVO();
        BeanUtils.copyProperties(entity, vo);
        return vo;
    }
}

