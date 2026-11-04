package com.community.analytics.forget;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.community.analytics.controller.VisitForgetController;
import com.community.analytics.dto.VisitForget;
import com.community.analytics.exception.VisitForgetBusyException;
import com.community.analytics.exception.VisitForgetRateLimitedException;
import com.community.analytics.store.AnalyticsDatabase;
import com.community.analytics.store.VisitForgetStore;
import com.community.testsupport.CommerceTestDatabase;
import com.community.zconfiguration.crypto.BlindIndex;
import com.community.zconfiguration.crypto.SecretCipher;
import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.exception.GlobalExceptionHandler;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.id.SnowflakeIdGenerator;
import com.community.zconfiguration.message.Messages;
import com.community.zconfiguration.web.ClientIpResolver;
import com.community.zconfiguration.web.ProxyProperties;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.StaticMessageSource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * 방문 기록 삭제 요청 받기(공개 {@code POST /api/visits/forget} — site-analytics-contract 「2단계 — 정보주체 요청」) — 실제 PostgreSQL(analytics V4).
 * 202 모양 · 같은 해시 대기 중이면 같은 요청(서버 여러 대 동시에도 한 줄) · 대기 전체 상한 {@link VisitForgetStore#MAX_PENDING}(503 {@code VISIT_FORGET_BUSY}) ·
 * IP 빈도 제한 429 · 400 {@code fields.visitorId}.
 *
 * <p>요청 표는 크론 시험({@code VisitForgetCronTest})과 같이 쓴다 — 시험마다 넣은 줄을 지운다(남은 대기 줄이 2분 뒤 크론에 잡히지 않게).
 */
class VisitForgetRequestTest {

    static final BlindIndex INDEX = new BlindIndex(SecretCipher.generateKey());
    static final SnowflakeIdGenerator IDS = new SnowflakeIdGenerator(914);

    private static DataSource dataSource;
    private static JdbcTemplate jdbc;
    private static ExecutorService pool;
    private final Set<Long> mine = new HashSet<>();

    @BeforeAll
    static void database() {
        dataSource = CommerceTestDatabase.migrated("api", "analytics");
        jdbc = new JdbcTemplate(dataSource);
        pool = Executors.newFixedThreadPool(4);
    }

    @AfterAll
    static void stop() {
        pool.shutdownNow();
    }

    @AfterEach
    void cleanUp() {
        if (!mine.isEmpty()) {
            jdbc.update("DELETE FROM analytics_forget_requests WHERE id = ANY(?::bigint[])", "{" + String.join(",", mine.stream().map(String::valueOf).toList()) + "}");
        }
        jdbc.update("DELETE FROM analytics_forget_requests WHERE visitor_hash LIKE 'qa-cap-%'");
    }

    private static VisitForgetStore store() {
        return new VisitForgetStore(new AnalyticsDatabase(dataSource, false));
    }

    private VisitForgetService service() {
        return new VisitForgetService(store(), INDEX, IDS);
    }

    private VisitForget.Response submit(VisitForgetService svc, String visitorId) {
        VisitForget.Response r = svc.submit(new VisitForget.Request(visitorId));
        mine.add(Long.parseLong(r.requestId()));
        return r;
    }

    private static String hashOf(UUID id) {
        return INDEX.hash(VisitForgetService.VISITOR, id.toString());
    }

    private static long rowsFor(String hash) {
        return jdbc.queryForObject("SELECT count(*) FROM analytics_forget_requests WHERE visitor_hash = ?", Long.class, hash);
    }

    private static long pending() {
        return jdbc.queryForObject("SELECT count(*) FROM analytics_forget_requests WHERE status = 'PENDING'", Long.class);
    }

    // ── 접수 · 같은 해시 ────────────────────────────────────────────────────

    @Test
    void 접수하면_해시만_대기_줄로_남기고_같은_방문자는_같은_요청을_돌려준다() {
        VisitForgetService svc = service();
        UUID visitor = UUID.randomUUID();
        Instant before = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        VisitForget.Response first = submit(svc, visitor.toString());
        assertThat(first.requestId()).matches("\\d+");
        assertThat(first.receivedAt()).isAfterOrEqualTo(before.minusMillis(1));

        Long id = Long.parseLong(first.requestId());
        List<String> row = jdbc.query("SELECT visitor_hash, status, deleted_rows, processed_at FROM analytics_forget_requests WHERE id = ?",
                (rs, i) -> List.of(rs.getString(1), rs.getString(2), String.valueOf(rs.getObject(3)), String.valueOf(rs.getObject(4))), id).get(0);
        assertThat(row).containsExactly(hashOf(visitor), "PENDING", "null", "null");
        assertThat(row.get(0)).as("원본 UUID 를 남기지 않는다").doesNotContain(visitor.toString());

        // 대문자 · 앞뒤 공백도 같은 방문자
        VisitForget.Response again = submit(svc, "  " + visitor.toString().toUpperCase() + " ");
        assertThat(again).isEqualTo(first);
        assertThat(rowsFor(hashOf(visitor))).isEqualTo(1);
    }

    @Test
    void 서버_세_대가_같은_방문자를_동시에_받아도_한_줄_같은_요청() throws Exception {
        for (int round = 0; round < 10; round++) {
            String hash = "qa-cap-same-" + UUID.randomUUID();
            List<VisitForgetStore> stores = List.of(store(), store(), store());
            CyclicBarrier start = new CyclicBarrier(stores.size());
            List<Future<VisitForgetStore.Received>> done = new ArrayList<>();
            Instant at = Instant.now().truncatedTo(ChronoUnit.MICROS);
            for (VisitForgetStore s : stores) {
                done.add(pool.submit(() -> {
                    start.await(10, TimeUnit.SECONDS);
                    return s.submit(IDS.nextId(), hash, at);
                }));
            }
            Set<Long> ids = new HashSet<>();
            for (Future<VisitForgetStore.Received> f : done) {
                ids.add(f.get(30, TimeUnit.SECONDS).id());
            }
            assertThat(ids).as("round %d", round).hasSize(1);
            assertThat(rowsFor(hash)).isEqualTo(1);
        }
    }

    @Test
    void 처리가_끝난_방문자가_다시_요청하면_새_요청() {
        VisitForgetService svc = service();
        UUID visitor = UUID.randomUUID();
        VisitForget.Response first = submit(svc, visitor.toString());
        jdbc.update("UPDATE analytics_forget_requests SET status = 'DONE', visitor_hash = NULL, deleted_rows = 0, processed_at = now() WHERE id = ?",
                Long.parseLong(first.requestId()));
        VisitForget.Response second = submit(svc, visitor.toString());
        assertThat(second.requestId()).isNotEqualTo(first.requestId());
        assertThat(rowsFor(hashOf(visitor))).isEqualTo(1);
    }

    // ── 대기 전체 상한 ──────────────────────────────────────────────────────

    /** 대기 줄을 n 개 채운다(시험용 해시 'qa-cap-…' — 끝나면 지운다) */
    private static void fill(int n) {
        if (n <= 0) {
            return;
        }
        jdbc.update("INSERT INTO analytics_forget_requests (id, visitor_hash, received_at, status) "
                + "SELECT 9100000000000 + g + (extract(epoch FROM clock_timestamp()) * 1000)::bigint * 10000, 'qa-cap-' || g || '-' || md5(random()::text), now(), 'PENDING' "
                + "FROM generate_series(1, ?) g", n);
    }

    @Test
    void 대기가_상한에_닿으면_새_요청은_503_이지만_대기_중인_같은_해시는_그_요청() throws Exception {
        VisitForgetService svc = service();
        UUID waiting = UUID.randomUUID();
        VisitForget.Response waitingRequest = submit(svc, waiting.toString()); // 상한 전에 들어온 요청
        long now = pending();
        assertThat(now).isLessThan(VisitForgetStore.MAX_PENDING);
        fill((int) (VisitForgetStore.MAX_PENDING - now - 1));
        assertThat(pending()).isEqualTo(VisitForgetStore.MAX_PENDING - 1);

        // 상한 하나 앞 — 받는다(이제 꽉 참)
        UUID last = UUID.randomUUID();
        submit(svc, last.toString());
        assertThat(pending()).isEqualTo(VisitForgetStore.MAX_PENDING);

        // 꽉 참 — 새 방문자는 503 VISIT_FORGET_BUSY · Retry-After 300, 줄은 늘지 않는다
        UUID fresh = UUID.randomUUID();
        assertThatThrownBy(() -> svc.submit(new VisitForget.Request(fresh.toString()))).isInstanceOfSatisfying(VisitForgetBusyException.class, e -> {
            assertThat(e.getStatus().value()).isEqualTo(503);
            assertThat(e.getErrorCode()).isEqualTo("VISIT_FORGET_BUSY");
            assertThat(e.getRetryAfterSeconds()).isEqualTo(300);
        });
        assertThat(rowsFor(hashOf(fresh))).isZero();
        assertThat(store().submit(IDS.nextId(), "qa-cap-new", Instant.now())).isNull();
        assertThat(pending()).isEqualTo(VisitForgetStore.MAX_PENDING);

        // 꽉 차도 대기 중인 같은 해시는 그 요청을 돌려준다
        assertThat(submit(svc, waiting.toString())).isEqualTo(waitingRequest);
        String filler = jdbc.queryForObject("SELECT visitor_hash FROM analytics_forget_requests WHERE visitor_hash LIKE 'qa-cap-%' LIMIT 1", String.class);
        assertThat(store().submit(IDS.nextId(), filler, Instant.now())).isNotNull();

        // HTTP — 503 · Retry-After · 코드
        mvc(new VisitForgetRateLimiter()).perform(forget(fresh.toString(), "10.0.0.9")).andExpect(status().isServiceUnavailable())
                .andExpect(header().string("Retry-After", "300")).andExpect(jsonPath("$.error.code").value("VISIT_FORGET_BUSY"));

        // 대기가 하나 줄면 다시 받는다
        jdbc.update("DELETE FROM analytics_forget_requests WHERE id = (SELECT id FROM analytics_forget_requests WHERE visitor_hash LIKE 'qa-cap-%' LIMIT 1)");
        assertThat(submit(svc, fresh.toString()).requestId()).matches("\\d+");
    }

    // ── 입력 · 해시 키 ──────────────────────────────────────────────────────

    @Test
    void UUID_가_아니면_400_fields_visitorId_이고_해시_키가_없으면_503() {
        VisitForgetService svc = service();
        for (String bad : new String[] {null, "", "   ", "zz", "9b2b0b5e-6f3c-4c55-9d9a-0c2f2b1f0a1", "9b2b0b5e-6f3c-4c55-9d9a-0c2f2b1f0a111", "9b2b0b5e6f3c4c559d9a0c2f2b1f0a11",
                "gb2b0b5e-6f3c-4c55-9d9a-0c2f2b1f0a11"}) {
            assertThatThrownBy(() -> svc.submit(new VisitForget.Request(bad))).isInstanceOfSatisfying(ValidationException.class,
                    e -> assertThat(e.getFields()).containsOnlyKeys("visitorId"));
        }
        assertThatThrownBy(() -> svc.submit(null)).isInstanceOf(ValidationException.class);

        VisitForgetService noKey = new VisitForgetService(store(), new BlindIndex(""), IDS);
        assertThatThrownBy(() -> noKey.submit(new VisitForget.Request("zz"))).as("형식 검사가 먼저").isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> noKey.submit(new VisitForget.Request(UUID.randomUUID().toString()))).isInstanceOfSatisfying(CommonException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo("INDEX_KEY_MISSING"));
    }

    // ── IP 빈도 제한 ────────────────────────────────────────────────────────

    @Test
    void IP_하나가_1시간에_5번까지이고_여섯_번째는_429_와_남은_초() {
        VisitForgetRateLimiter limiter = new VisitForgetRateLimiter();
        long t0 = 1_000_000_000L;
        for (int i = 0; i < 5; i++) {
            limiter.acquire("1.2.3.4", t0 + i * 1000L);
        }
        assertThatThrownBy(() -> limiter.acquire("1.2.3.4", t0 + 10_000)).isInstanceOfSatisfying(VisitForgetRateLimitedException.class, e -> {
            assertThat(e.getStatus().value()).isEqualTo(429);
            assertThat(e.getErrorCode()).isEqualTo("VISIT_FORGET_RATE_LIMITED");
            assertThat(e.getRetryAfterSeconds()).isEqualTo(3590);
        });
        limiter.acquire("5.6.7.8", t0 + 10_000); // 다른 IP 는 따로
        assertThatThrownBy(() -> limiter.acquire("1.2.3.4", t0 + 3_599_999)).isInstanceOf(VisitForgetRateLimitedException.class);
        limiter.acquire("1.2.3.4", t0 + 3_600_000); // 첫 요청이 창 밖으로 — 하나 비었다
        assertThatThrownBy(() -> limiter.acquire("1.2.3.4", t0 + 3_600_001)).isInstanceOf(VisitForgetRateLimitedException.class);
        limiter.acquire("1.2.3.4", t0 + 3_601_000);
    }

    // ── HTTP ────────────────────────────────────────────────────────────────

    private MockMvc mvc(VisitForgetRateLimiter limiter) {
        Messages messages = new Messages(new StaticMessageSource(), new StandardEnvironment(), "messages");
        return MockMvcBuilders.standaloneSetup(new VisitForgetController(service(), limiter, new ClientIpResolver(new ProxyProperties(0))))
                .setControllerAdvice(new GlobalExceptionHandler(messages)).build();
    }

    private static MockHttpServletRequestBuilder forget(String visitorId, String ip) {
        return post("/api/visits/forget").contentType(MediaType.APPLICATION_JSON).content(visitorId == null ? "{}" : "{\"visitorId\":\"" + visitorId + "\"}")
                .with(r -> {
                    r.setRemoteAddr(ip);
                    return r;
                });
    }

    @Test
    void HTTP_202_모양과_no_store_400_fields_visitorId() throws Exception {
        MockMvc mvc = mvc(new VisitForgetRateLimiter());
        UUID visitor = UUID.randomUUID();
        String body = mvc.perform(forget(visitor.toString(), "10.0.0.1")).andExpect(status().isAccepted()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.success").value(true)).andExpect(jsonPath("$.data.requestId").isString()).andExpect(jsonPath("$.data.receivedAt").isString())
                .andReturn().getResponse().getContentAsString();
        String id = body.replaceAll(".*\"requestId\":\"(\\d+)\".*", "$1");
        mine.add(Long.parseLong(id));
        mvc.perform(forget(visitor.toString(), "10.0.0.1")).andExpect(status().isAccepted()).andExpect(jsonPath("$.data.requestId").value(id));

        mvc.perform(forget("not-a-uuid", "10.0.0.1")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("VALIDATION"))
                .andExpect(jsonPath("$.error.fields.visitorId").exists());
        mvc.perform(forget(null, "10.0.0.1")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.fields.visitorId").exists());
        mvc.perform(post("/api/visits/forget").with(r -> {
            r.setRemoteAddr("10.0.0.1");
            return r;
        })).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.fields.visitorId").exists()); // 본문 없음
    }

    @Test
    void HTTP_형식이_틀린_요청도_횟수에_들어_여섯_번째는_429_Retry_After() throws Exception {
        MockMvc mvc = mvc(new VisitForgetRateLimiter());
        for (int i = 0; i < 5; i++) {
            mvc.perform(forget("bad-" + i, "10.0.0.2")).andExpect(status().isBadRequest());
        }
        mvc.perform(forget(UUID.randomUUID().toString(), "10.0.0.2")).andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", org.hamcrest.Matchers.matchesPattern("\\d+"))).andExpect(jsonPath("$.error.code").value("VISIT_FORGET_RATE_LIMITED"));
        // 다른 IP 는 받는다
        String body = mvc.perform(forget(UUID.randomUUID().toString(), "10.0.0.3")).andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();
        mine.add(Long.parseLong(body.replaceAll(".*\"requestId\":\"(\\d+)\".*", "$1")));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM analytics_forget_requests WHERE received_at > ?", Long.class,
                Timestamp.from(Instant.now().minusSeconds(60)))).isGreaterThanOrEqualTo(1);
    }
}
