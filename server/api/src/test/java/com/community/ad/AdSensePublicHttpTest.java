package com.community.ad;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.handler;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.community.ad.controller.AdsTxtController;
import com.community.ad.service.AdSenseSiteService;
import com.community.analytics.collect.VisitCollector;
import com.community.bizapplication.BizApplicationApiTestBase;
import com.community.installation.service.InstallationState;
import com.community.site.config.AdminPreviewFrame;
import com.community.site.config.SiteHeadFilter;
import com.community.site.dto.PublicSiteSnapshot;
import com.community.site.dto.RenderedIndex;
import com.community.site.service.IndexHtmlRenderer;
import com.community.site.service.PublicSiteService;
import jakarta.servlet.Filter;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 구글 애드센스 공개 쪽(docs/features/adsense-contract.md 「서버 구현」, shared V176) — 실제 DB 의 {@code ad_placement_settings} 한 행을 바꿔 가며 본다.
 *
 * <ul>
 *   <li>{@code GET /api/ads/placements} 의 {@code adsense}: 꺼짐·행 없음이면 null(키는 있다) · VERIFY 면 {@code units} 빈 묶음 · SHOW 면 값 있는 자리만.</li>
 *   <li>{@code /ads.txt}: VERIFY·SHOW + 게시자 ID 면 200 {@code text/plain} 한 줄 · 꺼짐이면 본문 없는 404(컨트롤러가 받는다 — 화면 index.html 로 바뀌지 않음).</li>
 *   <li>머리 주입: {@link SiteHeadFilter} 를 실제 {@link AdSenseSiteService}(DB 읽기)·실제 {@link IndexHtmlRenderer#withAdSense} 로 — 시험 클래스패스에는 화면
 *       {@code static/index.html} 이 없어 바탕 사본만 고정 본문으로 바꾼다. VERIFY·SHOW 면 메타·스크립트, OFF·미리보기면 없음, 설정을 바꾸면 다음 요청부터.</li>
 * </ul>
 */
class AdSensePublicHttpTest extends BizApplicationApiTestBase {

    private static final String CLIENT = "ca-pub-1234567890123456";
    private static final String OTHER = "ca-pub-6543210987654321";
    private static final String ADMIN_ORIGIN = "https://admin.roboyam.test";
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String BASE_HTML = "<!doctype html><html lang=\"ko\"><head><meta charset=\"utf-8\"><title>로보얌</title></head>"
            + "<body><div id=\"__nuxt\"></div></body></html>";
    private static final String BASE_ETAG = "base-etag";

    @Autowired
    private WebApplicationContext context;
    @Autowired
    private InstallationState installation;
    @Autowired
    @Qualifier("springSecurityFilterChain")
    private Filter securityFilterChain;
    @Autowired
    private AdSenseSiteService adsense;
    @Autowired
    private VisitCollector visits;

    private MockMvc secured;

    @BeforeEach
    void setUpMvc() {
        installation.markCompleted();
        secured = MockMvcBuilders.webAppContextSetup(context).addFilters(securityFilterChain).build();
        jdbc.update("DELETE FROM ad_placement_settings");
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM ad_placement_settings");
    }

    // ── 준비 ───────────────────────────────────────────────────────────────

    /** 자리 설정 한 행(기본값) + 애드센스 칸. {@code units} 는 칸 이름 → 값(인피드 짝 CHECK 때문에 한 문장으로 넣는다) */
    private void row(String mode, String client, boolean adtest, Map<String, String> units) {
        jdbc.update("DELETE FROM ad_placement_settings");
        jdbc.update("INSERT INTO ad_placement_settings (id, post_inline_enabled, post_inline_type, post_inline_position, wing_left, wing_right, wing_screens, "
                + "board_list_enabled, board_list_after, updated_at, updated_by) VALUES (1, true, 'TEXT_LINE', 'BETWEEN_CARDS', true, true, "
                + "ARRAY['HOME','PAGE','BOARD','POST']::text[], true, 5, now(), 1)");
        set(mode, client, adtest, units);
    }

    /** 있는 행의 애드센스 칸만 바꾼다(관리자 저장 뒤의 다음 요청 흉내) */
    private void set(String mode, String client, boolean adtest, Map<String, String> units) {
        StringBuilder sql = new StringBuilder("UPDATE ad_placement_settings SET adsense_mode = ?, adsense_client = ?, adsense_adtest = ?");
        Object[] args = new Object[3 + units.size()];
        args[0] = mode;
        args[1] = client;
        args[2] = adtest;
        int i = 3;
        for (Map.Entry<String, String> e : units.entrySet()) {
            sql.append(", ").append(e.getKey()).append(" = ?");
            args[i++] = e.getValue();
        }
        jdbc.update(sql.append(" WHERE id = 1").toString(), args);
    }

    private static Map<String, String> units(String... columnValue) {
        Map<String, String> out = new LinkedHashMap<>();
        for (int i = 0; i < columnValue.length; i += 2) {
            out.put(columnValue[i], columnValue[i + 1]);
        }
        return out;
    }

    private JsonNode placementsAdsense() throws Exception {
        String body = secured.perform(get("/api/ads/placements")).andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andReturn().getResponse().getContentAsString();
        JsonNode data = JSON.readTree(body).path("data");
        assertThat(data.has("adsense")).as("adsense 키는 늘 있다").isTrue();
        assertThat(data.has("postInline")).isTrue();
        return data.get("adsense");
    }

    // ── 공개 자리 설정 ─────────────────────────────────────────────────────

    @Test
    void 공개_자리_설정의_adsense_는_행이_없거나_OFF_면_null() throws Exception {
        assertThat(placementsAdsense().isNull()).isTrue();
        // 꺼져 있으면 게시자 ID·단위가 있어도 null
        row("OFF", CLIENT, true, units("adsense_wide_banner", "1234567890"));
        assertThat(placementsAdsense().isNull()).isTrue();
        row("OFF", null, false, units());
        assertThat(placementsAdsense().isNull()).isTrue();
    }

    @Test
    void VERIFY_면_units_는_빈_묶음() throws Exception {
        row("VERIFY", CLIENT, true, units("adsense_wide_banner", "1234567890", "adsense_board_list_slot", "4234567890", "adsense_board_list_layout_key", "-fb+5w+4e-db+86"));
        JsonNode a = placementsAdsense();
        assertThat(a.path("mode").asString()).isEqualTo("VERIFY");
        assertThat(a.path("client").asString()).isEqualTo(CLIENT);
        assertThat(a.path("adtest").asBoolean()).isTrue();
        assertThat(a.get("units").isObject()).isTrue();
        assertThat(a.get("units").isEmpty()).isTrue();
    }

    @Test
    void SHOW_면_값_있는_자리만_BOARD_LIST_는_단위_ID_와_레이아웃_키가_다_있을_때() throws Exception {
        row("SHOW", CLIENT, false, units("adsense_wide_banner", "1234567890", "adsense_wing_banner", "2234567890", "adsense_post_inline", "3234567890",
                "adsense_board_list_slot", "4234567890", "adsense_board_list_layout_key", "-fb+5w+4e-db+86"));
        JsonNode a = placementsAdsense();
        assertThat(a.path("mode").asString()).isEqualTo("SHOW");
        assertThat(a.path("adtest").asBoolean()).isFalse();
        JsonNode u = a.get("units");
        assertThat(names(u)).containsExactlyInAnyOrder("WIDE_BANNER", "WING_BANNER", "POST_INLINE", "BOARD_LIST");
        assertThat(u.path("WIDE_BANNER").asString()).isEqualTo("1234567890");
        assertThat(u.path("WING_BANNER").asString()).isEqualTo("2234567890");
        assertThat(u.path("POST_INLINE").asString()).isEqualTo("3234567890");
        assertThat(u.path("BOARD_LIST").path("slot").asString()).isEqualTo("4234567890");
        assertThat(u.path("BOARD_LIST").path("layoutKey").asString()).isEqualTo("-fb+5w+4e-db+86");

        // 단위가 하나도 없으면 빈 묶음 · 인피드가 없으면 BOARD_LIST 키 없음
        row("SHOW", CLIENT, false, units("adsense_strip_banner", "5234567890"));
        assertThat(names(placementsAdsense().get("units"))).containsExactly("STRIP_BANNER");
        row("SHOW", CLIENT, false, units());
        assertThat(placementsAdsense().get("units").isEmpty()).isTrue();
    }

    private static java.util.List<String> names(JsonNode node) {
        java.util.List<String> out = new java.util.ArrayList<>();
        node.propertyNames().forEach(out::add);
        return out;
    }

    // ── /ads.txt ──────────────────────────────────────────────────────────

    @Test
    void ads_txt_는_VERIFY_SHOW_면_200_text_plain_한_줄() throws Exception {
        for (String mode : new String[]{"VERIFY", "SHOW"}) {
            row(mode, CLIENT, false, units());
            secured.perform(get("/ads.txt")).andExpect(status().isOk()).andExpect(handler().handlerType(AdsTxtController.class))
                    .andExpect(header().string("Content-Type", "text/plain;charset=UTF-8")).andExpect(header().string("Cache-Control", "no-cache"))
                    .andExpect(content().bytes("google.com, pub-1234567890123456, DIRECT, f08c47fec0942fa0\n".getBytes(StandardCharsets.UTF_8)));
        }
        // 게시자 ID 를 바꾸면 다음 요청부터
        set("SHOW", OTHER, false, units());
        secured.perform(get("/ads.txt")).andExpect(status().isOk()).andExpect(content().string("google.com, pub-6543210987654321, DIRECT, f08c47fec0942fa0\n"));
    }

    @Test
    void ads_txt_는_꺼져_있으면_본문_없는_404_이고_화면으로_바뀌지_않는다() throws Exception {
        // 행 없음
        assertAdsTxtNotFound();
        // OFF(게시자 ID 있음)
        row("OFF", CLIENT, false, units());
        assertAdsTxtNotFound();
        // 켜져 있다가 끄면 다음 요청부터 404
        set("VERIFY", CLIENT, false, units());
        secured.perform(get("/ads.txt")).andExpect(status().isOk());
        set("OFF", CLIENT, false, units());
        assertAdsTxtNotFound();
    }

    private void assertAdsTxtNotFound() throws Exception {
        MockHttpServletResponse res = secured.perform(get("/ads.txt")).andExpect(status().isNotFound()).andExpect(handler().handlerType(AdsTxtController.class))
                .andExpect(header().string("Cache-Control", "no-cache")).andReturn().getResponse();
        assertThat(res.getContentAsByteArray()).isEmpty();
        String type = res.getContentType();
        assertThat(type == null || !type.contains("text/html")).as("index.html(text/html) 로 바뀌지 않는다: " + type).isTrue();
    }

    // ── index.html 머리 주입 ──────────────────────────────────────────────

    /** 바탕 사본(머리를 채운 index.html)을 고정 본문으로 — 애드센스 머리 더하기({@link IndexHtmlRenderer#withAdSense})는 실제 코드 */
    static final class FixedHeadRenderer extends IndexHtmlRenderer {

        @Override
        public Optional<RenderedIndex> render(PublicSiteSnapshot snapshot) {
            return Optional.of(new RenderedIndex(BASE_HTML.getBytes(StandardCharsets.UTF_8), BASE_ETAG));
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

    private SiteHeadFilter filter(String adminOrigin) {
        return new SiteHeadFilter(new NullSiteService(), new FixedHeadRenderer(), new AdminPreviewFrame(adminOrigin), visits, adsense);
    }

    private static MockHttpServletRequest page(String path, boolean preview) {
        MockHttpServletRequest r = new MockHttpServletRequest("GET", path);
        r.setServletPath(path);
        if (preview) {
            r.setParameter("adminPreview", "1");
        }
        return r;
    }

    private static MockHttpServletResponse serve(SiteHeadFilter f, MockHttpServletRequest request) throws Exception {
        MockHttpServletResponse res = new MockHttpServletResponse();
        f.doFilter(request, res, new MockFilterChain());
        assertThat(res.getStatus()).isEqualTo(200);
        assertThat(res.getContentType()).startsWith("text/html");
        return res;
    }

    private static String meta(String client) {
        return "<meta name=\"google-adsense-account\" content=\"" + client + "\">";
    }

    private static String script(String client) {
        return "<script async src=\"https://pagead2.googlesyndication.com/pagead/js/adsbygoogle.js?client=" + client + "\" crossorigin=\"anonymous\"></script>";
    }

    @Test
    void VERIFY_SHOW_면_공개_화면_머리_끝에_메타와_스크립트() throws Exception {
        SiteHeadFilter f = filter(ADMIN_ORIGIN);
        for (String mode : new String[]{"VERIFY", "SHOW"}) {
            row(mode, CLIENT, false, units());
            for (String path : new String[]{"/", "/boards/free", "/index.html"}) {
                MockHttpServletResponse res = serve(f, page(path, false));
                String html = res.getContentAsString(StandardCharsets.UTF_8);
                assertThat(html).as(mode + " " + path).contains(meta(CLIENT), script(CLIENT));
                // </head> 바로 앞, 한 번만
                int head = html.indexOf("</head>");
                assertThat(html.indexOf(meta(CLIENT))).isPositive().isLessThan(head);
                assertThat(html.indexOf(script(CLIENT))).isGreaterThan(html.indexOf(meta(CLIENT))).isLessThan(head);
                assertThat(html.indexOf(meta(CLIENT))).isEqualTo(html.lastIndexOf(meta(CLIENT)));
                assertThat(html.indexOf("adsbygoogle.js")).isEqualTo(html.lastIndexOf("adsbygoogle.js"));
                assertThat(html.substring(html.indexOf(script(CLIENT)) + script(CLIENT).length()).strip()).startsWith("</head>");
                // 나머지는 바탕 그대로
                assertThat(html).startsWith("<!doctype html><html lang=\"ko\"><head><meta charset=\"utf-8\"><title>로보얌</title>")
                        .endsWith("</head><body><div id=\"__nuxt\"></div></body></html>");
                // ETag 는 바탕과 다르다(바탕 지문을 그대로 쓰면 설정을 바꿔도 304 가 된다)
                assertThat(res.getHeader("ETag")).isNotNull().isNotEqualTo("\"" + BASE_ETAG + "\"");
            }
        }
    }

    @Test
    void OFF_거나_행이_없으면_머리에_애드센스가_없다() throws Exception {
        SiteHeadFilter f = filter(ADMIN_ORIGIN);
        String noRow = serve(f, page("/", false)).getContentAsString(StandardCharsets.UTF_8);
        assertThat(noRow).isEqualTo(BASE_HTML);
        row("OFF", CLIENT, false, units("adsense_wide_banner", "1234567890"));
        MockHttpServletResponse off = serve(f, page("/", false));
        assertThat(off.getContentAsString(StandardCharsets.UTF_8)).isEqualTo(BASE_HTML).doesNotContain("google-adsense-account", "adsbygoogle");
        assertThat(off.getHeader("ETag")).isEqualTo("\"" + BASE_ETAG + "\"");
    }

    @Test
    void 관리자_미리보기_응답에는_애드센스를_넣지_않는다() throws Exception {
        row("SHOW", CLIENT, false, units());
        SiteHeadFilter f = filter(ADMIN_ORIGIN);
        String preview = serve(f, page("/", true)).getContentAsString(StandardCharsets.UTF_8);
        assertThat(preview).contains("<meta name=\"roboyam-admin-origin\" content=\"" + ADMIN_ORIGIN + "\">")
                .doesNotContain("google-adsense-account", "adsbygoogle");
        String previewBoard = serve(f, page("/boards/free", true)).getContentAsString(StandardCharsets.UTF_8);
        assertThat(previewBoard).doesNotContain("google-adsense-account", "adsbygoogle");
        // 같은 필터의 보통 요청에는 들어간다
        assertThat(serve(f, page("/", false)).getContentAsString(StandardCharsets.UTF_8)).contains(meta(CLIENT)).doesNotContain("roboyam-admin-origin");
        // 관리자 주소가 없으면 adminPreview=1 은 미리보기가 아니다(AdminPreviewFrame#isPreview) — 보통 요청처럼 애드센스가 들어간다
        String notPreview = serve(filter(""), page("/", true)).getContentAsString(StandardCharsets.UTF_8);
        assertThat(notPreview).contains(meta(CLIENT)).doesNotContain("roboyam-admin-origin");
    }

    @Test
    void 설정을_바꾸면_다음_요청부터_머리가_따라온다() throws Exception {
        SiteHeadFilter f = filter(ADMIN_ORIGIN);
        row("VERIFY", CLIENT, false, units());
        MockHttpServletResponse first = serve(f, page("/", false));
        String etagA = first.getHeader("ETag");
        assertThat(first.getContentAsString(StandardCharsets.UTF_8)).contains(meta(CLIENT), script(CLIENT));
        // 같은 설정 — 같은 ETag 면 304
        MockHttpServletRequest cond = page("/", false);
        cond.addHeader("If-None-Match", etagA);
        MockHttpServletResponse notModified = new MockHttpServletResponse();
        f.doFilter(cond, notModified, new MockFilterChain());
        assertThat(notModified.getStatus()).isEqualTo(304);

        // 게시자 ID 바꿈 — 다음 요청이 새 ID, 옛 ETag 로 물어도 200
        set("SHOW", OTHER, false, units());
        MockHttpServletRequest stale = page("/", false);
        stale.addHeader("If-None-Match", etagA);
        MockHttpServletResponse changed = serve(f, stale);
        assertThat(changed.getContentAsString(StandardCharsets.UTF_8)).contains(meta(OTHER), script(OTHER)).doesNotContain(CLIENT);
        assertThat(changed.getHeader("ETag")).isNotEqualTo(etagA);

        // 끄면 다음 요청부터 없음
        set("OFF", OTHER, false, units());
        assertThat(serve(f, page("/", false)).getContentAsString(StandardCharsets.UTF_8)).isEqualTo(BASE_HTML);
        // 다시 처음 ID 로 켜면 처음과 같은 본문·ETag
        set("VERIFY", CLIENT, false, units());
        MockHttpServletResponse again = serve(f, page("/", false));
        assertThat(again.getContentAsString(StandardCharsets.UTF_8)).isEqualTo(first.getContentAsString(StandardCharsets.UTF_8));
        assertThat(again.getHeader("ETag")).isEqualTo(etagA);
    }
}
