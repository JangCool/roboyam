package com.community.cache.definition;

import java.time.Duration;
import java.util.List;

/**
 * 캐시 하나의 정의 — 코드가 원천이다(cache-admin-contract). 이름·대상·쓰이는 곳의 글은 메시지 번들에 있다:
 * {@code cache.def.<키>.name} · {@code cache.def.<키>.target} · {@code cache.def.<키>.usage.<차례>.label}/{@code .desc} · {@code cache.link.<링크 ID>}.
 *
 * @param key               캐시 키(= Spring Cache 이름, 공개 화면 {@code data-cache-area} 값)
 * @param kind              분류 — 허용 TTL 범위를 정한다
 * @param servers           이 캐시를 두는 서버 종류(차례: API → ADMIN)
 * @param defaultTtlSeconds 기본 TTL(초) — DB 에 값이 없으면 이것
 * @param memberMode        회원별 처리
 * @param targetIsPath      대상 글이 API 경로인가(화면이 고정폭 글자로 보인다)
 * @param gadgetKind        가젯 데이터 캐시면 그 가젯 종류, 아니면 null — 가젯은 쓰이는 곳을 배치에서 센다
 * @param usages            쓰이는 곳(가젯이면 빈 목록 — 배치에서 만든다)
 * @param adminLinks        관련 관리 화면
 * @param maximumSize       서버 한 대가 담는 항목 수 상한(넘으면 오래 안 쓴 것부터 버린다)
 */
public record CacheDefinition(String key, CacheKind kind, List<CacheServerRole> servers, int defaultTtlSeconds, CacheMemberMode memberMode, boolean targetIsPath,
                              String gadgetKind, List<Usage> usages, List<AdminLink> adminLinks, long maximumSize) {

    public CacheDefinition {
        servers = List.copyOf(servers);
        usages = List.copyOf(usages);
        adminLinks = List.copyOf(adminLinks);
    }

    public Duration defaultTtl() {
        return Duration.ofSeconds(defaultTtlSeconds);
    }

    /**
     * 「이전 값 유지」 캐시인가 — 설정 사본·내부 규칙(옛 메모리 사본 12개 + 오픈소스 라이선스 고지). TTL 이 지나 다시 읽다 실패하면 이전 값을 쓴다({@code zconfiguration.cache.CacheReloader}).
     * 공개 화면·가젯은 아니다(항목 키가 설정 지문이라 키만으로 다시 읽을 수 없다 — 만료되면 다음 읽기가 새로 읽고, 실패하면 그 요청이 실패한다).
     */
    public boolean keepsLastValue() {
        return kind == CacheKind.SETTINGS_COPY || kind == CacheKind.INTERNAL_RULE;
    }

    public boolean on(CacheServerRole role) {
        return servers.contains(role);
    }

    public String nameKey() {
        return "cache.def." + key + ".name";
    }

    public String targetKey() {
        return "cache.def." + key + ".target";
    }

    public String usageLabelKey(int index) {
        return "cache.def." + key + ".usage." + index + ".label";
    }

    public String usageDescKey(int index) {
        return "cache.def." + key + ".usage." + index + ".desc";
    }

    /**
     * 쓰이는 곳 한 줄(고정 정보).
     *
     * @param path          공개 사이트 경로(쿼리 포함 가능) — 서버 내부이거나 예시 경로 규칙을 쓰면 null
     * @param sampleKind    글·상품 하나를 열어야 보이는 곳의 예시 종류(아니면 null)
     * @param template      예시 경로 틀({@code {id}} 자리에 예시 번호, 아니면 null)
     * @param loginRequired 로그인한 회원에게만 보이는 쪽인가
     */
    public record Usage(CacheUsageKind kind, String path, CacheSampleKind sampleKind, String template, boolean loginRequired) {
    }

    /** 관련 관리 화면 — 이름은 {@code cache.link.<id>} */
    public record AdminLink(String id, String path) {

        public String labelKey() {
            return "cache.link." + id;
        }
    }
}
