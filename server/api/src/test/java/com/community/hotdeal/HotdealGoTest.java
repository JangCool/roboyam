package com.community.hotdeal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.community.hotdeal.service.DealGoService;
import com.community.hotdeal.service.DealGoService.Visitor;
import com.community.installation.service.InstallationState;
import jakarta.servlet.Filter;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * D5 구매하기 {@code /api/deals/{postId}/go} — MEMBER 저장 링크 그대로 · SITE 개인 제휴 파라미터 떼고 TEMPLATE·QUERY 변환 · 사이트 제휴 몰이 아니면 뗀 주소 ·
 * 종료·기간 종료는 원래 링크(집계 없음) · 차단·숨김·없는 글은 글로 · 집계는 하루 같은 방문자 1번 · 봇·빈 User-Agent 제외 · 로그인 없이 302 + no-store + no-referrer.
 */
class HotdealGoTest extends HotdealApiTestBase {

    private static final String UA = "Mozilla/5.0 (Windows NT 10.0) Chrome/130";

    @Autowired
    private DealGoService go;
    @Autowired
    private WebApplicationContext context;
    @Autowired
    private InstallationState installation;
    @Autowired
    @Qualifier("springSecurityFilterChain")
    private Filter securityFilterChain;

    private long clicks(long postId) {
        return jdbc.queryForObject("SELECT click_count FROM post_deals WHERE post_id = ?", Long.class, postId);
    }

    @Test
    void MEMBER_모드는_저장한_링크_그대로() {
        String link = url("shop.example.com") + "?affid=me&color=red";
        long id = writeId(member(), deal(link));
        assertThat(go.target(Long.toString(id), new Visitor(null, "10.0.0.1", UA))).isEqualTo(link);
        assertThat(clicks(id)).isEqualTo(1);
    }

    @Test
    void SITE_모드는_개인_제휴_파라미터를_떼고_사이트_제휴_몰의_변환으로() {
        String tmplMall = "tm" + uniq() + ".example";
        String queryMall = "qm" + uniq() + ".example";
        siteStore(tmplMall, "TEMPLATE", "https://partners.example/r?u={url}", null);
        siteStore(queryMall, "QUERY", null, "aff=roboyam");
        String tmplLink = "https://" + tmplMall + "/p/1?affid=writer&size=L";
        String queryLink = "https://" + queryMall + "/p/2?aff=writer&utm_source=x";
        String plainLink = "https://plain" + uniq() + ".example/p/3?utm_medium=y&id=9";
        long tmpl = writeId(member(), deal(tmplLink));
        long query = writeId(member(), deal(queryLink));
        long plain = writeId(member(), deal(plainLink));
        setting("affiliate_mode", "SITE");

        assertThat(go.target(Long.toString(tmpl), new Visitor(null, "10.0.0.2", UA)))
                .isEqualTo("https://partners.example/r?u=" + com.community.hotdeal.service.SiteAffiliateLinker.encodeUriComponent("https://" + tmplMall + "/p/1?size=L"));
        assertThat(go.target(Long.toString(query), new Visitor(null, "10.0.0.2", UA))).isEqualTo("https://" + queryMall + "/p/2?aff=roboyam");
        assertThat(go.target(Long.toString(plain), new Visitor(null, "10.0.0.2", UA))).as("사이트 제휴 몰이 아니면 뗀 주소").startsWith("https://plain").endsWith("/p/3?id=9");
        assertThat(dealRow(tmpl).get("product_url")).as("저장한 링크는 바꾸지 않는다").isEqualTo(tmplLink);
    }

    @Test
    void 종료와_기간_종료는_원래_링크이고_세지_않는다() {
        String link = url("shop.example.com") + "?affid=me";
        long ended = writeId(member(), withStock(deal(link), "SOLD_OUT"));
        setting("affiliate_mode", "SITE");
        assertThat(go.target(Long.toString(ended), new Visitor(null, "10.0.0.3", UA))).isEqualTo(link);
        assertThat(clicks(ended)).isZero();

        long expired = writeId(member(), deal(url("shop.example.com")));
        // SQL 로 남김: 과거·미래 시각을 바로 세운다 — 엔티티 팩터리·메서드는 지금 시각으로만 쓴다
        jdbc.update("UPDATE post_deals SET created_at = ? WHERE post_id = ?", Timestamp.from(Instant.now().minus(Duration.ofDays(31))), expired);
        assertThat(go.target(Long.toString(expired), new Visitor(null, "10.0.0.3", UA))).isEqualTo(dealRow(expired).get("product_url"));
        assertThat(clicks(expired)).isZero();
    }

