package com.community.hotdeal;

import static org.assertj.core.api.Assertions.assertThat;

import com.community.hotdeal.service.DealHosts;
import com.community.report.entity.ReportReasons;
import org.junit.jupiter.api.Test;

/** 관리자 규칙 도메인 정규화(링크 규칙·사이트 제휴 몰 — admin-web {@code UtilHotdeal.normalizeDomain} 과 같은 규칙)와 신고 사건 사유 체계(핫딜 글 사건만 핫딜 사유) */
class DealHostsDomainTest {

    @Test
    void 스킴_www_포트_경로_자격증명을_떼고_소문자() {
        assertThat(DealHosts.normalizeDomain("HTTPS://WWW.Shop.Example.COM:8080/a/b?x=1#f")).isEqualTo("shop.example.com");
        assertThat(DealHosts.normalizeDomain("  shop.example.com.  ")).isEqualTo("shop.example.com");
        assertThat(DealHosts.normalizeDomain("//user@link.example.com/x")).isEqualTo("link.example.com");
        assertThat(DealHosts.normalizeDomain("11st.co.kr")).isEqualTo("11st.co.kr");
        assertThat(DealHosts.normalizeDomain("쇼핑.한국")).startsWith("xn--"); // 국제화 도메인은 ASCII
    }

    @Test
    void 형식이_아니면_null() {
        for (String bad : new String[] {null, "", "   ", "localhost", "shop", "-shop.example.com", "shop_.example.com", "sh op.example.com", "a..b.com",
                "a".repeat(250) + ".com"}) {
            assertThat(DealHosts.normalizeDomain(bad)).as(String.valueOf(bad)).isNull();
        }
    }

    @Test
    void 핫딜_사유는_핫딜_글_사건에서만() {
        assertThat(ReportReasons.forCase("POST", true)).contains(ReportReasons.DEAL_FALSE_PRICE, ReportReasons.DEAL_HIDDEN_AD);
        assertThat(ReportReasons.forCase("POST", false)).doesNotContain(ReportReasons.DEAL_FALSE_PRICE);
        assertThat(ReportReasons.forCase("COMMENT", true)).doesNotContain(ReportReasons.DEAL_HIDDEN_AD);
        assertThat(ReportReasons.forCase("GROUP", true)).isEqualTo(ReportReasons.GROUP_ALL);
        assertThat(ReportReasons.EVERY.indexOf(ReportReasons.DEAL_HIDDEN_AD)).isLessThan(ReportReasons.EVERY.indexOf(ReportReasons.OTHER));
    }
}
