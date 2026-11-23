package com.community.ad.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.community.ad.dto.AdPlacements;
import com.community.ad.dto.AdSenseSettings;
import com.community.bizapplication.entity.BizProduct;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 광고 자리 관리의 판정(스프링 없이) — 예시 주소 · 애드센스 FILLS|EMPTY|NA · 나가는 곳(노출 자리 설정을 따른다) */
class AdSlotAdminServiceTest {

    @Test
    void 예시_주소는_example_도메인과_하위_예약_최상위_이름_localhost() {
        assertThat(AdSlotAdminService.exampleUrl("https://example.com/a")).isTrue();
        assertThat(AdSlotAdminService.exampleUrl("https://shop.EXAMPLE.org/x?y=1")).isTrue();
        assertThat(AdSlotAdminService.exampleUrl("http://www.example.net.")).isTrue();
        assertThat(AdSlotAdminService.exampleUrl("https://foo.example")).isTrue();
        assertThat(AdSlotAdminService.exampleUrl("https://shop.test/a")).isTrue();
        assertThat(AdSlotAdminService.exampleUrl("https://a.invalid")).isTrue();
        assertThat(AdSlotAdminService.exampleUrl("http://localhost:3100/posts/1")).isTrue();
        assertThat(AdSlotAdminService.exampleUrl("http://app.localhost/")).isTrue();

        assertThat(AdSlotAdminService.exampleUrl("https://notexample.com/a")).isFalse();
        assertThat(AdSlotAdminService.exampleUrl("https://example.com.evil.kr/a")).isFalse();
        assertThat(AdSlotAdminService.exampleUrl("https://irobit.kr/sale")).isFalse();
        assertThat(AdSlotAdminService.exampleUrl("/posts/1")).isFalse();
        assertThat(AdSlotAdminService.exampleUrl("not a url")).isFalse();
        assertThat(AdSlotAdminService.exampleUrl(null)).isFalse();
    }

    @Test
    void 애드센스는_나가는_자리마다_보고_다_채우면_FILLS_일부면_PARTIAL_틀이_없으면_NA() {
        AdSenseSettings.Units units = new AdSenseSettings.Units("1111111111", null, null, null, null, "2222222222", "3333333333", new AdSenseSettings.InFeed("4444444444",
                "-fb+5w+4e-db+86"));
        AdSenseSettings show = new AdSenseSettings(AdSenseSettings.SHOW, "ca-pub-1234567890123456", false, units);
        List<String> gadget = List.of("HOME", "PAGE", "BOARD");
        assertThat(AdSlotAdminService.adsense(BizProduct.WIDE_BANNER, gadget, show)).isEqualTo("FILLS");
        assertThat(AdSlotAdminService.adsense(BizProduct.STRIP_BANNER, gadget, show)).isEqualTo("EMPTY"); // 단위 없음
        // 글 중간 유형인 배너 — 글 중간은 POST_INLINE 단위로 따로 본다
        assertThat(AdSlotAdminService.adsense(BizProduct.STRIP_BANNER, List.of("HOME", "PAGE", "BOARD", "POST_INLINE"), show)).isEqualTo("PARTIAL");
        // 날개 — 한쪽만 채우므로 양쪽이면 일부, 한쪽이면 다
        assertThat(AdSlotAdminService.adsense(BizProduct.WING_BANNER, List.of("WING_LEFT", "WING_RIGHT"), show)).isEqualTo("PARTIAL");
        assertThat(AdSlotAdminService.adsense(BizProduct.WING_BANNER, List.of("WING_RIGHT"), show)).isEqualTo("FILLS");
        // 한 줄 — 가젯 자리도 목록 한 줄과 같은 인피드로 채운다(2026-11-23), 글 중간은 POST_INLINE 단위
        assertThat(AdSlotAdminService.adsense(BizProduct.TEXT_LINE, List.of("HOME", "PAGE", "BOARD", "POST_INLINE", "BOARD_LIST"), show)).isEqualTo("FILLS");
        assertThat(AdSlotAdminService.adsense(BizProduct.TEXT_LINE, gadget, show)).isEqualTo("FILLS");
        assertThat(AdSlotAdminService.adsense(BizProduct.TEXT_LINE, List.of("BOARD_LIST"), show)).isEqualTo("FILLS");
        AdSenseSettings noFeed = new AdSenseSettings(AdSenseSettings.SHOW, "ca-pub-1234567890123456", false,
                new AdSenseSettings.Units(null, null, null, null, null, null, null, new AdSenseSettings.InFeed("4444444444", null)));
        assertThat(AdSlotAdminService.adsense(BizProduct.TEXT_LINE, List.of("HOME", "BOARD_LIST"), noFeed)).isEqualTo("EMPTY"); // 레이아웃 키 없음 — 가젯·목록 다 빈다
        assertThat(AdSlotAdminService.adsense(BizProduct.TEXT_LINE, List.of("HOME", "POST_INLINE"), show)).isEqualTo("FILLS");
        assertThat(AdSlotAdminService.adsense(BizProduct.HOTDEAL_TOP, List.of("HOTDEAL_TOP"), show)).isEqualTo("NA");
        assertThat(AdSlotAdminService.adsense(BizProduct.HOTDEAL_CATEGORY, List.of("HOTDEAL_CATEGORY"), show)).isEqualTo("NA");
        // 「광고 표시」 단계 + 게시자 ID 가 아니면 비어 있다
        AdSenseSettings verify = new AdSenseSettings(AdSenseSettings.VERIFY, "ca-pub-1234567890123456", false, units);
        assertThat(AdSlotAdminService.adsense(BizProduct.WIDE_BANNER, gadget, verify)).isEqualTo("EMPTY");
        AdSenseSettings noClient = new AdSenseSettings(AdSenseSettings.SHOW, null, false, units);
        assertThat(AdSlotAdminService.adsense(BizProduct.WIDE_BANNER, gadget, noClient)).isEqualTo("EMPTY");
        assertThat(AdSlotAdminService.adsense(BizProduct.WIDE_BANNER, gadget, AdSenseSettings.DEFAULTS)).isEqualTo("EMPTY");
    }

