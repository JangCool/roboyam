package com.community.ad;

import static org.assertj.core.api.Assertions.assertThat;

import com.community.ad.dto.AdSenseSettings;
import com.community.ad.service.AdSenseSiteService;
import com.community.analytics.collect.VisitCollector;
import com.community.site.config.AdminPreviewFrame;
import com.community.site.config.SiteHeadFilter;
import com.community.site.dto.PublicSiteSnapshot;
import com.community.site.dto.RenderedIndex;
import com.community.site.service.IndexHtmlRenderer;
import com.community.site.service.PublicSiteService;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * 구글 애드센스 모양 규칙(DB 없이 — {@link AdSenseSettings} 공개 묶음·{@code ads.txt}·머리 게시자 ID)과 머리 주입의 실패 처리(설정 읽기가 실패하면 애드센스 없이 내린다).
 * DB CHECK 로는 만들 수 없는 조합(VERIFY 인데 게시자 ID 없음 · 인피드 한쪽만)도 여기서 본다.
 */
class AdSenseSettingsTest {

    private static final String CLIENT = "ca-pub-1234567890123456";

    private static AdSenseSettings.Units units(String wide, String feedSlot, String layoutKey) {
        return new AdSenseSettings.Units(wide, null, null, null, null, null, new AdSenseSettings.InFeed(feedSlot, layoutKey));
    }

    @Test
    void 기본값은_끔이고_아무것도_내지_않는다() {
        AdSenseSettings d = AdSenseSettings.DEFAULTS;
        assertThat(d.mode()).isEqualTo("OFF");
        assertThat(d.client()).isNull();
        assertThat(d.adtest()).isFalse();
        assertThat(d.units().boardList().slot()).isNull();
        assertThat(d.activeClient()).isEmpty();
        assertThat(d.adsTxt()).isEmpty();
        assertThat(d.publicView()).isNull();
    }

    @Test
    void 켜짐은_VERIFY_SHOW_이고_게시자_ID_가_있을_때만() {
        assertThat(new AdSenseSettings("OFF", CLIENT, false, null).activeClient()).isEmpty();
        assertThat(new AdSenseSettings("OFF", CLIENT, false, null).publicView()).isNull();
        assertThat(new AdSenseSettings("VERIFY", null, false, null).activeClient()).isEmpty();
        assertThat(new AdSenseSettings("SHOW", "  ", false, null).activeClient()).isEmpty();
        assertThat(new AdSenseSettings("SHOW", null, false, units("1234567890", null, null)).publicView()).isNull();
        assertThat(new AdSenseSettings("VERIFY", CLIENT, false, null).activeClient()).contains(CLIENT);
        assertThat(new AdSenseSettings("SHOW", CLIENT, false, null).activeClient()).contains(CLIENT);
        // units 가 null 이면 빈 묶음으로
        assertThat(new AdSenseSettings("SHOW", CLIENT, false, null).units()).isEqualTo(AdSenseSettings.Units.EMPTY);
    }

    @Test
    void ads_txt_는_ca_를_뗀_pub_한_줄_줄바꿈_포함() {
        assertThat(new AdSenseSettings("VERIFY", CLIENT, false, null).adsTxt()).contains("google.com, pub-1234567890123456, DIRECT, f08c47fec0942fa0\n");
        assertThat(new AdSenseSettings("SHOW", CLIENT, true, null).adsTxt()).contains("google.com, pub-1234567890123456, DIRECT, f08c47fec0942fa0\n");
        assertThat(new AdSenseSettings("OFF", CLIENT, false, null).adsTxt()).isEmpty();
    }

