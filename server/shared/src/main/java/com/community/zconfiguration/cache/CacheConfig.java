package com.community.zconfiguration.cache;

import com.community.cache.definition.CacheServerRole;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 캐시 틀(Spring Cache + Caffeine) — docs/features/cache-admin-contract.md. {@code CacheManager} 와 캐시 운영({@link CacheControl})은 같은 구현 하나다.
 * 이 서버 종류(api · admin)는 앱의 {@code roboyam.schema.stream} 으로 안다.
 */
@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public CaffeineCacheControl cacheManager(@Value("${roboyam.schema.stream:api}") String stream) {
        return new CaffeineCacheControl(CacheServerRole.ofStream(stream));
    }
}
