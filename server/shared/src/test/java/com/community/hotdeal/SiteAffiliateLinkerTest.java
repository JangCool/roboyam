package com.community.hotdeal;

import static org.assertj.core.api.Assertions.assertThat;

import com.community.hotdeal.service.SiteAffiliateLinker;
import com.community.hotdeal.service.SiteAffiliateLinker.SiteStore;
import java.util.List;
import org.junit.jupiter.api.Test;

/** D5 사이트 제휴 변환 — 몰 판정(켠 몰만 · 최종 호스트 우선 · 접미사 · 긴 도메인 우선) · TEMPLATE(encodeURIComponent) · QUERY(같은 이름은 바꿈) */
class SiteAffiliateLinkerTest {

    private static final SiteStore COUPANG = new SiteStore(1, "coupang.com", "쿠팡 파트너스", "TEMPLATE", "https://link.example/c?u={url}&x={url}", null, true);
    private static final SiteStore ELEVEN = new SiteStore(2, "11st.co.kr", "11번가 제휴", "QUERY", null, "aff=irobit&src=r", true);
    private static final SiteStore ELEVEN_M = new SiteStore(3, "m.11st.co.kr", "11번가 모바일", "QUERY", null, "aff=mobile", true);
    private static final SiteStore OFF = new SiteStore(4, "gmarket.co.kr", "G마켓", "QUERY", null, "aff=g", false);

    private static final List<SiteStore> STORES = List.of(COUPANG, ELEVEN, ELEVEN_M, OFF);

    @Test
    void 최종_호스트를_먼저_보고_없으면_처음_호스트() {
        assertThat(SiteAffiliateLinker.match(STORES, "coupa.ng", "coupang.com")).contains(COUPANG);
        assertThat(SiteAffiliateLinker.match(STORES, "www2.coupang.com", null)).contains(COUPANG);
        assertThat(SiteAffiliateLinker.match(STORES, "coupang.com", "unknown.kr")).contains(COUPANG);
    }

    @Test
    void 긴_도메인이_이기고_끈_몰은_없다() {
        assertThat(SiteAffiliateLinker.match(STORES, "m.11st.co.kr", null)).contains(ELEVEN_M);
        assertThat(SiteAffiliateLinker.match(STORES, "www.11st.co.kr".substring(4), null)).contains(ELEVEN);
        assertThat(SiteAffiliateLinker.match(STORES, "gmarket.co.kr", null)).isEmpty();
        assertThat(SiteAffiliateLinker.match(STORES, "not11st.co.kr", null)).isEmpty();
    }

    @Test
    void TEMPLATE_은_모든_자리에_encodeURIComponent() {
        String url = "https://www.coupang.com/vp/products/1?itemId=5&q=a b~(c)'*!";
        String out = SiteAffiliateLinker.transform(COUPANG, url);
        String encoded = "https%3A%2F%2Fwww.coupang.com%2Fvp%2Fproducts%2F1%3FitemId%3D5%26q%3Da%20b~(c)'*!";
        assertThat(out).isEqualTo("https://link.example/c?u=" + encoded + "&x=" + encoded);
    }

    @Test
    void QUERY_는_붙이되_같은_이름은_바꾸고_조각은_끝에() {
        assertThat(SiteAffiliateLinker.transform(ELEVEN, "https://11st.co.kr/p?prdNo=1&aff=me#a")).isEqualTo("https://11st.co.kr/p?prdNo=1&aff=irobit&src=r#a");
        assertThat(SiteAffiliateLinker.transform(ELEVEN, "https://11st.co.kr/p")).isEqualTo("https://11st.co.kr/p?aff=irobit&src=r");
    }
}
