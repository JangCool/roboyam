package com.community.sanction.repository.master;

import com.community.sanction.entity.BoardSanctionEntity;
import com.community.zconfiguration.jpa.IrobitRepository;

/** {@code BoardSanctionEntity} 넣기({@code persist}) — 읽기와 조건부 고치기는 저장소의 QueryDSL */
public interface BoardSanctionRepository extends IrobitRepository<BoardSanctionEntity, Long> {
}
