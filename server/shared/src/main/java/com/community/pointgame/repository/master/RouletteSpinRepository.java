package com.community.pointgame.repository.master;

import com.community.pointgame.entity.RouletteSpinEntity;
import com.community.zconfiguration.jpa.IrobitRepository;

/** 룰렛 돌린 기록 넣기({@code persist}) — 읽기는 {@link PointGameQueries} */
public interface RouletteSpinRepository extends IrobitRepository<RouletteSpinEntity, Long> {
}
