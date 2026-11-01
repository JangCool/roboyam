package com.community.hotdeal.dto.response;

import java.util.List;

/**
 * {@code GET /api/deals/link-check}(user-web {@code DealLinkCheck}) — 링크 판정(접미사 일치·더 구체적인 규칙 우선·리디렉션 체인의 모든 호스트·차단 &gt; 제휴) + OG + 같은 딜.
 *
 * @param finalHost     리디렉션을 따라간 최종 호스트(최대 3회) — {@code host} 와 같거나 따라가지 못했으면 null
 * @param rule          BLOCK · AFFILIATE · null
 * @param shortener     단축 주소 차단인가
 * @param storeName     규칙 표로 찾은 쇼핑몰 이름(없으면 null)
 * @param affiliateMode 지금 제휴 링크 방식(MEMBER · SITE)
 * @param siteAffiliate {@code SITE} 이고 켠 사이트 제휴 몰이면 그 프로그램, 아니면 null
 */
public record DealLinkCheckView(String host, String finalHost, String rule, boolean common, boolean shortener, String storeName, DealOgView og,
                                List<DealSameRefView> sameDeals, String affiliateMode, DealSiteAffiliateRefView siteAffiliate) {
}