    @Test
    void 차단_숨김_없는_글은_글로_보낸다() {
        String host = "gb" + uniq() + ".example";
        long blocked = writeId(member(), deal(url(host)));
        domain(host, "BLOCK", false);
        assertThat(go.target(Long.toString(blocked), new Visitor(null, "10.0.0.4", UA))).isEqualTo("/posts/" + blocked);

        String finalBlocked = "gf" + uniq() + ".example";
        long viaChain = writeId(member(), deal(chain(url("hop" + uniq() + ".example"), "hop.example", "ok-final.example")));
        // SQL 로 남김: 핫딜 칸을 바로 세운다 — 엔티티는 딜 칸 전체(DealValues)로만 만들고 바꾼다(다음 단계에서 픽스처로)
        jdbc.update("UPDATE post_deals SET final_host = ? WHERE post_id = ?", finalBlocked, viaChain);
        domain(finalBlocked, "BLOCK", false);
        assertThat(go.target(Long.toString(viaChain), new Visitor(null, "10.0.0.4", UA))).as("최종 호스트 차단").isEqualTo("/posts/" + viaChain);

        long hidden = writeId(member(), deal(url("shop.example.com")));
        boardFixture.staffHide(hidden);
        assertThat(go.target(Long.toString(hidden), new Visitor(null, "10.0.0.4", UA))).isEqualTo("/posts/" + hidden);

        long deleted = writeId(member(), deal(url("shop.example.com")));
        boardFixture.delete(deleted);
        assertThat(go.target(Long.toString(deleted), new Visitor(null, "10.0.0.4", UA))).isEqualTo("/posts/" + deleted);
        assertThat(go.target("123456789", new Visitor(null, "10.0.0.4", UA))).isEqualTo("/posts/123456789");
        assertThat(go.target("abc", new Visitor(null, "10.0.0.4", UA))).isEqualTo("/");
        assertThat(clicks(blocked) + clicks(hidden)).isZero();
    }

    @Test
    void 집계는_하루_같은_방문자_같은_글_한_번_봇은_세지_않는다() {
        long id = writeId(member(), deal(url("shop.example.com")));
        long viewer = member();
        go.target(Long.toString(id), new Visitor(viewer, "10.0.0.5", UA));
        go.target(Long.toString(id), new Visitor(viewer, "10.9.9.9", UA)); // 같은 회원 — IP 가 달라도 한 번
        assertThat(clicks(id)).isEqualTo(1);
        go.target(Long.toString(id), new Visitor(null, "10.0.0.6", UA));
        go.target(Long.toString(id), new Visitor(null, "10.0.0.6", UA));
        assertThat(clicks(id)).isEqualTo(2);
        go.target(Long.toString(id), new Visitor(null, "10.0.0.7", "Googlebot/2.1"));
        go.target(Long.toString(id), new Visitor(null, "10.0.0.8", ""));
        go.target(Long.toString(id), new Visitor(null, "10.0.0.9", "curl/8.0"));
        assertThat(clicks(id)).isEqualTo(2);

        clock.set(clock.instant().plus(Duration.ofDays(1)));
        go.target(Long.toString(id), new Visitor(viewer, "10.0.0.5", UA));
        assertThat(clicks(id)).as("다음 날(한국 날짜)은 다시 센다").isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM deal_click_daily WHERE post_id = ?", Integer.class, id)).isEqualTo(3);
    }

    @Test
    void 로그인_없이_302_no_store_no_referrer() throws Exception {
        installation.markCompleted();
        MockMvc secured = MockMvcBuilders.webAppContextSetup(context).addFilters(securityFilterChain).build();
        String link = url("shop.example.com");
        long id = writeId(member(), deal(link));
        secured.perform(get("/api/deals/" + id + "/go").header("User-Agent", UA)).andExpect(status().isFound()).andExpect(header().string("Location", link))
                .andExpect(header().string("Cache-Control", "no-store")).andExpect(header().string("Referrer-Policy", "no-referrer"));
        secured.perform(get("/api/deals/link-check").param("url", link).param("board", board)).andExpect(status().isUnauthorized());
        secured.perform(get("/api/deals/prefill").param("from", Long.toString(id))).andExpect(status().isUnauthorized());
        secured.perform(get("/api/posts/" + id + "/deal/same")).andExpect(status().isOk());
    }
}
