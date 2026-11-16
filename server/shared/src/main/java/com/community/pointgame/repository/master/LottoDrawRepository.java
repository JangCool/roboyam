package com.community.pointgame.repository.master;

import com.community.pointgame.entity.LottoDrawEntity;
import com.community.zconfiguration.jpa.IrobitRepository;

/** 뽑은 로또 번호 넣기({@code persist})·한 줄 찾기 — 목록·개수·지우기는 {@link PointGameQueries} */
public interface LottoDrawRepository extends IrobitRepository<LottoDrawEntity, Long> {
}
