package com.community.hotdeal;

import static org.assertj.core.api.Assertions.assertThat;

import com.community.hotdeal.service.DealUrlKey;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 서버 규칙 3(같은 딜 키) — 스킴·호스트 소문자 · www · 기본 포트 · 조각 · 끝 / 제거 + 추적 파라미터 제거 + keyParams 가 있으면 그 쿼리만 → 정렬 → SHA-256.
 */
class DealUrlKeyTest {

    @Test
    void 표본_정규_주소() {
        assertThat(DealUrlKey.canonical("HTTPS://WWW.Coupang.com:443/vp/products/123/?b=2&a=1#review", List.of()))
                .isEqualTo("https://coupang.com/vp/products/123?a=1&b=2");
        assertThat(DealUrlKey.canonical("http://shop.example.com:80/item", List.of())).isEqualTo("http://shop.example.com/item");
        assertThat(DealUrlKey.canonical("http://shop.example.com:8080/item/", List.of())).isEqualTo("http://shop.example.com:8080/item");
    }

    @Test
    void 추적_파라미터를_뗀다() {
        String url = "https://shop.example.com/p/9?utm_source=a&UTM_MEDIUM=b&fbclid=c&gclid=d&igshid=e&ref=f&tag=g&affid=h&aff_sub=i&spm=j&_trkparms=k&sub_id=l&color=red";
        assertThat(DealUrlKey.canonical(url, List.of())).isEqualTo("https://shop.example.com/p/9?color=red");
        assertThat(DealUrlKey.isTracking("Affiliate")).isTrue();
        assertThat(DealUrlKey.isTracking("refer")).isFalse();
    }

    @Test
    void keyParams_가_있으면_그_쿼리만_남긴다() {
        String a = "https://www.11st.co.kr/products/view?prdNo=777&trTypeCd=22&utm_source=x";
        String b = "https://11st.co.kr/products/view?trTypeCd=99&prdNo=777";
        assertThat(DealUrlKey.canonical(a, List.of("prdNo"))).isEqualTo("https://11st.co.kr/products/view?prdNo=777");
        assertThat(DealUrlKey.of(a, List.of("prdNo"))).isEqualTo(DealUrlKey.of(b, List.of("prdNo")));
        assertThat(DealUrlKey.of(a, List.of())).as("keyParams 가 없으면 다른 쿼리도 키에 든다").isNotEqualTo(DealUrlKey.of(b, List.of()));
    }

    @Test
    void 같은_상품의_다른_표기는_같은_키_다른_상품은_다른_키() {
        String key = DealUrlKey.of("https://coupang.com/vp/products/1?itemId=5", List.of());
        assertThat(key).hasSize(64).matches("[0-9a-f]{64}");
        assertThat(DealUrlKey.of("HTTPS://www.COUPANG.com/vp/products/1/?itemId=5&utm_campaign=z#top", List.of())).isEqualTo(key);
        assertThat(DealUrlKey.of("https://coupang.com/vp/products/2?itemId=5", List.of())).isNotEqualTo(key);
        assertThat(DealUrlKey.of("https://coupang.com/vp/products/1?itemId=6", List.of())).isNotEqualTo(key);
        assertThat(DealUrlKey.of("not a url", List.of())).isNull();
    }

    @Test
    void 추적_파라미터만_떼는_주소는_차례와_조각을_지킨다() {
        assertThat(DealUrlKey.stripTracking("https://shop.example.com/p?z=1&affid=me&a=2&utm_source=x#frag")).isEqualTo("https://shop.example.com/p?z=1&a=2#frag");
        assertThat(DealUrlKey.stripTracking("https://shop.example.com/p?utm_source=x")).isEqualTo("https://shop.example.com/p");
        assertThat(DealUrlKey.stripTracking("https://shop.example.com/p")).isEqualTo("https://shop.example.com/p");
    }
}