    @Test
    void 나가는_곳은_가젯_자리에_설정한_글_중간_유형_켠_날개_쪽_목록_한_줄을_더한다() {
        AdPlacements defaults = AdPlacements.DEFAULTS; // 글 중간 한 줄 · 날개 양쪽 · 목록 한 줄 켬
        assertThat(AdSlotAdminService.places(BizProduct.WIDE_BANNER, defaults)).containsExactly("HOME", "PAGE", "BOARD");
        assertThat(AdSlotAdminService.places(BizProduct.TEXT_LINE, defaults)).containsExactly("HOME", "PAGE", "BOARD", "POST_INLINE", "BOARD_LIST");
        assertThat(AdSlotAdminService.places(BizProduct.WING_BANNER, defaults)).containsExactly("WING_LEFT", "WING_RIGHT");
        assertThat(AdSlotAdminService.places(BizProduct.HOTDEAL_TOP, defaults)).containsExactly("HOTDEAL_TOP");
        assertThat(AdSlotAdminService.places(BizProduct.HOTDEAL_CATEGORY, defaults)).containsExactly("HOTDEAL_CATEGORY");

        AdPlacements square = new AdPlacements(new AdPlacements.PostInline(true, "SQUARE_BANNER", "AFTER_BODY"), new AdPlacements.Wings(false, true, List.of("HOME")),
                new AdPlacements.BoardList(false, 5, 1, 10), new AdPlacements.Adult(false));
        assertThat(AdSlotAdminService.places(BizProduct.SQUARE_BANNER, square)).containsExactly("HOME", "PAGE", "BOARD", "POST_INLINE");
        assertThat(AdSlotAdminService.places(BizProduct.TEXT_LINE, square)).containsExactly("HOME", "PAGE", "BOARD");
        assertThat(AdSlotAdminService.places(BizProduct.WING_BANNER, square)).containsExactly("WING_RIGHT");

        AdPlacements off = new AdPlacements(new AdPlacements.PostInline(false, "STRIP_BANNER", "BETWEEN_CARDS"), new AdPlacements.Wings(false, false, List.of()),
                new AdPlacements.BoardList(true, 5, 1, 10), new AdPlacements.Adult(false));
        assertThat(AdSlotAdminService.places(BizProduct.STRIP_BANNER, off)).containsExactly("HOME", "PAGE", "BOARD");
        assertThat(AdSlotAdminService.places(BizProduct.WING_BANNER, off)).isEmpty();
    }

    @Test
    void 거르기_입금_대기는_결제_대기를_함께() {
        assertThat(AdSlotAdminService.filterOf("AWAITING_PAYMENT")).isEqualTo("AWAITING_DEPOSIT");
        assertThat(AdSlotAdminService.filterOf("AWAITING_DEPOSIT")).isEqualTo("AWAITING_DEPOSIT");
        assertThat(AdSlotAdminService.filterOf("RUNNING")).isEqualTo("RUNNING");
    }
}
