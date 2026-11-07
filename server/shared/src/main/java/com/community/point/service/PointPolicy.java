package com.community.point.service;

import com.community.point.dto.PointLotRules;
import com.community.point.dto.PointRules;
import com.community.point.repository.master.PointPolicyQueries;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 포인트 정책의 <b>짧은 메모리 사본</b> — 글·댓글·추천마다 정책 행을 읽지 않게 한다(게시판 사본 {@code BoardCatalog} 와 같은 방식).
 *
 * <ul>
 *   <li>{@value #TTL_SECONDS}초마다 다시 읽는다 — 관리자 앱에서 저장하면 공개 서버(다른 프로세스)에는 <b>최대 그만큼 늦게</b> 닿는다(계약의 「15초 안에 모든 서버」).
 *       저장한 서버 안에서는 {@link #invalidate()} 로 바로 비운다.</li>
 *   <li>다시 읽다 실패하면 이전 사본을 쓰고 5초 뒤 다시 시도한다. 행이 없으면 기본값({@link PointRules#DEFAULTS}·{@link PointLotRules#DEFAULTS}).</li>
 *   <li>규칙은 앞으로의 지급에만 적용된다(이미 지급한 포인트는 그대로, 소급 없음). 묶음 유효기간도 새로 만드는 묶음에만 적용된다.</li>
 * </ul>
 */
@Component
public class PointPolicy {

    private static final Logger log = LoggerFactory.getLogger(PointPolicy.class);

    static final int TTL_SECONDS = 15;
    private static final long TTL_NANOS = Duration.ofSeconds(TTL_SECONDS).toNanos();
    private static final long RETRY_NANOS = Duration.ofSeconds(5).toNanos();

    private final PointPolicyQueries queries;
    private volatile Cached cached;

    public PointPolicy(PointPolicyQueries queries) {
        this.queries = queries;
    }

    /** 관리자 정책 화면의 지급·조건 칸(1-3 적립 조건 포함) */
    public PointRules current() {
        return snapshot().rules;
    }

    /** 묶음 정책(유효기간·소멸 알림·전환 시각·큰 금액 조정 문턱) */
    public PointLotRules lots() {
        return snapshot().lots;
    }

    private Cached snapshot() {
        Cached current = cached;
        if (current != null && System.nanoTime() - current.loadedAt < TTL_NANOS) {
            return current;
        }
        synchronized (this) {
            current = cached;
            long now = System.nanoTime();
            if (current != null && now - current.loadedAt < TTL_NANOS) {
                return current;
            }
            try {
                Cached loaded = load(now);
                cached = loaded;
                return loaded;
            } catch (RuntimeException e) {
                if (current == null) {
                    throw e;
                }
                log.warn("포인트 정책을 다시 읽지 못해 이전 값을 쓴다 원인={}", e.getClass().getSimpleName());
                cached = new Cached(current.rules, current.lots, now - TTL_NANOS + RETRY_NANOS);
                return cached;
            }
        }
    }

    /** 정책 행을 읽는다(JDBC — 세이브포인트 안에서 읽혀서다, {@link PointPolicyQueries}). 행이 없으면 기본값 */
    private Cached load(long loadedAt) {
        return queries.load().map(row -> new Cached(row.rules(), row.lots(), loadedAt))
                .orElseGet(() -> new Cached(PointRules.DEFAULTS, PointLotRules.DEFAULTS, loadedAt));
    }

    /** 사본을 버린다 — 다음 {@link #current()} 가 다시 읽는다 */
    public void invalidate() {
        cached = null;
    }

    private record Cached(PointRules rules, PointLotRules lots, long loadedAt) {
    }
}
