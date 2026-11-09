package com.community.point;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.community.cache.definition.CacheServerRole;
import com.community.point.dto.PointLotRules;
import com.community.point.dto.PointRules;
import com.community.point.repository.master.PointPolicyQueries;
import com.community.point.service.PointPolicy;
import com.community.testsupport.CommerceTestDatabase;
import com.community.zconfiguration.cache.CaffeineCacheControl;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;

/**
 * 포인트 정책 사본(캐시 {@code point.policy}, 기본 15초)의 「이전 값 유지」 — 실제 PostgreSQL 의 정책 행을 읽고, 읽기 실패만 흉내 낸다(DB 값은 바꾸지 않는다).
 * TTL 이 지나 다시 읽다 실패하면 이전 정책으로 지급 판정을 계속하고(예전 메모리 사본과 같다), 비운 뒤(관리자 저장 → 세대 올리기) 첫 읽기 실패는 그 요청이 실패한다.
 */
class PointPolicyCacheTest {

    @Test
    void TTL_이_지나_다시_읽다_실패하면_이전_정책을_쓰고_비운_뒤_첫_읽기_실패는_그_요청이_실패한다() {
        AtomicBoolean down = new AtomicBoolean();
        AtomicBoolean empty = new AtomicBoolean();
        PointPolicyQueries queries = new PointPolicyQueries(CommerceTestDatabase.migrated()) {
            @Override
            public Optional<PolicyRow> load() {
                if (down.get()) {
                    throw new IllegalStateException("DB 읽기 실패(시험)");
                }
                return empty.get() ? Optional.empty() : super.load();
            }
        };
        AtomicLong nanos = new AtomicLong(1_000_000_000L);
        CaffeineCacheControl caches = new CaffeineCacheControl(CacheServerRole.API, nanos::get);
        PointPolicy policy = new PointPolicy(queries, caches);
        caches.registerReloader(policy);

        PointRules stored = policy.current();
        assertThat(stored).as("DB 행에서 읽은 정책(기본값 상수가 아님)").isNotSameAs(PointRules.DEFAULTS);
        PointLotRules storedLots = policy.lots();

        nanos.addAndGet(Duration.ofSeconds(16).toNanos());
        down.set(true);
        assertThat(policy.current()).as("다시 읽기 실패 — 이전 정책").isSameAs(stored);
        assertThat(policy.lots()).isSameAs(storedLots);

        down.set(false);
        empty.set(true); // 다시 읽으면 기본값이 되는 상태(행 없음)로 바꿔 새로 읽었는지 가린다
        assertThat(policy.current()).as("살아나면 다음 읽기가 새로 읽는다").isSameAs(PointRules.DEFAULTS);

        policy.invalidate();
        down.set(true);
        assertThatThrownBy(policy::current).as("비운 뒤 첫 읽기 실패 — 이전 정책은 지워졌다").isInstanceOf(Cache.ValueRetrievalException.class);
    }
}
