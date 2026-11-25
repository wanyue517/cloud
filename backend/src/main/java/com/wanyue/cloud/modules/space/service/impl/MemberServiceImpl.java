package com.wanyue.cloud.modules.space.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.wanyue.cloud.common.exception.BizException;
import com.wanyue.cloud.common.result.PageResult;
import com.wanyue.cloud.modules.space.vo.MemberRespVO;
import com.wanyue.cloud.modules.space.dataobject.MemberDO;
import com.wanyue.cloud.modules.space.mapper.MemberMapper;
import com.wanyue.cloud.modules.space.service.MemberService;
import com.wanyue.cloud.modules.space.vo.MemberCreateReqVO;
import com.wanyue.cloud.modules.space.vo.MemberUpdateReqVO;
import com.wanyue.cloud.modules.space.vo.MemberPageReqVO;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 共享空间成员关系，独立于文件夹授权单表 CRUD；生成后需补充业务规则及关联数据处理。 */
@Service
public class MemberServiceImpl extends ServiceImpl<MemberMapper, MemberDO>
        implements MemberService {

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(MemberCreateReqVO request) {
        MemberDO entity = new MemberDO();
        BeanUtils.copyProperties(request, entity);
        if (!save(entity)) {
            throw BizException.conflict("新增失败");
        }
        return entity.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, MemberUpdateReqVO request) {
        MemberDO entity = getById(id);
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
    public MemberRespVO get(Long id) {
        MemberDO entity = getById(id);
        if (entity == null) {
            throw BizException.notFound("记录不存在");
        }
        return toResp(entity);
    }

    @Override
    public PageResult<MemberRespVO> page(MemberPageReqVO request) {
        IPage<MemberDO> result = super.page(
                new Page<>(request.getPageNo(), request.getPageSize()),
                Wrappers.<MemberDO>lambdaQuery()
                        .orderByDesc(MemberDO::getCreatedAt)
        );
        return PageResult.of(
                result.getRecords().stream().map(this::toResp).toList(),
                result.getTotal(), result.getCurrent(), result.getSize()
        );
    }

    private MemberRespVO toResp(MemberDO entity) {
        MemberRespVO vo = new MemberRespVO();
        BeanUtils.copyProperties(entity, vo);
        return vo;
    }
}

