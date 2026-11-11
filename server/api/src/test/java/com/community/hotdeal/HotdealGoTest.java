package com.community.hotdeal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.community.hotdeal.service.DealGoService;
import com.community.hotdeal.service.DealGoService.Visitor;
import com.community.installation.service.InstallationState;
import com.community.zconfiguration.time.ServiceTime;
import jakarta.servlet.Filter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * D5 구매하기 {@code /api/deals/{postId}/go} — MEMBER 저장 링크 그대로 · SITE 개인 제휴 파라미터 떼고 TEMPLATE·QUERY 변환 · 사이트 제휴 몰이 아니면 뗀 주소 ·
 * 종료·기간 종료는 원래 링크(집계 없음) · 차단·숨김·없는 글은 글로 · 집계는 하루 같은 방문자 1번 · 봇·빈 User-Agent 제외 · 로그인 없이 302 + no-store + no-referrer ·
 * 그 날 판정 조각이 없으면(V193 하루 파티션 — 23514) 업무 트랜잭션 안에서 세이브포인트로 그 넣기만 되돌리고 조각을 만들어 다시 넣는다(트랜잭션은 이어서 쓸 수 있다).
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

    /** 그 날(한국 날짜) 클릭 판정 조각 이름 — deal_click_daily_YYYYMMDD(V193·R__batch_43) */
    private static String clickPartition(LocalDate day) {
        return "deal_click_daily_" + day.format(DateTimeFormatter.BASIC_ISO_DATE);
    }

    /** 조각이 없는 날을 만든다 — 먼 미래 날이라 다른 시험의 줄이 없다. 시험 뒤에도 같은 것으로 지운다 */
    private void dropPartition(String name) {
        // SQL 로 남김: 파티션 조각 DDL — 엔티티·저장소가 없는 DB 구조다
        jdbc.execute("DROP TABLE IF EXISTS " + name);
    }

    /** 이 문맥(트랜잭션 안이면 그 연결)에서 본 조각 유무 */
    private boolean partitionExists(String name) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT to_regclass(?) IS NOT NULL", Boolean.class, name));
    }

    /** 트랜잭션 밖의 다른 연결에서 본 조각 유무 — 커밋 전의 CREATE TABLE 은 보이지 않는다(to_regclass 는 잠금을 잡지 않아 기다리지 않는다) */
    private boolean partitionVisibleElsewhere(String name) {
        try (Connection other = dataSource.getConnection(); PreparedStatement ps = other.prepareStatement("SELECT to_regclass(?) IS NOT NULL")) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getBoolean(1);
            }
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    private int clickMarks(long postId, LocalDate day) {
        return jdbc.queryForObject("SELECT count(*) FROM deal_click_daily WHERE post_id = ? AND day = ?", Integer.class, postId, java.sql.Date.valueOf(day));
    }

    /** 시계를 먼 미래로 옮기고 그 딜을 그때 올린 것으로 — 진행 중(기간 종료 아님)으로 남긴다. 돌려주는 값 = 그 날(한국 날짜) */
    private LocalDate farFuture(long postId, long days) {
        clock.set(clock.instant().plus(Duration.ofDays(days)));
        // SQL 로 남김: 과거·미래 시각을 바로 세운다 — 엔티티 팩터리·메서드는 지금 시각으로만 쓴다
        jdbc.update("UPDATE post_deals SET created_at = ? WHERE post_id = ?", Timestamp.from(clock.instant()), postId);
        return ServiceTime.dateOf(clock.instant());
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

    @Test
    void 그_날_조각이_없어도_구매하기는_302_이고_조각을_만들어_한_번_센다() throws Exception {
        installation.markCompleted();
        MockMvc secured = MockMvcBuilders.webAppContextSetup(context).addFilters(securityFilterChain).build();
        String link = url("shop.example.com");
        long id = writeId(member(), deal(link));
        LocalDate day = farFuture(id, 500);
        String part = clickPartition(day);
        dropPartition(part);
        try {
            assertThat(partitionExists(part)).as("시작 — 그 날 조각이 없다").isFalse();

            secured.perform(get("/api/deals/" + id + "/go").header("User-Agent", UA)).andExpect(status().isFound()).andExpect(header().string("Location", link));
            assertThat(partitionExists(part)).as("그 날 조각을 새로 만들었다").isTrue();
            assertThat(clickMarks(id, day)).as("판정 줄 1").isEqualTo(1);
            assertThat(clicks(id)).as("다시 넣은 뒤 같은 트랜잭션의 click_count + 1 도 커밋됐다").isEqualTo(1);

            secured.perform(get("/api/deals/" + id + "/go").header("User-Agent", UA)).andExpect(status().isFound()).andExpect(header().string("Location", link));
            assertThat(clickMarks(id, day)).isEqualTo(1);
            assertThat(clicks(id)).as("같은 방문자는 그 날 한 번").isEqualTo(1);
        } finally {
            dropPartition(part);
        }
    }

    @Test
    void 조각_만들기와_다시_넣기는_업무_트랜잭션_안이고_그_트랜잭션은_이어서_쓸_수_있다() {
        String link = url("shop.example.com");
        long id = writeId(member(), deal(link));
        LocalDate day = farFuture(id, 501);
        String part = clickPartition(day);
        dropPartition(part);
        Visitor visitor = new Visitor(null, "10.0.1.1", UA);
        try {
            assertThat(partitionVisibleElsewhere(part)).isFalse();
            // 바깥 트랜잭션에 go.target(@Transactional REQUIRED)이 참여한다 — 커밋 전에 같은 연결·다른 연결에서 각각 본다
            String target = tx.execute(status -> {
                String to = go.target(Long.toString(id), visitor);
                assertThat(status.isRollbackOnly()).as("롤백 전용이 되지 않았다").isFalse();
                // 「current transaction is aborted」 라면 아래 질의가 실패한다 — 세이브포인트로 실패한 넣기만 되돌렸다
                assertThat(clicks(id)).as("같은 트랜잭션에서 click_count + 1 이 이미 보인다").isEqualTo(1);
                assertThat(clickMarks(id, day)).isEqualTo(1);
                assertThat(partitionExists(part)).as("이 트랜잭션에서는 조각이 보인다").isTrue();
                assertThat(partitionVisibleElsewhere(part)).as("조각 만들기도 업무 트랜잭션 안(같은 연결) — 커밋 전 다른 연결엔 안 보인다").isFalse();
                return to;
            });
            assertThat(target).isEqualTo(link);
            assertThat(partitionVisibleElsewhere(part)).as("커밋 뒤에는 보인다").isTrue();
            assertThat(clicks(id)).isEqualTo(1);
            assertThat(clickMarks(id, day)).isEqualTo(1);

            assertThat(go.target(Long.toString(id), visitor)).isEqualTo(link);
            assertThat(clicks(id)).as("같은 방문자는 그 날 한 번").isEqualTo(1);
            assertThat(clickMarks(id, day)).isEqualTo(1);
        } finally {
            dropPartition(part);
        }
    }
}
