package com.community.hotdeal.service;

import com.community.hotdeal.dto.DealSettings;
import com.community.hotdeal.entity.DealSettingEntity;
import com.community.hotdeal.repository.master.DealSettingRepository;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 핫딜 설정의 <b>짧은 메모리 사본</b> — 글 읽기·쓰기마다 설정 행을 읽지 않게 한다(포인트 정책 {@code PointPolicy}·게시판 사본 {@code BoardCatalog} 와 같은 방식).
 *
 * <ul>
 *   <li>{@value #TTL_SECONDS}초마다 다시 읽는다 — 관리자 앱에서 저장하면 공개 서버에는 최대 그만큼 늦게 닿는다. 같은 서버 안에서 바꾸면 {@link #invalidate()}.</li>
 *   <li>다시 읽다 실패하면 이전 사본을 쓰고 5초 뒤 다시 시도한다. 행이 없으면 기본값({@link DealSettings#DEFAULTS}).</li>
 * </ul>
 */
@Component
public class DealSettingsReader {

    private static final Logger log = LoggerFactory.getLogger(DealSettingsReader.class);

    static final int TTL_SECONDS = 15;
    private static final long TTL_NANOS = Duration.ofSeconds(TTL_SECONDS).toNanos();
    private static final long RETRY_NANOS = Duration.ofSeconds(5).toNanos();

    private final DealSettingRepository settings;
    private volatile Cached cached;

    private record Cached(DealSettings settings, long loadedAt) {
    }

    public DealSettingsReader(DealSettingRepository settings) {
        this.settings = settings;
    }

    public DealSettings current() {
        Cached current = cached;
        if (current != null && System.nanoTime() - current.loadedAt < TTL_NANOS) {
            return current.settings;
        }
        synchronized (this) {
            current = cached;
            long now = System.nanoTime();
            if (current != null && now - current.loadedAt < TTL_NANOS) {
                return current.settings;
            }
            try {
                DealSettings loaded = load();
                cached = new Cached(loaded, now);
                return loaded;
            } catch (RuntimeException e) {
                if (current == null) {
                    throw e;
                }
                log.warn("핫딜 설정을 다시 읽지 못해 이전 값을 쓴다 원인={}", e.getClass().getSimpleName());
                cached = new Cached(current.settings, now - TTL_NANOS + RETRY_NANOS);
                return current.settings;
            }
        }
    }

    /** 이 서버에서 설정을 바꿨다 — 다음 읽기에서 바로 다시 읽는다 */
    public void invalidate() {
        cached = null;
    }

    private DealSettings load() {
        return settings.findById(DealSettingEntity.ROW_ID).map(DealSettingEntity::settings).orElse(DealSettings.DEFAULTS);
    }
}
