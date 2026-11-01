package com.community.hotdeal;

import static org.assertj.core.api.Assertions.assertThat;

import com.community.hotdeal.service.DealHosts;
import com.community.hotdeal.service.DealLinkJudge;
import com.community.hotdeal.service.DealLinkJudge.ChainVerdict;
import com.community.hotdeal.service.DealLinkJudge.DomainRule;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 서버 규칙 2(링크 판정) — 호스트 정규화 · 접미사 일치(점 경계) · 더 구체적인 규칙 우선 · 리디렉션 체인의 모든 호스트 · 차단 &gt; 제휴 · 최종 호스트.
 */
class DealLinkJudgeTest {

    private static final List<DomainRule> RULES = List.of(
            new DomainRule("bit.ly", "BLOCK", false, true, List.of(), null),
            new DomainRule("coupa.ng", "AFFILIATE", false, false, List.of(), "쿠팡"),
            new DomainRule("link.coupang.com", "AFFILIATE", false, false, List.of(), null),
            new DomainRule("coupang.com", null, true, false, List.of(), "쿠팡"),
            new DomainRule("11st.co.kr", null, true, false, List.of("prdNo"), "11번가"),
            new DomainRule("cheap-robot.shop", "BLOCK", false, false, List.of(), null),
            new DomainRule("good.cheap-robot.shop", "AFFILIATE", false, false, List.of(), null),
            new DomainRule("named.cheap-robot.shop", null, false, false, List.of(), "이름만"));

    @Test
    void 호스트는_소문자_끝점_www_를_떼고_스킴_포트_경로는_보지_않는다() {
        assertThat(DealHosts.hostOf("HTTPS://WWW.Coupang.COM.:8443/vp/products/1?x=1#a")).isEqualTo("coupang.com");
        assertThat(DealHosts.hostOf("http://m.11st.co.kr/a")).isEqualTo("m.11st.co.kr");
        assertThat(DealHosts.hostOf("ftp://coupang.com/a")).isNull();
        assertThat(DealHosts.hostOf("https://user:pw@coupang.com/a")).isNull();
        assertThat(DealHosts.hostOf("not a url")).isNull();
    }

    @Test
    void 접미사_일치는_점_경계만_본다() {
        assertThat(DealLinkJudge.judgeHost("a.b.bit.ly", RULES).rule()).isEqualTo("BLOCK");
        assertThat(DealLinkJudge.judgeHost("notbit.ly", RULES).rule()).isNull();
        assertThat(DealLinkJudge.judgeHost("bit.ly.example.com", RULES).rule()).isNull();
    }

    @Test
    void 더_구체적인_규칙이_이긴다_차단_도메인의_하위가_제휴면_제휴() {
        assertThat(DealLinkJudge.judgeHost("good.cheap-robot.shop", RULES).rule()).isEqualTo("AFFILIATE");
        assertThat(DealLinkJudge.judgeHost("x.good.cheap-robot.shop", RULES).rule()).isEqualTo("AFFILIATE");
        assertThat(DealLinkJudge.judgeHost("other.cheap-robot.shop", RULES).rule()).isEqualTo("BLOCK");
        assertThat(DealLinkJudge.judgeHost("link.coupang.com", RULES).rule()).as("대형몰 줄보다 구체적인 제휴 줄").isEqualTo("AFFILIATE");
        assertThat(DealLinkJudge.judgeHost("www2.coupang.com", RULES).rule()).isNull();
    }

    @Test
    void 규칙_없이_이름만_둔_하위_줄은_위의_차단을_풀지_않는다() {
        DealLinkJudge.HostVerdict v = DealLinkJudge.judgeHost("named.cheap-robot.shop", RULES);
        assertThat(v.rule()).isEqualTo("BLOCK");
        assertThat(v.storeName()).isEqualTo("이름만");
    }

    @Test
    void 대형몰의_하위_도메인도_대형몰이고_keyParams_는_가장_구체적인_줄() {
        DealLinkJudge.HostVerdict v = DealLinkJudge.judgeHost("m.11st.co.kr", RULES);
        assertThat(v.common()).isTrue();
        assertThat(v.keyParams()).containsExactly("prdNo");
        assertThat(DealLinkJudge.judgeHost("link.coupang.com", RULES).common()).isTrue();
    }

    @Test
    void 체인의_모든_호스트를_본다_중간에_차단이_있으면_차단() {
        ChainVerdict v = DealLinkJudge.judgeChain(List.of("example-mall.kr", "bit.ly", "coupang.com"), RULES);
        assertThat(v.rule()).isEqualTo("BLOCK");
        assertThat(v.blockedHost()).isEqualTo("bit.ly");
        assertThat(v.shortener()).isTrue();
        assertThat(v.finalHost()).isEqualTo("coupang.com");
    }

    @Test
    void 차단이_제휴보다_먼저다() {
        ChainVerdict v = DealLinkJudge.judgeChain(List.of("coupa.ng", "other.cheap-robot.shop"), RULES);
        assertThat(v.rule()).isEqualTo("BLOCK");
        assertThat(v.shortener()).isFalse();
    }

    @Test
    void 제휴_단축_주소가_대형몰로_가면_제휴이고_최종_호스트와_이름은_최종_쪽() {
        ChainVerdict v = DealLinkJudge.judgeChain(List.of("coupa.ng", "www.coupang.com".replace("www.", ""), "coupang.com"), RULES);
        assertThat(v.rule()).isEqualTo("AFFILIATE");
        assertThat(v.host()).isEqualTo("coupa.ng");
        assertThat(v.finalHost()).isEqualTo("coupang.com");
        assertThat(v.hosts()).containsExactly("coupa.ng", "coupang.com");
        assertThat(v.storeName()).isEqualTo("쿠팡");
        assertThat(v.common()).isTrue();
    }

    @Test
    void 최종_호스트가_처음과_같으면_null_이고_제휴로_가는_리디렉션도_제휴() {
        assertThat(DealLinkJudge.judgeChain(List.of("coupang.com"), RULES).finalHost()).isNull();
        ChainVerdict v = DealLinkJudge.judgeChain(List.of("plain-shop.kr", "link.coupang.com"), RULES);
        assertThat(v.rule()).isEqualTo("AFFILIATE");
        assertThat(v.finalHost()).isEqualTo("link.coupang.com");
        assertThat(v.keyParams()).as("같은 딜 키는 처음(저장한) 링크 호스트의 keyParams").isEmpty();
    }

    @Test
    void 규칙이_없으면_null() {
        ChainVerdict v = DealLinkJudge.judgeChain(List.of("plain-shop.kr"), RULES);
        assertThat(v.rule()).isNull();
        assertThat(v.blocked()).isFalse();
        assertThat(v.affiliate()).isFalse();
        assertThat(v.storeName()).isNull();
    }
}
