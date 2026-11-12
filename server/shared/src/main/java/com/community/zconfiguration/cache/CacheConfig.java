package com.community.zconfiguration.cache;

import com.community.cache.definition.CacheServerRole;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 캐시 틀(Spring Cache + Caffeine) — docs/features/cache-admin-contract.md. {@code CacheManager} 와 캐시 운영({@link CacheControl})은 같은 구현 하나다.
 * 이 서버 종류(api · admin)는 앱의 {@code irobit.schema.stream} 으로 안다.
 */
@Configuration
@EnableCaching
public class CacheConfig {

    private static final Logger log = LoggerFactory.getLogger(CacheConfig.class);

    @Bean
    public CaffeineCacheControl cacheManager(@Value("${irobit.schema.stream:api}") String stream) {
        return new CaffeineCacheControl(CacheServerRole.ofStream(stream));
    }

    /**
     * 「이전 값 유지」 캐시의 다시 읽기({@link CacheReloader} 빈 — 그 캐시를 읽는 서비스)를 띄운 뒤 한꺼번에 등록한다. 서비스가 {@code CacheManager} 를 받으므로 만들 때 넘기면 순환이 된다.
     * 빠진 것이 있으면 경고만 남긴다 — 그 캐시는 TTL 이 지나면 새로 읽는(만료와 같은) 동작이 된다.
     */
    @Bean
    public SmartInitializingSingleton cacheReloaderRegistration(CacheControl control, ObjectProvider<CacheReloader> reloaders) {
        return () -> {
            reloaders.orderedStream().forEach(control::registerReloader);
            List<String> missing = control.missingReloaders();
            if (!missing.isEmpty()) {
                log.warn("다시 읽기가 없는 이전 값 유지 캐시 — 다시 읽다 실패하면 이전 값을 쓰지 못한다: {}", missing);
            }
        };
    }
}
