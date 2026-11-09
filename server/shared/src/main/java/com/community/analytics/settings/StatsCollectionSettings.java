package com.community.analytics.settings;

import com.community.cache.definition.CacheNames;
import org.springframework.cache.CacheManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 통계 수집 설정의 <b>서버 메모리 사본</b>(캐시 {@code stats.collection}, 기본 30초) — 방문 신호·알림 발송처럼 요청마다 불리는 곳이 DB 를 읽지 않게 한다(서버마다 따로).
 *
 * <ul>
 *   <li>관리자가 설정을 바꾸면 그 관리자 서버는 바로({@link #invalidate}), 다른 서버(공개 사이트 api 여러 대 포함)는 세대 번호로 몇 초 안에 따른다.</li>
 *   <li>읽지 못하면 모두 꺼짐({@link StatsCollection#OFF}) — 수집은 기본 꺼짐이라 안전한 쪽이다.</li>
 *   <li>크론(pg_cron)은 이 사본이 아니라 DB 행을 직접 읽는다.</li>
 * </ul>
 */
@Component
public class StatsCollectionSettings {

    private static final Logger log = LoggerFactory.getLogger(StatsCollectionSettings.class);



    private final StatsCollectionStore store;
    private final CacheManager caches;

    public StatsCollectionSettings(StatsCollectionStore store, CacheManager caches) {
        this.caches = caches;
        this.store = store;
    }

    /** 지금 설정 — 캐시 {@code stats.collection}. 읽지 못하면 모두 꺼짐(담지 않는다 — 다음 요청이 다시 읽는다) */
    public StatsCollection current() {
        try {
            return caches.getCache(CacheNames.STATS_COLLECTION).get(CacheNames.SINGLE,
                    () -> store.find().map(StatsCollectionStore.Stored::value).orElse(StatsCollection.OFF));
        } catch (RuntimeException e) {
            log.warn("통계 수집 설정을 읽지 못해 꺼짐을 쓴다: {}", e.getClass().getSimpleName());
            return StatsCollection.OFF;
        }
    }

    /** 이 서버의 사본을 버린다(다른 서버는 관리자 저장의 세대 번호로 따라온다) */
    public void invalidate() {
        caches.getCache(CacheNames.STATS_COLLECTION).clear();
    }
}
