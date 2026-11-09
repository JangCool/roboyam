package com.community.zconfiguration.cache;

import com.community.cache.definition.CacheCatalog;
import com.community.cache.definition.CacheDefinition;
import com.community.cache.definition.CacheServerRole;
import com.github.benmanes.caffeine.cache.CacheLoader;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Policy;
import com.github.benmanes.caffeine.cache.Ticker;
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
 *   <li>캐시는 카탈로그({@link CacheCatalog})에서 이 서버 종류가 두는 것만 만든다 — 항목 수 상한, 통계 켬.</li>
 *   <li>공개 화면·가젯: 넣은 뒤 TTL 이 지나면 만료({@code expireAfterWrite}) — 다음 읽기가 새로 읽고, 실패하면 그 요청이 실패한다.</li>
 *   <li>설정 사본·내부 규칙({@link CacheDefinition#keepsLastValue()}): TTL 이 지나면 다음 읽기에서 다시 읽는다({@code refreshAfterWrite} + 등록한 {@link CacheReloader}).
 *       다시 읽기는 그 읽는 스레드에서 바로 하고({@code executor(Runnable::run)} — 그 읽기가 새 값을 받는다, 예전 메모리 사본과 같다) 같은 항목을 동시에 읽는 다른 요청은
 *       기다리지 않고 이전 값을 받는다. 다시 읽다 실패하면 Caffeine 이 이전 값을 그대로 두고(경고 기록) 그다음 읽기에서 다시 시도한다. 만료는 없다(이전 값을 계속 쓴다).</li>
 *   <li>꺼진 캐시는 {@link #getCache} 가 {@link NoOpCache} 를 준다 — 저장하지 않고 요청마다 원본을 읽는다(Spring Cache 는 부를 때마다 캐시를 찾는다).
 *       이 서버가 두지 않는 이름(다른 서버 종류의 캐시)도 {@link NoOpCache} 다(원본을 읽는다).</li>
 *   <li>비우기·끄기는 이전 값까지 지운다(그 뒤 첫 읽기가 실패하면 부르는 쪽의 「처음부터 못 읽은 경우」 규칙).</li>
 *   <li>런타임 TTL 은 Caffeine {@code policy().expireAfterWrite()}(이전 값 유지 캐시는 {@code policy().refreshAfterWrite()}) 로 바꾼다.</li>
 * </ul>
 */
public class CaffeineCacheControl implements CacheManager, CacheControl {

    private static final Logger log = LoggerFactory.getLogger(CaffeineCacheControl.class);

    private final Map<String, CaffeineCache> caches = new LinkedHashMap<>();
    private final Set<String> keepsLastValue = ConcurrentHashMap.newKeySet();
    private final Map<String, CacheReloader> reloaders = new ConcurrentHashMap<>();
    private final Set<String> disabled = ConcurrentHashMap.newKeySet();
    private final Map<String, NoOpCache> noOps = new ConcurrentHashMap<>();

    public CaffeineCacheControl(CacheServerRole role) {
        this(role, Ticker.systemTicker());
    }

    /** 시계를 바꿔 끼운다(시험용 — 운영은 {@link Ticker#systemTicker()}) */
    public CaffeineCacheControl(CacheServerRole role, Ticker ticker) {
        for (CacheDefinition d : CacheCatalog.on(role)) {
            Caffeine<Object, Object> builder = Caffeine.newBuilder().maximumSize(d.maximumSize()).recordStats().ticker(ticker);
            com.github.benmanes.caffeine.cache.Cache<Object, Object> nativeCache;
            if (d.keepsLastValue()) {
                keepsLastValue.add(d.key());
                nativeCache = builder.refreshAfterWrite(d.defaultTtl()).executor(Runnable::run).build(new Reloading(d.key()));
            } else {
                nativeCache = builder.expireAfterWrite(d.defaultTtl()).build();
            }
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
        Policy<Object, Object> policy = cache.getNativeCache().policy();
        if (keepsLastValue.contains(name)) {
            policy.refreshAfterWrite().ifPresent(p -> {
                if (!p.getRefreshesAfter().equals(ttl)) {
                    p.setRefreshesAfter(ttl);
                    logTtl(name, ttl);
                }
            });
        } else {
            policy.expireAfterWrite().ifPresent(p -> {
                if (!p.getExpiresAfter().equals(ttl)) {
                    p.setExpiresAfter(ttl);
                    logTtl(name, ttl);
                }
            });
        }
    }

    private static void logTtl(String name, Duration ttl) {
        log.info("캐시 TTL 적용 {} = {}초", name, ttl.toSeconds());
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
    public void registerReloader(CacheReloader reloader) {
        String name = reloader.cacheName();
        if (name == null || !keepsLastValue.contains(name)) { // null = 시험의 가짜 빈
            return;
        }
        CacheReloader was = reloaders.putIfAbsent(name, reloader);
        if (was != null && was != reloader) {
            throw new IllegalStateException("캐시 " + name + " 의 다시 읽기가 둘이다: " + was.getClass().getName() + ", " + reloader.getClass().getName());
        }
    }

    @Override
    public List<String> missingReloaders() {
        return caches.keySet().stream().filter(keepsLastValue::contains).filter(name -> !reloaders.containsKey(name)).toList();
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

    /**
     * 이전 값 유지 캐시의 로더 — 등록한 {@link CacheReloader} 로 읽는다. 처음 담는 것은 보통 부르는 쪽의 로더({@code Cache#get(key, loader)})이고, 이것은 TTL 이 지난 뒤
     * 다시 읽기({@link #reload})와 로더 없이 읽는 {@code Cache#get(key)} 에 쓰인다.
     */
    private final class Reloading implements CacheLoader<Object, Object> {

        private final String name;

        Reloading(String name) {
            this.name = name;
        }

        @Override
        public Object load(Object key) throws Exception {
            CacheReloader reloader = reloaders.get(name);
            if (reloader == null) {
                throw new IllegalStateException("캐시 " + name + " 의 다시 읽기가 등록되지 않았다");
            }
            return reloader.reload(key);
        }

        /**
         * 다시 읽기 — 실패하면 던지고 Caffeine 이 이전 값을 둔다. 등록이 없으면 null 을 돌려 항목을 버린다(다음 읽기가 부르는 쪽 로더로 새로 읽는다 — 만료와 같은 결과).
         */
        @Override
        public Object reload(Object key, Object oldValue) throws Exception {
            CacheReloader reloader = reloaders.get(name);
            if (reloader == null) {
                return null;
            }
            return reloader.reload(key);
        }
    }
}
