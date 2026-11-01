package com.community.hotdeal.service;

import com.community.hotdeal.entity.DealDomainEntity;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * 링크 판정(서버 규칙 2 — 순수 함수). 규칙 표({@code deal_domains})를 받아 호스트·리디렉션 체인을 판정한다.
 *
 * <ul>
 *   <li><b>접미사 일치</b>: 규칙 {@code example.com} 은 {@code example.com}·{@code a.example.com} 에 걸린다({@link DealHosts#matches}, 점 경계).</li>
 *   <li><b>더 구체적인 규칙 우선</b>: 한 호스트에 여러 규칙이 걸리면 도메인이 긴(더 구체적인) 쪽이 이긴다 — 규칙(차단·제휴)은 규칙이 있는 줄 중 가장 구체적인 줄,
 *       이름·{@code keyParams} 는 그 칸이 있는 줄 중 가장 구체적인 줄, 대형몰은 걸리는 줄 중 하나라도 대형몰이면 대형몰. 규칙 없이 이름만 둔 하위 도메인 줄이 상위 도메인의 차단을 풀지는 않는다(보수적으로 둔 가정).</li>
 *   <li><b>체인의 모든 호스트</b>: 리디렉션을 따라간 모든 호스트(처음 주소 포함)에 규칙을 적용하고 <b>차단 &gt; 제휴</b> — 하나라도 차단이면 차단, 아니고 하나라도 제휴면 제휴.</li>
 *   <li>대형몰·쇼핑몰 이름은 최종 호스트의 줄을 먼저 보고, 없으면 처음 호스트의 줄.</li>
 * </ul>
 */
public final class DealLinkJudge {

    private DealLinkJudge() {
    }

    /** 규칙 한 줄(사본) */
    public record DomainRule(String domain, String rule, boolean common, boolean shortener, List<String> keyParams, String storeName) {

        public DomainRule {
            keyParams = keyParams == null ? List.of() : List.copyOf(keyParams);
        }
    }

    /** 호스트 하나의 판정 */
    public record HostVerdict(String host, String rule, boolean shortener, boolean common, String storeName, List<String> keyParams) {
    }

    /**
     * 체인 판정.
     *
     * @param hosts       판정한 호스트들(처음 → 최종, 중복 없음)
     * @param host        처음 호스트
     * @param finalHost   최종 호스트 — 처음 호스트와 같거나 모르면 null
     * @param rule        차단 &gt; 제휴 &gt; null
     * @param blockedHost 차단에 걸린 첫 호스트(차단이 아니면 null)
     * @param shortener   차단에 걸린 줄이 단축 주소 줄인가
     * @param keyParams   처음 호스트의 {@code keyParams}(같은 딜 키 — 저장한 상품 링크로 만든다)
     */
    public record ChainVerdict(List<String> hosts, String host, String finalHost, String rule, String blockedHost, boolean shortener, boolean common,
                               String storeName, List<String> keyParams) {

        public boolean blocked() {
            return DealDomainEntity.BLOCK.equals(rule);
        }

        public boolean affiliate() {
            return DealDomainEntity.AFFILIATE.equals(rule);
        }
    }

    /** 호스트 하나 — 걸리는 줄 가운데 칸마다 가장 구체적인 줄 */
    public static HostVerdict judgeHost(String host, Collection<DomainRule> rules) {
        List<DomainRule> matched = new ArrayList<>();
        for (DomainRule r : rules) {
            if (DealHosts.matches(host, r.domain())) {
                matched.add(r);
            }
        }
        matched.sort(Comparator.comparingInt((DomainRule r) -> r.domain().length()).reversed());
        DomainRule ruled = matched.stream().filter(r -> r.rule() != null).findFirst().orElse(null);
        DomainRule named = matched.stream().filter(r -> r.storeName() != null && !r.storeName().isBlank()).findFirst().orElse(null);
        DomainRule keyed = matched.stream().filter(r -> !r.keyParams().isEmpty()).findFirst().orElse(null);
        boolean common = matched.stream().anyMatch(DomainRule::common); // 대형몰의 하위 도메인(제휴 단축 포함)도 대형몰
        return new HostVerdict(host, ruled == null ? null : ruled.rule(), ruled != null && ruled.shortener(), common,
                named == null ? null : named.storeName(), keyed == null ? List.of() : keyed.keyParams());
    }

    /**
     * 체인 판정 — 차단 &gt; 제휴.
     *
     * @param chain 처음 호스트부터 차례(정규화한 호스트, null·중복은 뺀다). 비어 있으면 안 된다
     */
    public static ChainVerdict judgeChain(List<String> chain, Collection<DomainRule> rules) {
        List<String> hosts = chain.stream().filter(Objects::nonNull).distinct().toList();
        if (hosts.isEmpty()) {
            throw new IllegalArgumentException("호스트가 없다");
        }
        List<HostVerdict> verdicts = hosts.stream().map(h -> judgeHost(h, rules)).toList();
        HostVerdict first = verdicts.get(0);
        HostVerdict last = verdicts.get(verdicts.size() - 1);
        HostVerdict blocked = verdicts.stream().filter(v -> DealDomainEntity.BLOCK.equals(v.rule())).findFirst().orElse(null);
        boolean affiliate = verdicts.stream().anyMatch(v -> DealDomainEntity.AFFILIATE.equals(v.rule()));
        String rule = blocked != null ? DealDomainEntity.BLOCK : affiliate ? DealDomainEntity.AFFILIATE : null;
        String storeName = last.storeName() != null ? last.storeName() : first.storeName();
        String finalHost = last.host().equals(first.host()) ? null : last.host();
        return new ChainVerdict(hosts, first.host(), finalHost, rule, blocked == null ? null : blocked.host(), blocked != null && blocked.shortener(),
                last.common(), storeName, first.keyParams());
    }
}
