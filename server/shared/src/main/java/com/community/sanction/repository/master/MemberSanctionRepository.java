package com.community.sanction.repository.master;

import com.community.sanction.entity.MemberSanctionEntity;
import com.community.zconfiguration.jpa.IrobitRepository;

/** {@code MemberSanctionEntity} 넣기({@code persist}) — 읽기와 조건부 고치기는 저장소의 QueryDSL */
public interface MemberSanctionRepository extends IrobitRepository<MemberSanctionEntity, Long> {
}
