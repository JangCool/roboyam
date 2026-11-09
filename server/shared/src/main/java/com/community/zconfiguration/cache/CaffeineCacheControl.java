package com.community.zconfiguration.cache;

import com.community.cache.definition.CacheCatalog;
import com.community.cache.definition.CacheDefinition;
import com.community.cache.definition.CacheServerRole;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.stats.CacheStats;
import java.time.Duration;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.support.NoOpCache;

/**
 * 서버 메모리 캐시(Caffeine) 구현 — {@link CacheManager}(업무 코드가 쓰는 Spring Cache 추상)와 {@link CacheControl}(운영) 둘 다 여기 있다. Caffeine API 는 이 클래스에서만 부른다.
 *
 * <ul>
 *   <li>캐시는 카탈로그({@link CacheCatalog})에서 이 서버 종류가 두는 것만 만든다 — 항목마다 넣은 뒤 TTL 이 지나면 만료({@code expireAfterWrite}), 항목 수 상한, 통계 켬.</li>
 *   <li>꺼진 캐시는 {@link #getCache} 가 {@link NoOpCache} 를 준다 — 저장하지 않고 요청마다 원본을 읽는다(Spring Cache 는 부를 때마다 캐시를 찾는다).
 *       이 서버가 두지 않는 이름(다른 서버 종류의 캐시)도 {@link NoOpCache} 다(원본을 읽는다).</li>
 *   <li>런타임 TTL 은 Caffeine {@code policy().expireAfterWrite()} 로 바꾼다.</li>
 * </ul>
 */
public class CaffeineCacheControl implements CacheManager, CacheControl {

    private static final Logger log = LoggerFactory.getLogger(CaffeineCacheControl.class);

    private final Map<String, CaffeineCache> caches = new LinkedHashMap<>();
    private final Set<String> disabled = ConcurrentHashMap.newKeySet();
    private final Map<String, NoOpCache> noOps = new ConcurrentHashMap<>();

    public CaffeineCacheControl(CacheServerRole role) {
        for (CacheDefinition d : CacheCatalog.on(role)) {
            com.github.benmanes.caffeine.cache.Cache<Object, Object> nativeCache = Caffeine.newBuilder()
                    .expireAfterWrite(d.defaultTtl())
                    .maximumSize(d.maximumSize())
                    .recordStats()
                    .build();
            caches.put(d.key(), new CaffeineCache(d.key(), nativeCache, true));
        }
    }

    // ── CacheManager ─────────────────────────────────────────────────────────

    @Override
    public Cache getCache(String name) {
        CaffeineCache cache = caches.get(name);
        if (cache == null || disabled.contains(name)) {
            return noOps.computeIfAbsent(name, NoOpCache::new);
        }
        return cache;
    }

    @Override
    public Collection<String> getCacheNames() {
        return List.copyOf(caches.keySet());
    }

    // ── CacheControl ─────────────────────────────────────────────────────────

    @Override
    public List<String> localNames() {
        return List.copyOf(caches.keySet());
    }

    @Override
    public boolean holds(String name) {
        return caches.containsKey(name);
    }

    @Override
    public void applyTtl(String name, Duration ttl) {
        CaffeineCache cache = caches.get(name);
        if (cache == null) {
            return;
        }
        cache.getNativeCache().policy().expireAfterWrite().ifPresent(policy -> {
            if (!policy.getExpiresAfter().equals(ttl)) {
                policy.setExpiresAfter(ttl);
                log.info("캐시 TTL 적용 {} = {}초", name, ttl.toSeconds());
            }
        });
    }

    @Override
    public void applyEnabled(String name, boolean enabled) {
        CaffeineCache cache = caches.get(name);
        if (cache == null) {
            return;
        }
        if (enabled) {
            disabled.remove(name);
        } else if (disabled.add(name)) {
            cache.clear();
        }
    }

    @Override
    public boolean enabled(String name) {
        return caches.containsKey(name) && !disabled.contains(name);
    }

    @Override
    public void clear(String name) {
        CaffeineCache cache = caches.get(name);
        if (cache != null) {
            cache.clear();
        }
    }

    @Override
    public Stats stats(String name) {
        CaffeineCache cache = caches.get(name);
        if (cache == null) {
            return Stats.EMPTY;
        }
        com.github.benmanes.caffeine.cache.Cache<Object, Object> nativeCache = cache.getNativeCache();
        nativeCache.cleanUp(); // 만료된 항목을 치우고 센다
        CacheStats s = nativeCache.stats();
        return new Stats(nativeCache.estimatedSize(), s.hitCount(), s.missCount());
    }
}
