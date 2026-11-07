package com.community.commerce.guard.service;

import com.community.commerce.guard.dto.RequestAction;
import com.community.commerce.guard.dto.RequestRateLimitRow;
import com.community.commerce.guard.repository.master.RequestRateLimitRepositoryQDSL;
import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 요청 종류별 1분 한도({@code request_rate_limits})의 <b>짧은 메모리 사본</b> — 돈 요청마다 설정 행을 읽지 않게 한다({@code PointPolicy} 와 같은 방식).
 *
 * <ul>
 *   <li>{@value #TTL_SECONDS}초마다 다시 읽는다 — 관리자 앱에서 저장하면 공개 서버에는 <b>최대 그만큼 늦게</b> 닿는다(commerce.md 12-4 「15초 안」). 저장한 서버 안에서는
 *       {@link #invalidate()} 로 바로 비운다.</li>
 *   <li>다시 읽다 실패하면 이전 사본을 쓰고 5초 뒤 다시 시도한다. 행이 없는 종류는 기본값({@link RequestAction#defaultLimit()}), 범위(0~100) 밖 값도 기본값.</li>
 *   <li>읽기는 두 칸 프로젝션({@link RequestRateLimitRepositoryQDSL#all}) 한 문장이라 트랜잭션을 따로 열지 않는다 — 업무 트랜잭션 밖이면 질의 하나가 자기 연결로 끝나고,
 *       안이면 그 트랜잭션에서 읽는다(엔티티를 영속성 컨텍스트에 올리지 않는다).</li>
 * </ul>
 */
@Component
public class RequestRateLimits {

    private static final Logger log = LoggerFactory.getLogger(RequestRateLimits.class);

    static final int TTL_SECONDS = 15;
    private static final long TTL_NANOS = Duration.ofSeconds(TTL_SECONDS).toNanos();
    private static final long RETRY_NANOS = Duration.ofSeconds(5).toNanos();

    private final RequestRateLimitRepositoryQDSL rows;
    private volatile Cached cached;

    public RequestRateLimits(RequestRateLimitRepositoryQDSL rows) {
        this.rows = rows;
    }
    /** 그 요청 종류의 1분 한도(0 = 제한 없음) */
    public int limitOf(RequestAction action) {
        return current().get(action);
    }

    /** 모든 종류의 한도(지금 사본) */
    public Map<RequestAction, Integer> current() {
        Cached current = cached;
        if (current != null && System.nanoTime() - current.loadedAt < TTL_NANOS) {
            return current.limits;
        }
        synchronized (this) {
            current = cached;
            long now = System.nanoTime();
            if (current != null && now - current.loadedAt < TTL_NANOS) {
                return current.limits;
            }
            try {
                Map<RequestAction, Integer> loaded = load();
                cached = new Cached(loaded, now);
                return loaded;
            } catch (RuntimeException e) {
                if (current == null) {
                    throw e;
                }
                log.warn("요청 제한 값을 다시 읽지 못해 이전 값을 쓴다 원인={}", e.getClass().getSimpleName());
                cached = new Cached(current.limits, now - TTL_NANOS + RETRY_NANOS);
                return current.limits;
            }
        }
    }

    /** 사본을 버린다 — 다음 조회가 다시 읽는다 */
    public void invalidate() {
        cached = null;
    }

    private Map<RequestAction, Integer> load() {
        Map<RequestAction, Integer> limits = new EnumMap<>(RequestAction.class);
        for (RequestAction action : RequestAction.values()) {
            limits.put(action, action.defaultLimit());
        }
        for (RequestRateLimitRow row : rows.all()) {
            int value = row.limitPerMinute();
            RequestAction.ofCode(row.action()).ifPresent(action -> {
                if (value >= RequestAction.MIN_LIMIT && value <= RequestAction.MAX_LIMIT) {
                    limits.put(action, value);
                }
            });
        }
        return Map.copyOf(limits);
    }

    private record Cached(Map<RequestAction, Integer> limits, long loadedAt) {
    }
}
