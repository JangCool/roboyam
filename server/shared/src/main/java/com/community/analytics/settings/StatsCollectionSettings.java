package com.community.analytics.settings;

import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 통계 수집 설정의 <b>서버 메모리 사본</b> — 방문 신호·알림 발송처럼 요청마다 불리는 곳이 DB 를 읽지 않게 {@value #TTL_SECONDS}초마다 한 번만 읽는다(서버마다 따로).
 *
 * <ul>
 *   <li>관리자가 설정을 바꾸면 그 관리자 서버는 바로({@link #invalidate}), 다른 서버(공개 사이트 api 여러 대 포함)는 최대 {@value #TTL_SECONDS}초 늦게 따른다 — 통계라 허용한다.</li>
 *   <li>다시 읽다 실패하면 이전 사본을 쓰고 5초 뒤 다시 시도한다. 처음부터 읽지 못하면 모두 꺼짐({@link StatsCollection#OFF}) — 수집은 기본 꺼짐이라 안전한 쪽이다.</li>
 *   <li>크론(pg_cron)은 이 사본이 아니라 DB 행을 직접 읽는다.</li>
 * </ul>
 */
@Component
public class StatsCollectionSettings {

    private static final Logger log = LoggerFactory.getLogger(StatsCollectionSettings.class);

    static final int TTL_SECONDS = 30;
    private static final long TTL_NANOS = Duration.ofSeconds(TTL_SECONDS).toNanos();
    private static final long RETRY_NANOS = Duration.ofSeconds(5).toNanos();

    private record Cached(StatsCollection value, long loadedAt) {
    }

    private final StatsCollectionStore store;
    private volatile Cached cached;

    public StatsCollectionSettings(StatsCollectionStore store) {
        this.store = store;
    }

    /** 지금 설정(사본) */
    public StatsCollection current() {
        Cached c = cached;
        if (c != null && System.nanoTime() - c.loadedAt() < TTL_NANOS) {
            return c.value();
        }
        synchronized (this) {
            c = cached;
            long now = System.nanoTime();
            if (c != null && now - c.loadedAt() < TTL_NANOS) {
                return c.value();
            }
            try {
                StatsCollection loaded = store.find().map(StatsCollectionStore.Stored::value).orElse(StatsCollection.OFF);
                cached = new Cached(loaded, now);
                return loaded;
            } catch (RuntimeException e) {
                StatsCollection fallback = c == null ? StatsCollection.OFF : c.value();
                log.warn("통계 수집 설정을 읽지 못해 {}을 쓴다: {}", c == null ? "꺼짐" : "이전 값", e.getClass().getSimpleName());
                cached = new Cached(fallback, now - TTL_NANOS + RETRY_NANOS);
                return fallback;
            }
        }
    }

    /** 사본을 버린다 — 이 서버에서 설정을 저장한 뒤 */
    public void invalidate() {
        cached = null;
    }
}
