package com.community.hotdeal.repository.master;

import com.community.hotdeal.entity.DealSettingEntity;
import com.community.zconfiguration.jpa.IrobitRepository;

/** 핫딜 설정 행({@code deal_settings}, id = 1) — 공개 쪽 사본 읽기({@code DealSettingsReader}). 관리자 잠금 조회·저장은 admin {@code DealSettingsStore} */
public interface DealSettingRepository extends IrobitRepository<DealSettingEntity, Integer> {
}