    @Test
    void 공개_묶음_VERIFY_는_빈_units_SHOW_는_값_있는_자리만_인피드는_둘_다() {
        AdSenseSettings.Units all = new AdSenseSettings.Units("1111111111", "2222222222", "3333333333", "4444444444", "5555555555", "6666666666",
                new AdSenseSettings.InFeed("7777777777", "-fb+5w+4e-db+86"));
        AdSenseSettings.PublicView verify = new AdSenseSettings("VERIFY", CLIENT, true, all).publicView();
        assertThat(verify.mode()).isEqualTo("VERIFY");
        assertThat(verify.client()).isEqualTo(CLIENT);
        assertThat(verify.adtest()).isTrue();
        assertThat(verify.units()).isEmpty();

        AdSenseSettings.PublicView show = new AdSenseSettings("SHOW", CLIENT, false, all).publicView();
        assertThat(show.units()).containsOnlyKeys("WIDE_BANNER", "STRIP_BANNER", "SQUARE_BANNER", "SMALL_BANNER", "WING_BANNER", "POST_INLINE", "BOARD_LIST");
        assertThat(show.units().get("BOARD_LIST")).isEqualTo(new AdSenseSettings.InFeed("7777777777", "-fb+5w+4e-db+86"));

        // 인피드 한쪽만이면 BOARD_LIST 를 내지 않는다
        assertThat(new AdSenseSettings("SHOW", CLIENT, false, units("1234567890", "7777777777", null)).publicView().units()).containsOnlyKeys("WIDE_BANNER");
        assertThat(new AdSenseSettings("SHOW", CLIENT, false, units(null, null, "-fb+5w+4e-db+86")).publicView().units()).isEmpty();
        assertThat(new AdSenseSettings("SHOW", CLIENT, false, units(null, null, null)).publicView().units()).isEmpty();
    }

    // ── 머리 주입 실패 처리 ─────────────────────────────────────────────────

    static final class FixedRenderer extends IndexHtmlRenderer {

        static final String HTML = "<html><head><title>t</title></head><body></body></html>";

        @Override
        public Optional<RenderedIndex> render(PublicSiteSnapshot snapshot) {
            return Optional.of(new RenderedIndex(HTML.getBytes(StandardCharsets.UTF_8), "fixed"));
        }
    }

    static final class NullSiteService extends PublicSiteService {

        NullSiteService() {
            super(null, null, null, null, null, null, null);
        }

        @Override
        public PublicSiteSnapshot snapshot() {
            return null;
        }
    }

    /** 설정 읽기가 늘 실패한다 */
    static final class BrokenAdSense extends AdSenseSiteService {

        BrokenAdSense() {
            super(null);
        }

        @Override
        public Optional<String> activeClient() {
            throw new IllegalStateException("DB 끊김(시험)");
        }
    }

    /** 늘 같은 게시자 ID */
    static final class FixedAdSense extends AdSenseSiteService {

        final String client;

        FixedAdSense(String client) {
            super(null);
            this.client = client;
        }

        @Override
        public Optional<String> activeClient() {
            return Optional.of(client);
        }
    }

    private static VisitCollector noVisits() {
        // 봇 통계는 이 시험과 상관없다 — 꺼진 수집기
        return new VisitCollector(null, null, null) {
            @Override
            public void countBotPage(String userAgent) {
            }
        };
    }

    private static MockHttpServletResponse serve(AdSenseSiteService adsense) throws Exception {
        SiteHeadFilter f = new SiteHeadFilter(new NullSiteService(), new FixedRenderer(), new AdminPreviewFrame(""), noVisits(), adsense);
        MockHttpServletRequest r = new MockHttpServletRequest("GET", "/");
        r.setServletPath("/");
        MockHttpServletResponse res = new MockHttpServletResponse();
        f.doFilter(r, res, new MockFilterChain());
        return res;
    }

    @Test
    void 애드센스_설정을_읽지_못하면_애드센스_없이_화면을_내린다() throws Exception {
        MockHttpServletResponse res = serve(new BrokenAdSense());
        assertThat(res.getStatus()).isEqualTo(200);
        assertThat(res.getContentAsString(StandardCharsets.UTF_8)).isEqualTo(FixedRenderer.HTML);
    }

    @Test
    void 게시자_ID_는_HTML_이스케이프해_넣는다() throws Exception {
        // 관리자 서버가 형식을 검사하지만 렌더러는 다른 칸처럼 이스케이프한다
        String html = serve(new FixedAdSense("ca-pub-1\"><script>x</script>")).getContentAsString(StandardCharsets.UTF_8);
        assertThat(html).doesNotContain("<script>x</script>").contains("&quot;&gt;&lt;script&gt;");
    }
}
