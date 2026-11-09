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

    /** 새 TTL — 이미 담긴 항목에도 넣은(다시 읽은) 시각 기준으로 적용한다(비우지 않는다). 이전 값 유지 캐시는 다시 읽는 주기다 */
    void applyTtl(String name, Duration ttl);

    /** 켜고 끈다 — 끄면 담긴 항목을 비우고, 그 뒤로는 저장하지 않고 요청마다 원본을 읽는다 */
    void applyEnabled(String name, boolean enabled);

    boolean enabled(String name);

    /** 담긴 항목을 모두 비운다(이 서버만) */
    void clear(String name);

    /**
     * 「이전 값 유지」 캐시의 다시 읽기를 등록한다({@link CacheReloader}) — 이 서버가 두지 않거나 이전 값 유지가 아닌 캐시면 무시한다.
     * 이전 값 유지 캐시는 TTL 이 지난 항목을 다음 읽기에서 다시 읽고(그 읽기는 새 값을 받는다), 다시 읽다 실패하면 이전 값을 쓰고 그다음 읽기에서 다시 시도한다.
     * 비우기·끄기는 이전 값까지 지운다. 등록이 없으면 TTL 이 지난 항목은 한 번 이전 값을 준 뒤 버린다(그다음 읽기가 새로 읽는다).
     */
    void registerReloader(CacheReloader reloader);

    /** 이전 값 유지 캐시인데 이 서버에 다시 읽기가 등록되지 않은 것(띄운 뒤 확인용) */
    List<String> missingReloaders();

    /** 이 서버의 통계 — 항목 수 · 적중 · 누락(띄운 뒤 센 값) */
    Stats stats(String name);

    record Stats(long entryCount, long hitCount, long missCount) {

        public static final Stats EMPTY = new Stats(0, 0, 0);
    }
}
