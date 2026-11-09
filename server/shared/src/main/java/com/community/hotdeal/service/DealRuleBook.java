package com.community.hotdeal.service;

import com.community.cache.definition.CacheNames;
import org.springframework.cache.CacheManager;
import com.community.hotdeal.repository.master.DealRuleRepositoryQDSL;
import com.community.hotdeal.service.DealLinkJudge.ChainVerdict;
import com.community.hotdeal.service.DealLinkJudge.DomainRule;
import com.community.hotdeal.service.SiteAffiliateLinker.SiteStore;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * 링크 규칙({@code deal_domains})·사이트 제휴 몰({@code deal_site_affiliates})의 <b>짧은 메모리 사본</b> — 판정({@link DealLinkJudge})·변환({@link SiteAffiliateLinker})이
 * 글 줄마다 표를 읽지 않게 한다. 캐시 {@code hotdeal.rules}(기본 15초)에 두고, 관리자 저장은 세대 번호로 몇 초 안에
 * 모든 서버에 닿는다. 읽다 실패하면 담지 않고 그 요청이 실패한다. 같은 서버 안에서는 {@link #invalidate()}.
 */
@Component
public class DealRuleBook {


    private final DealRuleRepositoryQDSL rules;
    private final CacheManager caches;

    /** 사본 */
    public record Snapshot(List<DomainRule> domains, List<SiteStore> siteStores) {
    }

    public DealRuleBook(DealRuleRepositoryQDSL rules, CacheManager caches) {
        this.caches = caches;
        this.rules = rules;
    }

    /** 지금 사본 — 캐시 {@code hotdeal.rules} */
    public Snapshot snapshot() {
        return caches.getCache(CacheNames.HOTDEAL_RULES).get(CacheNames.SINGLE, () -> new Snapshot(List.copyOf(loadDomains()), List.copyOf(loadStores())));
    }

    /** 이 서버의 사본을 버린다(다른 서버는 관리자 저장의 세대 번호로 따라온다) */
    public void invalidate() {
        caches.getCache(CacheNames.HOTDEAL_RULES).clear();
    }

    /** 지금 규칙으로 체인 판정 */
    public ChainVerdict judge(List<String> hosts) {
        return DealLinkJudge.judgeChain(hosts, snapshot().domains());
    }

    /** 지금 켠 사이트 제휴 몰 가운데 이 링크가 가는 몰 */
    public Optional<SiteStore> siteStore(String productHost, String finalHost) {
        return SiteAffiliateLinker.match(snapshot().siteStores(), productHost, finalHost);
    }

    private List<DomainRule> loadDomains() {
        return rules.domainRules();
    }

    private List<SiteStore> loadStores() {
        return rules.siteStores();
    }
}
