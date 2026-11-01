package com.community.hotdeal;

import static org.assertj.core.api.Assertions.assertThat;

import com.community.hotdeal.dto.response.DealLinkCheckView;
import com.community.hotdeal.service.DealLinkCheckLimiter;
import com.community.hotdeal.service.DealLinkService;
import com.community.zconfiguration.exception.CommonException;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * {@code GET /api/deals/link-check} — 체인 판정(최종 호스트·차단 &gt; 제휴·단축 주소) · 대형몰·쇼핑몰 이름 · OG(가짜 가져오기 — 그림 없음 NONE · 꺼짐 OFF) · 같은 딜(공개·진행 중) ·
 * 지금 제휴 방식과 사이트 제휴 몰 · 쓰기 자격 · 회원 분당 10회(429).
 */
class HotdealLinkCheckTest extends HotdealApiTestBase {

    @Autowired
    private DealLinkService links;

    @Test
    void 판정_OG_같은_딜_제휴_방식을_한_번에() {
        String link = chain("https://coupa.ng/" + uniq(), "coupa.ng", "coupang.com");
        long other = writeId(member(), deal(link + "?utm_source=a"));
        DealLinkCheckView v = links.linkCheck(link, board, member());
        assertThat(v.host()).isEqualTo("coupa.ng");
        assertThat(v.finalHost()).isEqualTo("coupang.com");
        assertThat(v.rule()).isEqualTo("AFFILIATE");
        assertThat(v.common()).isTrue();
        assertThat(v.shortener()).isFalse();
        assertThat(v.storeName()).isEqualTo("쿠팡");
        assertThat(v.og().status()).isEqualTo("NONE");
        assertThat(v.og().title()).isEqualTo("가짜 제목");
        assertThat(v.sameDeals()).extracting("postId").containsExactly(Long.toString(other));
        assertThat(v.affiliateMode()).isEqualTo("MEMBER");
        assertThat(v.siteAffiliate()).isNull();
    }

    @Test
    void 단축_주소_차단과_SITE_의_사이트_제휴_몰() {
        DealLinkCheckView shortLink = links.linkCheck("https://bit.ly/" + uniq(), board, member());
        assertThat(shortLink.rule()).isEqualTo("BLOCK");
        assertThat(shortLink.shortener()).isTrue();

        String mall = "lc" + uniq() + ".example";
        siteStore(mall, "QUERY", null, "aff=r");
        setting("affiliate_mode", "SITE");
        setting("og_fetch_enabled", false);
        DealLinkCheckView site = links.linkCheck(url(mall), board, member());
        assertThat(site.affiliateMode()).isEqualTo("SITE");
        assertThat(site.siteAffiliate().programName()).isEqualTo("시험 제휴");
        assertThat(site.og().status()).isEqualTo("OFF");
    }

    @Test
    void 쓰기_자격이_없거나_핫딜_게시판이_아니면_판정하지_않는다() {
        assertThat(error(() -> links.linkCheck(url("shop.example.com"), board, member("SEEDLING", true))).getErrorCode()).isEqualTo("GRADE_TOO_LOW");
        assertThat(error(() -> links.linkCheck(url("shop.example.com"), generalBoard(), member())).getFields()).containsKey("board");
        assertThat(error(() -> links.linkCheck("javascript:alert(1)", board, member())).getFields()).containsKey("url");
        assertThat(error(() -> links.linkCheck(url("shop.example.com"), board, null)).getErrorCode()).isEqualTo("UNAUTHENTICATED");
    }

    @Test
    void 회원_분당_10회() {
        long m = member();
        for (int i = 0; i < DealLinkCheckLimiter.PER_MINUTE; i++) {
            links.linkCheck(url("shop.example.com"), board, m);
        }
        CommonException e = error(() -> links.linkCheck(url("shop.example.com"), board, m));
        assertThat(e.getErrorCode()).isEqualTo("DEAL_LINK_CHECK_RATE_LIMIT");
        assertThat(e.getStatus().value()).isEqualTo(429);
        assertThat(e.getRetryAfterSeconds()).isPositive();
        links.linkCheck(url("shop.example.com"), board, member()); // 다른 회원은 따로 센다
    }

    @Test
    void 한도는_1분_창이다() {
        DealLinkCheckLimiter limiter = new DealLinkCheckLimiter();
        Instant t = Instant.parse("2026-11-01T00:00:00Z");
        for (int i = 0; i < 10; i++) {
            limiter.acquire(7L, t.plusSeconds(i));
        }
        CommonException e = error(() -> limiter.acquire(7L, t.plusSeconds(30)));
        assertThat(e.getRetryAfterSeconds()).isEqualTo(31L);
        limiter.acquire(7L, t.plusSeconds(60).plusMillis(1)); // 첫 줄이 창을 벗어남
    }
}
