package com.community.restriction.repository.master;

import com.community.restriction.entity.MemberRestrictionEntity;
import com.community.zconfiguration.jpa.IrobitRepository;

/** 기능 제한 줄 넣기({@code persist}) — 읽기·해제는 {@link MemberRestrictionStore} */
public interface MemberRestrictionRepository extends IrobitRepository<MemberRestrictionEntity, Long> {
}
