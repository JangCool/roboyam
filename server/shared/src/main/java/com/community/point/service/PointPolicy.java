package com.community.point.service;

import com.community.cache.definition.CacheNames;
import org.springframework.cache.CacheManager;
import com.community.point.dto.PointLotRules;
import com.community.point.dto.PointRules;
import com.community.point.repository.master.PointPolicyQueries;
import org.springframework.stereotype.Component;

/**
 * 포인트 정책의 <b>짧은 메모리 사본</b> — 글·댓글·추천마다 정책 행을 읽지 않게 한다(게시판 사본 {@code BoardCatalog} 와 같은 방식).
 *
 * <ul>
 *   <li>캐시 {@code point.policy}(기본 15초, 관리자 허용 5초~5분 — docs/features/cache-admin-contract.md)에 둔다. 관리자 앱에서 저장하면 세대 번호로 모든 서버에
 *       몇 초 안에 닿는다(계약의 「15초 안에 모든 서버」). 저장한 서버는 커밋 뒤 바로 비운다.</li>
 *   <li>읽다 실패하면 담지 않고 그 요청이 실패한다(다음 요청이 다시 읽는다). 행이 없으면 기본값({@link PointRules#DEFAULTS}·{@link PointLotRules#DEFAULTS}).</li>
 *   <li>규칙은 앞으로의 지급에만 적용된다(이미 지급한 포인트는 그대로, 소급 없음). 묶음 유효기간도 새로 만드는 묶음에만 적용된다.</li>
 * </ul>
 */
@Component
public class PointPolicy {


    private final PointPolicyQueries queries;
    private final CacheManager caches;

    public PointPolicy(PointPolicyQueries queries, CacheManager caches) {
        this.caches = caches;
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

    /** 지금 사본 — 캐시 {@code point.policy} */
    private Loaded snapshot() {
        return caches.getCache(CacheNames.POINT_POLICY).get(CacheNames.SINGLE, this::load);
    }

    /** 정책 행을 읽는다(JDBC — 세이브포인트 안에서 읽혀서다, {@link PointPolicyQueries}). 행이 없으면 기본값 */
    private Loaded load() {
        return queries.load().map(row -> new Loaded(row.rules(), row.lots())).orElseGet(() -> new Loaded(PointRules.DEFAULTS, PointLotRules.DEFAULTS));
    }

    /** 이 서버의 사본을 버린다 — 다음 {@link #current()} 가 다시 읽는다(다른 서버는 관리자 저장의 세대 번호로 따라온다) */
    public void invalidate() {
        caches.getCache(CacheNames.POINT_POLICY).clear();
    }

    /** 캐시에 담는 값 */
    private record Loaded(PointRules rules, PointLotRules lots) {
    }
}
