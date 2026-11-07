package com.community.hotdeal.repository.master;

import com.community.hotdeal.entity.QDealDomainEntity;
import com.community.hotdeal.entity.QDealSiteAffiliateEntity;
import com.community.hotdeal.service.DealLinkJudge.DomainRule;
import com.community.hotdeal.service.SiteAffiliateLinker.SiteStore;
import com.community.zconfiguration.jpa.RoboyamRepositoryQDSL;
import com.querydsl.core.types.Projections;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.util.Arrays;
import java.util.List;
import org.springframework.stereotype.Repository;

/**
 * 링크 규칙({@code deal_domains})·사이트 제휴 몰({@code deal_site_affiliates}) 전체 읽기 — 공개 쪽 15초 사본({@code DealRuleBook})이 다시 읽을 때. 엔티티를 올리지 않고 판정에 쓰는 칸만 읽는다
 * (트랜잭션 밖에서도 부른다 — 그때는 질의 하나가 자기 연결로 끝난다).
 */
@Repository
public class DealRuleRepositoryQDSL extends RoboyamRepositoryQDSL {

    private static final QDealDomainEntity DOMAIN = QDealDomainEntity.dealDomainEntity;
    private static final QDealSiteAffiliateEntity STORE = QDealSiteAffiliateEntity.dealSiteAffiliateEntity;

    public DealRuleRepositoryQDSL(JPAQueryFactory query) {
        super(query);
    }

    /** 링크 규칙 전부(차례 없음 — 판정이 더 구체적인 규칙을 고른다) */
    public List<DomainRule> domainRules() {
        return query.select(DOMAIN.domain, DOMAIN.rule, DOMAIN.common, DOMAIN.shortener, DOMAIN.keyParams, DOMAIN.storeName).from(DOMAIN).fetch().stream()
                .map(row -> new DomainRule(row.get(DOMAIN.domain), row.get(DOMAIN.rule), Boolean.TRUE.equals(row.get(DOMAIN.common)),
                        Boolean.TRUE.equals(row.get(DOMAIN.shortener)), strings(row.get(DOMAIN.keyParams)), row.get(DOMAIN.storeName)))
                .toList();
    }

    /** 사이트 제휴 몰 전부(꺼진 몰 포함 — 고르는 쪽이 {@code enabled} 를 본다) */
    public List<SiteStore> siteStores() {
        return query.select(Projections.constructor(SiteStore.class, STORE.id, STORE.domain, STORE.programName, STORE.transformType, STORE.template, STORE.query,
                STORE.enabled)).from(STORE).fetch();
    }

    private static List<String> strings(String[] values) {
        return values == null ? List.of() : Arrays.stream(values).map(String::valueOf).toList();
    }
}
