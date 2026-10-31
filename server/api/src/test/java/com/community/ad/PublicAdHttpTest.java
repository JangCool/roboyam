package com.community.ad;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.community.ad.service.PublicAdService;
import com.community.bizapplication.BizApplicationApiTestBase;
import com.community.bizapplication.service.BizPeriods;
import com.community.installation.service.InstallationState;
import com.community.zconfiguration.time.ServiceTime;
import jakarta.servlet.Filter;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * 공개 광고 HTTP(실제 보안 필터 체인) — 로그인 없이 읽고, 노출·클릭 두 집계 주소만 CSRF 토큰 없이 200(sendBeacon — application/json 본문), 다른 공개 POST 는 여전히 403
 * {@code CSRF_INVALID}(결제 웹훅 시험 선례). 목록 {@code {items}}·no-store·모르는 유형 400, 자리 설정 기본값 모양, 노출 20건 초과 400 {@code fields.items}.
 */
class PublicAdHttpTest extends BizApplicationApiTestBase {

    private static final String UA = "Mozilla/5.0 (Macintosh; Intel Mac OS X 14_0) Safari/605.1.15";

    @Autowired
    private WebApplicationContext context;
    @Autowired
    private InstallationState installation;
    @Autowired
    @Qualifier("springSecurityFilterChain")
    private Filter securityFilterChain;
    @Autowired
    private PublicAdService ads;

    private MockMvc secured;

    @BeforeEach
    void setUpMvc() {
        installation.markCompleted();
        secured = MockMvcBuilders.webAppContextSetup(context).addFilters(securityFilterChain).build();
        ads.invalidate();
    }

    @AfterEach
    void dropCache() {
        ads.invalidate();
    }

    @Test
    void 집계_두_주소는_CSRF_없이_200이고_다른_공개_POST_는_여전히_403() throws Exception {
        BizPeriods.Period p = freshPeriod(3);
        long user = approvedBusiness();
        long id = submitTextLine(user, 0, 2, "ONLINE");
        approve(id, "ONLINE", p);
        pay(user, id);
        clock.set(p.start().plus(Duration.ofHours(2)));

        secured.perform(post("/api/ads/impressions").contentType(MediaType.APPLICATION_JSON).header("User-Agent", UA).header("X-Forwarded-For", "203.0.113.60")
                        .content("{\"items\":[{\"adId\":\"" + id + "\",\"version\":1,\"placement\":\"HOME\"}]}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true)).andExpect(header().string("Cache-Control", "no-store"));
        secured.perform(post("/api/ads/" + id + "/clicks").contentType(MediaType.APPLICATION_JSON).header("User-Agent", UA)
                .content("{\"version\":1,\"placement\":\"HOME\"}")).andExpect(status().isOk());
        // 셌다 — 노출 1 · 클릭 1
        java.util.Map<String, Object> row = jdbc.queryForMap("SELECT impressions, clicks FROM ad_daily_stats WHERE application_id = ? AND version = 1 AND day = ? "
                + "AND placement = 'HOME'", id, java.sql.Date.valueOf(ServiceTime.today(clock)));
        assertThat(((Number) row.get("impressions")).longValue()).isEqualTo(1);
        assertThat(((Number) row.get("clicks")).longValue()).isEqualTo(1);
        // 버린 줄·봇도 같은 200
        secured.perform(post("/api/ads/impressions").contentType(MediaType.APPLICATION_JSON)
                .content("{\"items\":[{\"adId\":\"1\",\"version\":1,\"placement\":\"HOME\"}]}")).andExpect(status().isOk());
        secured.perform(post("/api/ads/999/clicks").contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isOk());

        // 다른 공개 POST(글쓰기 — 비회원도 부르는 주소)는 CSRF 토큰이 없으면 403
        secured.perform(post("/api/posts").contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("CSRF_INVALID"));
        secured.perform(post("/api/group-buys/1/quote").contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("CSRF_INVALID"));
    }

    @Test
    void 노출_20건_초과는_400_fields_items() throws Exception {
        StringBuilder items = new StringBuilder();
        for (int i = 0; i < 21; i++) {
            items.append(i == 0 ? "" : ",").append("{\"adId\":\"1\",\"version\":1,\"placement\":\"HOME\"}");
        }
        secured.perform(post("/api/ads/impressions").contentType(MediaType.APPLICATION_JSON).content("{\"items\":[" + items + "]}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.fields.items").exists());
    }

    @Test
    void 목록은_로그인_없이_items_no_store_모르는_유형은_400_자리_설정은_기본값() throws Exception {
        secured.perform(get("/api/ads").param("types", "WIDE_BANNER,TEXT_LINE")).andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.data.items").isArray());
        secured.perform(get("/api/ads").param("types", "HOTDEAL_TOP").param("board", "free").param("prefix", "1")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(0));
        secured.perform(get("/api/ads")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.fields.types").exists());
        secured.perform(get("/api/ads").param("types", "NOPE")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.fields.types").exists());

        jdbc.update("DELETE FROM ad_placement_settings");
        secured.perform(get("/api/ads/placements")).andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.data.postInline.enabled").value(true)).andExpect(jsonPath("$.data.postInline.adType").value("TEXT_LINE"))
                .andExpect(jsonPath("$.data.postInline.position").value("BETWEEN_CARDS")).andExpect(jsonPath("$.data.wings.left").value(true))
                .andExpect(jsonPath("$.data.wings.right").value(true)).andExpect(jsonPath("$.data.wings.screens.length()").value(4))
                .andExpect(jsonPath("$.data.wings.screens[0]").value("HOME")).andExpect(jsonPath("$.data.boardList.enabled").value(true))
                .andExpect(jsonPath("$.data.boardList.after").value(5));
    }
}
