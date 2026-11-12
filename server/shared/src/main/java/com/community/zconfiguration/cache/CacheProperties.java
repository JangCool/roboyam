package com.community.zconfiguration.cache;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * irobit.cache.* — 캐시 동기화 수치(docs/features/cache-admin-contract.md 「정책」). 배포 설정이고 관리자 화면에서는 읽기만 한다.
 *
 * @param nodeName           이 서버의 이름(노드 ID) — 비우면 {@code <서버 종류>-<NODE_ID>}(예 {@code api-3}). NODE_ID 는 서버마다 다르다(Snowflake)
 * @param pollSeconds        DB 설정(세대 번호·TTL·켜짐)을 읽어 따라가는 주기(초)
 * @param reportSeconds      이 서버의 상태(반영 세대·항목 수·적중·누락)를 DB 에 보고하는 주기(초) — 바뀐 것을 반영하면 곧바로도 보고한다
 * @param nodeTimeoutSeconds 이만큼 보고가 없는 서버는 관리자 화면 목록에서 뺀다(초)
 */
@ConfigurationProperties(prefix = "irobit.cache")
public record CacheProperties(String nodeName, Integer pollSeconds, Integer reportSeconds, Integer nodeTimeoutSeconds) {

    public static final int DEFAULT_POLL_SECONDS = 3;

    public int poll() {
        return pollSeconds == null || pollSeconds < 1 ? DEFAULT_POLL_SECONDS : pollSeconds;
    }

    public Duration report() {
        return Duration.ofSeconds(reportSeconds == null || reportSeconds < 1 ? 10 : reportSeconds);
    }

    public Duration nodeTimeout() {
        return Duration.ofSeconds(nodeTimeoutSeconds == null || nodeTimeoutSeconds < 10 ? 60 : nodeTimeoutSeconds);
    }
}
