package com.community.title.service;

import com.community.title.entity.TitleSettingEntity;
import com.community.title.repository.master.TitleSettingRepository;
import org.springframework.stereotype.Component;

/** 작가 칭호 설정 읽기(행 하나 — 없으면 기본값 3·3·30, V240 이 넣으므로 생기지 않는다) */
@Component
public class TitleSettingsReader {

    /** 설정 값 한 벌 */
    public record Values(int monthlyMax, int honoraryCount, int reapplyDays) {
        public static final Values DEFAULTS = new Values(3, 3, 30);
    }

    private final TitleSettingRepository settings;

    public TitleSettingsReader(TitleSettingRepository settings) {
        this.settings = settings;
    }

    public Values current() {
        return settings.findById(TitleSettingEntity.ID).map(s -> new Values(s.getMonthlyMax(), s.getHonoraryCount(), s.getReapplyDays())).orElse(Values.DEFAULTS);
    }
}
