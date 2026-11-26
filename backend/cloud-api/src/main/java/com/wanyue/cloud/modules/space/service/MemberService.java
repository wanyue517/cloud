package com.wanyue.cloud.modules.space.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.wanyue.cloud.common.result.PageResult;
import com.wanyue.cloud.modules.space.vo.MemberPageReqVO;
import com.wanyue.cloud.modules.space.vo.MemberRespVO;
import com.wanyue.cloud.modules.space.dataobject.MemberDO;
import com.wanyue.cloud.modules.space.vo.MemberCreateReqVO;
import com.wanyue.cloud.modules.space.vo.MemberUpdateReqVO;

/** 共享空间成员关系，独立于文件夹授权基础业务接口。 */
public interface MemberService extends IService<MemberDO> {
    Long create(MemberCreateReqVO request);
    void update(Long id, MemberUpdateReqVO request);
    void delete(Long id);
    MemberRespVO get(Long id);
    PageResult<MemberRespVO> page(MemberPageReqVO request);
}

