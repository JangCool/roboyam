package com.community.zconfiguration.cache;

import java.time.Duration;
import java.util.List;

/**
 * 캐시 운영 — 런타임 TTL 적용 · 켜기/끄기 · 비우기 · 통계 읽기를 <b>이 인터페이스 하나</b>에 가둔다(docs/features/cache-admin-contract.md 「원격 캐시 전환 시 고려할 점」 1).
 * 업무 코드는 Spring Cache 추상({@code CacheManager}·{@code Cache})만 쓰고 이 인터페이스는 동기화({@code CacheSync})·관리 쪽만 부른다.
 * 지금 구현은 서버 메모리(Caffeine — {@link CaffeineCacheControl})이고, 원격 캐시(Redis·Hazelcast)로 바꿀 때는 이 구현과 {@code CacheManager} 만 바꾼다.
 */
public interface CacheControl {

    /** 이 서버가 두는 캐시 이름(카탈로그 차례) */
    List<String> localNames();

    /** 이 서버가 그 캐시를 두는가 */
    boolean holds(String name);

    /** 새 TTL — 이미 담긴 항목에도 넣은 시각 기준으로 적용한다(비우지 않는다) */
    void applyTtl(String name, Duration ttl);

    /** 켜고 끈다 — 끄면 담긴 항목을 비우고, 그 뒤로는 저장하지 않고 요청마다 원본을 읽는다 */
    void applyEnabled(String name, boolean enabled);

    boolean enabled(String name);

    /** 담긴 항목을 모두 비운다(이 서버만) */
    void clear(String name);

    /** 이 서버의 통계 — 항목 수 · 적중 · 누락(띄운 뒤 센 값) */
    Stats stats(String name);

    record Stats(long entryCount, long hitCount, long missCount) {

        public static final Stats EMPTY = new Stats(0, 0, 0);
    }
}
