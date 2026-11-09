package com.community.hotdeal.service;

import com.community.cache.definition.CacheNames;
import org.springframework.cache.CacheManager;
import com.community.hotdeal.dto.DealSettings;
import com.community.hotdeal.entity.DealSettingEntity;
import com.community.hotdeal.repository.master.DealSettingRepository;
import org.springframework.stereotype.Component;

/**
 * 핫딜 설정의 <b>짧은 메모리 사본</b> — 글 읽기·쓰기마다 설정 행을 읽지 않게 한다(포인트 정책 {@code PointPolicy}·게시판 사본 {@code BoardCatalog} 와 같은 방식).
 *
 * <ul>
 *   <li>캐시 {@code hotdeal.settings}(기본 15초)에 둔다 — 관리자 저장은 세대 번호로 몇 초 안에 모든 서버에 닿는다. 같은 서버 안에서는 {@link #invalidate()}.</li>
 *   <li>읽다 실패하면 담지 않고 그 요청이 실패한다. 행이 없으면 기본값({@link DealSettings#DEFAULTS}).</li>
 * </ul>
 */
@Component
public class DealSettingsReader {


    private final DealSettingRepository settings;
    private final CacheManager caches;

    public DealSettingsReader(DealSettingRepository settings, CacheManager caches) {
        this.caches = caches;
        this.settings = settings;
    }

    /** 지금 설정 — 캐시 {@code hotdeal.settings} */
    public DealSettings current() {
        return caches.getCache(CacheNames.HOTDEAL_SETTINGS).get(CacheNames.SINGLE, this::load);
    }

    /** 이 서버의 사본을 버린다 — 다음 읽기에서 바로 다시 읽는다(다른 서버는 관리자 저장의 세대 번호로 따라온다) */
    public void invalidate() {
        caches.getCache(CacheNames.HOTDEAL_SETTINGS).clear();
    }

    private DealSettings load() {
        return settings.findById(DealSettingEntity.ROW_ID).map(DealSettingEntity::settings).orElse(DealSettings.DEFAULTS);
    }
}
