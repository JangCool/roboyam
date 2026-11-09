package com.community.commerce.guard.service;

import com.community.cache.definition.CacheNames;
import org.springframework.cache.CacheManager;
import com.community.commerce.guard.dto.RequestAction;
import com.community.commerce.guard.dto.RequestRateLimitRow;
import com.community.commerce.guard.repository.master.RequestRateLimitRepositoryQDSL;
import java.util.EnumMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 요청 종류별 1분 한도({@code request_rate_limits})의 <b>짧은 메모리 사본</b> — 돈 요청마다 설정 행을 읽지 않게 한다({@code PointPolicy} 와 같은 방식).
 *
 * <ul>
 *   <li>캐시 {@code commerce.rateLimits}(기본 15초, 관리자 허용 5초~5분 — docs/features/cache-admin-contract.md)에 둔다. 관리자 앱에서 저장하면 세대 번호로 모든 서버에
 *       몇 초 안에 닿는다(commerce.md 12-4 「15초 안」). 저장한 서버 안에서는 커밋 뒤 바로 비운다.</li>
 *   <li>읽다 실패하면 담지 않고 그 요청이 실패한다(다음 요청이 다시 읽는다). 행이 없는 종류는 기본값({@link RequestAction#defaultLimit()}), 범위(0~100) 밖 값도 기본값.</li>
 *   <li>읽기는 두 칸 프로젝션({@link RequestRateLimitRepositoryQDSL#all}) 한 문장이라 트랜잭션을 따로 열지 않는다 — 업무 트랜잭션 밖이면 질의 하나가 자기 연결로 끝나고,
 *       안이면 그 트랜잭션에서 읽는다(엔티티를 영속성 컨텍스트에 올리지 않는다).</li>
 * </ul>
 */
@Component
public class RequestRateLimits {


    private final RequestRateLimitRepositoryQDSL rows;
    private final CacheManager caches;

    public RequestRateLimits(RequestRateLimitRepositoryQDSL rows, CacheManager caches) {
        this.caches = caches;
        this.rows = rows;
    }
    /** 그 요청 종류의 1분 한도(0 = 제한 없음) */
    public int limitOf(RequestAction action) {
        return current().get(action);
    }

    /** 모든 종류의 한도(지금 사본 — 캐시 {@code commerce.rateLimits}) */
    public Map<RequestAction, Integer> current() {
        return caches.getCache(CacheNames.COMMERCE_RATE_LIMITS).get(CacheNames.SINGLE, this::load);
    }

    /** 이 서버의 사본을 버린다 — 다음 조회가 다시 읽는다(다른 서버는 관리자 저장의 세대 번호로 따라온다) */
    public void invalidate() {
        caches.getCache(CacheNames.COMMERCE_RATE_LIMITS).clear();
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

}
