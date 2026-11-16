package com.community.pointgame;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.community.auth.dto.AuthUser;
import com.community.commerce.CommerceApiTestBase;
import com.community.permission.entity.MemberPermission;
import com.community.point.entity.PointKind;
import com.community.point.entity.PointLotKind;
import com.community.point.entity.PointReason;
import com.community.point.repository.master.PointLedgerWriter;
import com.community.point.service.PointService;
import com.community.pointgame.service.MemberPointGameService;
import com.community.pointgame.service.RouletteTable;
import com.community.role.entity.MemberRoleIds;
import com.community.testsupport.PointInvariants;
import com.community.user.entity.Role;
import com.community.zconfiguration.time.ServiceTime;
import com.jayway.jsonpath.JsonPath;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * 포인트 게임 회원 API(docs/features/point-games-contract.md — 커머스 18절, 규칙마다 시험) — 공개 사이트 앱 문맥 + 실제 PostgreSQL + MockMvc(로그인 주체를 요청에 싣는다).
 *
 * <ul>
 *   <li>룰렛: 모양 · 적립 포인트만 빠짐(충전 그대로) · 적립 부족 409(충전이 많아도) · 하루 5회 409(동시 요청 포함) · 한국 0시에 다시 참 · 당첨은 무상 묶음 · 불변식.</li>
 *   <li>로또: 자동 1·5세트 값·저장·서로 다른 세트 · 반자동 검사 400 · 적립만 · 보관 50 초과 409(잔액보다 먼저, 동시 요청 포함) · 지우기 204/204/남의 것 404(포인트 안 돌려줌) · 당첨 확인 링크.</li>
 *   <li>공통 거절: 사업자 403 {@code BUSINESS_ACCOUNT_RESTRICTED}(읽기도) · 정지 403 {@code ACCOUNT_RESTRICTED} · 동결 403 {@code MEMBER_RESTRICTED} · 로그인 없으면 401.</li>
 * </ul>
 */
class PointGameHttpTest extends CommerceApiTestBase {

    private static final String BASE = "/api/me/point-games";

    @Autowired
    private WebApplicationContext webContext;
    @Autowired
    private PointService points;
    @Autowired
    private PointLedgerWriter writer;
    @Autowired
    private MemberPointGameService games;

    private MockMvc mvc;
    private long operator;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(webContext).build();
        operator = newOperator();
        seedMemberRoles();
    }

    // ── 룰렛 ────────────────────────────────────────────────────────────────

    @Test
    void 룰렛_상태는_값_횟수_상품표_적립_잔액_다시_차는_시각_모양이다() throws Exception {
        long member = member();
        grant(member, 120);
        chargePaid(member, 900);
        Instant reset = ServiceTime.today(clock).plusDays(1).atStartOfDay(ServiceTime.ZONE).toInstant();

        mvc.perform(as(member, get(BASE + "/roulette"))).andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.data.cost").value(50))
                .andExpect(jsonPath("$.data.dailyLimit").value(5))
                .andExpect(jsonPath("$.data.usedToday").value(0))
                .andExpect(jsonPath("$.data.prizes.length()").value(7))
                .andExpect(jsonPath("$.data.prizes[0].key").value("MISS"))
                .andExpect(jsonPath("$.data.prizes[0].points").value(0))
                .andExpect(jsonPath("$.data.prizes[0].chanceBp").value(7000))
                .andExpect(jsonPath("$.data.prizes[6].key").value("P500"))
                .andExpect(jsonPath("$.data.freeBalance").value(120))
                .andExpect(jsonPath("$.data.resetsAt").value(reset.toString()));
    }

    @Test
    void 돌리면_적립_포인트만_50_빠지고_당첨은_적립으로_들어오며_충전_포인트는_그대로다() throws Exception {
        long member = member();
        grant(member, 100);
        chargePaid(member, 500);

        String body = body(mvc.perform(as(member, post(BASE + "/roulette/spins"))).andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store")));
        String spinId = JsonPath.read(body, "$.data.spinId");
        String prizeKey = JsonPath.read(body, "$.data.prizeKey");
        int won = JsonPath.read(body, "$.data.won");
        RouletteTable.Prize prize = RouletteTable.PRIZES.stream().filter(p -> p.key().equals(prizeKey)).findFirst().orElseThrow();
        assertThat(won).isEqualTo(prize.points());
        assertThat(((Number) JsonPath.read(body, "$.data.freeBalance")).longValue()).isEqualTo(50L + won);
        assertThat((int) JsonPath.read(body, "$.data.usedToday")).isEqualTo(1);

        assertThat(free(member)).isEqualTo(50L + won);
        assertThat(paid(member)).as("충전 포인트는 절대 빠지지 않는다").isEqualTo(500);
        assertThat(jdbc.queryForObject("SELECT paid_delta FROM point_ledger WHERE reason = 'ROULETTE_SPIN' AND ref_id = ?", Long.class, Long.parseLong(spinId))).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM point_game_roulette_spins WHERE id = ? AND user_id = ? AND prize_key = ? AND won = ? AND cost = 50", Integer.class,
                Long.parseLong(spinId), member, prizeKey, won)).isEqualTo(1);
        if (won > 0) {
            assertThat(jdbc.queryForObject("SELECT t.kind FROM point_lots t JOIN point_ledger l ON l.id = t.source_ledger_id WHERE l.reason = 'ROULETTE_WIN' AND l.ref_id = ?",
                    String.class, Long.parseLong(spinId))).isEqualTo("FREE");
        }
        assertThat(points.earnedToday(member)).as("당첨은 하루 활동 한도에 들지 않는다").isZero();
        assertInvariants(member);
    }

    @Test
    void 적립_포인트가_모자라면_충전_포인트가_많아도_409_POINT_INSUFFICIENT() throws Exception {
        long member = member();
        grant(member, 30);
        chargePaid(member, 5_000);

        mvc.perform(as(member, post(BASE + "/roulette/spins"))).andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("POINT_INSUFFICIENT"));
        assertThat(free(member)).isEqualTo(30);
        assertThat(paid(member)).isEqualTo(5_000);
        assertThat(count("SELECT count(*) FROM point_game_roulette_spins WHERE user_id = ?", member)).isZero();
        assertInvariants(member);
    }

    @Test
    void 하루_5회를_넘으면_409_POINT_GAME_DAILY_LIMIT_이고_한국_0시에_다시_찬다() throws Exception {
        long member = member();
        grant(member, 1_000);
        for (int i = 1; i <= 5; i++) {
            mvc.perform(as(member, post(BASE + "/roulette/spins"))).andExpect(status().isOk()).andExpect(jsonPath("$.data.usedToday").value(i));
        }
        long before = free(member);
        mvc.perform(as(member, post(BASE + "/roulette/spins"))).andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("POINT_GAME_DAILY_LIMIT"))
                .andExpect(jsonPath("$.error.message").value("오늘 룰렛은 다 돌렸어요. 내일 다시 돌릴 수 있어요."));
        assertThat(free(member)).as("막힌 요청은 포인트를 빼지 않는다").isEqualTo(before);
        assertThat(count("SELECT count(*) FROM point_game_roulette_spins WHERE user_id = ?", member)).isEqualTo(5);
        mvc.perform(as(member, get(BASE + "/roulette"))).andExpect(jsonPath("$.data.usedToday").value(5));

        // 다음 한국 자정
        clock.set(ServiceTime.today(clock).plusDays(1).atStartOfDay(ServiceTime.ZONE).toInstant().plusSeconds(1));
        mvc.perform(as(member, get(BASE + "/roulette"))).andExpect(jsonPath("$.data.usedToday").value(0));
        mvc.perform(as(member, post(BASE + "/roulette/spins"))).andExpect(status().isOk()).andExpect(jsonPath("$.data.usedToday").value(1));
        assertInvariants(member);
    }

    @Test
    void 횟수_검사가_잔액_검사보다_먼저다() throws Exception {
        long member = member();
        grant(member, 250); // 다섯 번(꽝이면 0P 가 남는다)
        for (int i = 0; i < 5; i++) {
            mvc.perform(as(member, post(BASE + "/roulette/spins"))).andExpect(status().isOk());
        }
        // 남은 적립을 모두 쓴다(운영 차감)
        long left = free(member);
        if (left > 0) {
            tx.executeWithoutResult(s -> points.adjust(member, (int) -left, "시험 차감", operator));
        }
        mvc.perform(as(member, post(BASE + "/roulette/spins"))).andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("POINT_GAME_DAILY_LIMIT"));
    }

    @Test
    void 동시에_여러_번_눌러도_하루_5회를_넘지_않는다() throws Exception {
        long member = member();
        grant(member, 2_000);
        for (int i = 0; i < 3; i++) {
            mvc.perform(as(member, post(BASE + "/roulette/spins"))).andExpect(status().isOk());
        }
        List<Object> results = concurrently(6, () -> games.spin(member));
        long ok = results.stream().filter(r -> !(r instanceof Throwable)).count();
        assertThat(ok).as("남은 2회만 성공").isEqualTo(2);
        assertThat(results.stream().filter(r -> r instanceof Throwable).map(r -> r.getClass().getSimpleName()).distinct().toList())
                .containsOnly("PointGameDailyLimitException");
        assertThat(count("SELECT count(*) FROM point_game_roulette_spins WHERE user_id = ?", member)).isEqualTo(5);
        assertThat(count("SELECT count(*) FROM point_ledger WHERE user_id = ? AND reason = 'ROULETTE_SPIN'", member)).isEqualTo(5);
        assertInvariants(member);
    }

    // ── 로또 ────────────────────────────────────────────────────────────────

    @Test
    void 자동_1세트는_20P_5세트는_80P_이고_서로_다른_번호가_내_번호_맨_위에_남는다() throws Exception {
        long member = member();
        grant(member, 200);
        chargePaid(member, 300);

        String one = body(draw(member, "{\"sets\":1,\"mode\":\"AUTO\",\"fixed\":[],\"excluded\":[]}").andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store")).andExpect(jsonPath("$.data.draws.length()").value(1))
                .andExpect(jsonPath("$.data.draws[0].mode").value("AUTO")).andExpect(jsonPath("$.data.freeBalance").value(180)));
        List<Integer> numbers = JsonPath.read(one, "$.data.draws[0].numbers");
        assertThat(numbers).hasSize(6).isSorted().doesNotHaveDuplicates().allMatch(n -> n >= 1 && n <= 45);

        String five = body(draw(member, "{\"sets\":5,\"mode\":\"AUTO\"}").andExpect(status().isOk()).andExpect(jsonPath("$.data.draws.length()").value(5))
                .andExpect(jsonPath("$.data.freeBalance").value(100)));
        List<List<Integer>> sets = JsonPath.read(five, "$.data.draws[*].numbers");
        assertThat(new HashSet<>(sets)).as("한 번에 뽑은 세트끼리 겹치지 않는다").hasSize(5);
        assertThat(paid(member)).as("충전 포인트는 그대로").isEqualTo(300);

        String status = body(mvc.perform(as(member, get(BASE + "/lotto"))).andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.data.setCost").value(20)).andExpect(jsonPath("$.data.bundleSize").value(5)).andExpect(jsonPath("$.data.bundleCost").value(80))
                .andExpect(jsonPath("$.data.storageLimit").value(50)).andExpect(jsonPath("$.data.freeBalance").value(100))
                .andExpect(jsonPath("$.data.draws.length()").value(6)).andExpect(jsonPath("$.data.checkLink.providerName").isString())
                .andExpect(jsonPath("$.data.checkLink.url").value(org.hamcrest.Matchers.startsWith("https://"))));
        List<String> ids = JsonPath.read(status, "$.data.draws[*].id");
        List<String> fiveIds = JsonPath.read(five, "$.data.draws[*].id");
        assertThat(ids.subList(0, 5)).as("최신이 위 — 방금 뽑은 다섯 세트가 맨 위(응답과 같은 차례)").containsExactlyElementsOf(fiveIds);
        assertThat(ids.get(5)).isEqualTo(JsonPath.read(one, "$.data.draws[0].id"));
        assertThat(count("SELECT count(*) FROM point_ledger WHERE user_id = ? AND reason = 'LOTTO_DRAW' AND paid_delta = 0", member)).isEqualTo(2);
        assertInvariants(member);
    }

    @Test
    void 반자동은_고정을_넣고_제외를_빼며_조건이_틀리면_400_VALIDATION() throws Exception {
        long member = member();
        grant(member, 200);

        String body = body(draw(member, "{\"sets\":5,\"mode\":\"SEMI_AUTO\",\"fixed\":[7,33],\"excluded\":[1,2,3]}").andExpect(status().isOk()));
        List<List<Integer>> sets = JsonPath.read(body, "$.data.draws[*].numbers");
        assertThat(sets).allMatch(s -> s.contains(7) && s.contains(33) && !s.contains(1) && !s.contains(2) && !s.contains(3));
        assertThat(JsonPath.<List<String>>read(body, "$.data.draws[*].mode")).containsOnly("SEMI_AUTO");

        long before = free(member);
        draw(member, "{\"sets\":1,\"mode\":\"SEMI_AUTO\",\"fixed\":[],\"excluded\":[]}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION")).andExpect(jsonPath("$.error.fields.fixed").value("반자동은 고정 번호를 1개 이상 골라 주세요."));
        draw(member, "{\"sets\":3,\"mode\":\"AUTO\"}").andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.fields.sets").exists());
        draw(member, "{\"sets\":1,\"mode\":\"AUTO\",\"fixed\":[5]}").andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.fields.mode").exists());
        draw(member, "{\"sets\":1,\"mode\":\"SEMI_AUTO\",\"fixed\":[5],\"excluded\":[5]}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fields.excluded").value("같은 번호를 고정과 제외에 함께 둘 수 없어요."));
        draw(member, "{\"sets\":1,\"mode\":\"SEMI_AUTO\",\"fixed\":[1,2,3,4,5,6]}").andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.fields.fixed").exists());
        assertThat(free(member)).as("400 은 포인트를 빼지 않는다").isEqualTo(before);
    }

    @Test
    void 적립_포인트가_모자라면_충전_포인트가_있어도_409_POINT_INSUFFICIENT() throws Exception {
        long member = member();
        grant(member, 70);
        chargePaid(member, 1_000);
        draw(member, "{\"sets\":5,\"mode\":\"AUTO\"}").andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("POINT_INSUFFICIENT"));
        assertThat(free(member)).isEqualTo(70);
        assertThat(paid(member)).isEqualTo(1_000);
        assertThat(count("SELECT count(*) FROM point_game_lotto_draws WHERE user_id = ?", member)).isZero();
    }

    @Test
    void 보관이_50세트를_넘으면_409_LOTTO_STORAGE_FULL_이고_잔액_검사보다_먼저다() throws Exception {
        long member = member();
        grant(member, 500);
        storeDraws(member, 46);
        draw(member, "{\"sets\":5,\"mode\":\"AUTO\"}").andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("LOTTO_STORAGE_FULL"))
                .andExpect(jsonPath("$.error.message").value("내 번호는 50세트까지 남길 수 있어요. 지난 번호를 지운 뒤 뽑아 주세요."));
        assertThat(free(member)).isEqualTo(500);
        for (int i = 0; i < 4; i++) {
            draw(member, "{\"sets\":1,\"mode\":\"AUTO\"}").andExpect(status().isOk());
        }
        assertThat(count("SELECT count(*) FROM point_game_lotto_draws WHERE user_id = ?", member)).isEqualTo(50);
        draw(member, "{\"sets\":1,\"mode\":\"AUTO\"}").andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("LOTTO_STORAGE_FULL"));
        mvc.perform(as(member, get(BASE + "/lotto"))).andExpect(jsonPath("$.data.draws.length()").value(50));

        long broke = member(); // 0P · 가득
        storeDraws(broke, 50);
        draw(broke, "{\"sets\":1,\"mode\":\"AUTO\"}").andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("LOTTO_STORAGE_FULL"));
        assertInvariants(member);
    }

    @Test
    void 동시에_뽑아도_보관_50세트를_넘지_않는다() throws Exception {
        long member = member();
        grant(member, 500);
        storeDraws(member, 49);
        List<Object> results = concurrently(4, () -> games.draw(member, new com.community.pointgame.dto.request.LottoDrawRequest(1, "AUTO", List.of(), List.of())));
        assertThat(results.stream().filter(r -> !(r instanceof Throwable)).count()).isEqualTo(1);
        assertThat(results.stream().filter(r -> r instanceof Throwable).map(r -> r.getClass().getSimpleName()).distinct().toList())
                .containsOnly("LottoStorageFullException");
        assertThat(count("SELECT count(*) FROM point_game_lotto_draws WHERE user_id = ?", member)).isEqualTo(50);
        assertThat(free(member)).isEqualTo(480);
        assertInvariants(member);
    }

    @Test
    void 내_번호_지우기는_204_다시_지워도_204_남의_것은_404_이고_포인트는_돌려주지_않는다() throws Exception {
        long member = member();
        long other = member();
        grant(member, 100);
        String body = body(draw(member, "{\"sets\":1,\"mode\":\"AUTO\"}").andExpect(status().isOk()));
        String id = JsonPath.read(body, "$.data.draws[0].id");

        mvc.perform(as(other, delete(BASE + "/lotto/draws/" + id))).andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value("LOTTO_DRAW_NOT_FOUND"));
        assertThat(count("SELECT count(*) FROM point_game_lotto_draws WHERE id = ?", Long.parseLong(id))).isEqualTo(1);
        mvc.perform(as(member, delete(BASE + "/lotto/draws/" + id))).andExpect(status().isNoContent());
        mvc.perform(as(member, delete(BASE + "/lotto/draws/" + id))).andExpect(status().isNoContent());
        mvc.perform(as(member, delete(BASE + "/lotto/draws/not-a-number"))).andExpect(status().isNotFound());
        assertThat(count("SELECT count(*) FROM point_game_lotto_draws WHERE id = ?", Long.parseLong(id))).isZero();
        assertThat(free(member)).as("쓴 포인트는 돌려주지 않는다").isEqualTo(80);
        assertInvariants(member);
    }

    // ── 공통 거절 ───────────────────────────────────────────────────────────

    @Test
    void 사업자는_403_BUSINESS_ACCOUNT_RESTRICTED_정지는_403_ACCOUNT_RESTRICTED_동결은_403_MEMBER_RESTRICTED_로그인_없으면_401() throws Exception {
        long business = businessMember();
        grant(business, 500);
        mvc.perform(as(business, get(BASE + "/roulette"))).andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("BUSINESS_ACCOUNT_RESTRICTED"));
        mvc.perform(as(business, get(BASE + "/lotto"))).andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("BUSINESS_ACCOUNT_RESTRICTED"));
        mvc.perform(as(business, post(BASE + "/roulette/spins"))).andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("BUSINESS_ACCOUNT_RESTRICTED"));
        draw(business, "{\"sets\":1,\"mode\":\"AUTO\"}").andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("BUSINESS_ACCOUNT_RESTRICTED"));

        long suspended = member();
        grant(suspended, 500);
        // SQL 로 남김: 회원 상태(정지·차단·탈퇴)를 그대로 두는 도메인 메서드가 없다
        jdbc.update("UPDATE users SET status = 'SUSPENDED' WHERE id = ?", suspended);
        mvc.perform(as(suspended, post(BASE + "/roulette/spins"))).andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("ACCOUNT_RESTRICTED"));
        draw(suspended, "{\"sets\":1,\"mode\":\"AUTO\"}").andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("ACCOUNT_RESTRICTED"));
        mvc.perform(as(suspended, get(BASE + "/roulette"))).andExpect(status().isOk());

        long frozen = member();
        grant(frozen, 500);
        // SQL 로 남김: 과거 시각을 바로 세운다 — 엔티티 팩터리는 지금 시각으로만 쓴다
        jdbc.update("INSERT INTO member_restrictions (id, user_id, type, reason, starts_at, source, auto_rule, created_at) VALUES (?, ?, 'POINT_FREEZE', '시험 동결', ?, 'AUTO', "
                + "'POINT_TEST', ?)", ids.nextId(), frozen, Timestamp.from(clock.instant().minusSeconds(60)), Timestamp.from(clock.instant()));
        mvc.perform(as(frozen, post(BASE + "/roulette/spins"))).andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("MEMBER_RESTRICTED"));
        draw(frozen, "{\"sets\":1,\"mode\":\"AUTO\"}").andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("MEMBER_RESTRICTED"));

        for (long m : new long[] {business, suspended, frozen}) {
            assertThat(free(m)).isEqualTo(500);
            assertThat(count("SELECT count(*) FROM point_game_roulette_spins WHERE user_id = ?", m)).isZero();
            assertThat(count("SELECT count(*) FROM point_game_lotto_draws WHERE user_id = ?", m)).isZero();
        }

        mvc.perform(get(BASE + "/roulette")).andExpect(status().isUnauthorized());
        mvc.perform(post(BASE + "/roulette/spins")).andExpect(status().isUnauthorized());
        mvc.perform(get(BASE + "/lotto")).andExpect(status().isUnauthorized());
        mvc.perform(post(BASE + "/lotto/draws").contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isUnauthorized());
        mvc.perform(delete(BASE + "/lotto/draws/1")).andExpect(status().isUnauthorized());
    }

    // ── 도우미 ──────────────────────────────────────────────────────────────

    private static MockHttpServletRequestBuilder as(long memberId, MockHttpServletRequestBuilder request) {
        AuthUser user = new AuthUser(memberId, Role.USER);
        return request.principal(new UsernamePasswordAuthenticationToken(user, null, user.authorities())).header("Accept-Language", "ko");
    }

    private ResultActions draw(long member, String json) throws Exception {
        return mvc.perform(as(member, post(BASE + "/lotto/draws").contentType(MediaType.APPLICATION_JSON).content(json)));
    }

    private static String body(ResultActions actions) throws Exception {
        return actions.andReturn().getResponse().getContentAsString();
    }

    /** 이메일 인증을 마친 정상 회원 + 프로필 */
    private long member() {
        long id = newMember();
        memberFixture.verifyEmail(id);
        memberFixture.profile(id, nicknameOf(id));
        return id;
    }

    /** 사업자 회원(회원 역할) */
    private long businessMember() {
        long id = member();
        // SQL 로 남김: user_roles 엔티티에 팩터리가 없다
        jdbc.update("INSERT INTO user_roles (id, user_id, role_id) VALUES (?, ?, ?)", ids.nextId(), id, MemberRoleIds.BUSINESS);
        return id;
    }

    private static String nicknameOf(long id) {
        String base36 = Long.toString(id, 36);
        return "g" + base36.substring(Math.max(0, base36.length() - 11));
    }

    /** 운영과 같은 일반·사업자 회원 역할(시험 DB 에는 관리자 서버가 넣는 내장 역할이 없다 — PointShopApiTestBase 와 같다) */
    private void seedMemberRoles() {
        for (MemberPermission p : MemberPermission.values()) {
            // SQL 로 남김: ON CONFLICT upsert — 권한·내장 역할 사본을 없을 때만 넣는다
            jdbc.update("INSERT INTO permissions (code, axis, type_id, category, feature_ready) VALUES (?, 'MEMBER', ?, ?, ?) ON CONFLICT (code) DO NOTHING", p.code(),
                    p.typeId(), p.group().name(), p.featureReady());
        }
        role(MemberRoleIds.GENERAL, MemberRoleIds.GENERAL_DEFAULT_PERMISSIONS);
        role(MemberRoleIds.BUSINESS, List.of(MemberPermission.REPORT_CREATE));
    }

    private void role(String roleId, List<MemberPermission> granted) {
        // SQL 로 남김: ON CONFLICT upsert — 권한·내장 역할 사본을 없을 때만 넣는다
        int created = jdbc.update("INSERT INTO roles (role_id, role_type_id, axis, role_name, scope, grant_mode, builtin) VALUES (?, 'memberKind', 'MEMBER', ?, 'SITE', 'AUTO', true) "
                + "ON CONFLICT (role_id) DO NOTHING", roleId, roleId);
        if (created == 1) {
            for (MemberPermission p : granted) {
                // SQL 로 남김: ON CONFLICT upsert
                jdbc.update("INSERT INTO role_permissions (id, role_id, permission_code) VALUES (?, ?, ?) ON CONFLICT DO NOTHING", ids.nextId(), roleId, p.code());
            }
        }
    }

    /** 적립(무상) 포인트 — 운영 지급 한 줄 */
    private void grant(long userId, int amount) {
        tx.executeWithoutResult(s -> points.adjust(userId, amount, "시험 지급", operator));
    }

    /** 유상 충전을 흉내 낸다(충전 기능이 아직 없다) — 서비스와 같은 순서로 잔액 행을 잠그고 원장 줄(CHARGE, 유상 몫) + 유상 묶음 + 반영 */
    private void chargePaid(long userId, long amount) {
        tx.executeWithoutResult(s -> {
            Instant now = clock.instant();
            PointLedgerWriter.Balance balance = writer.lockBalance(userId, now);
            long line = ids.nextId();
            writer.insertEntry(line, userId, PointKind.EARN, PointReason.CHARGE.name(), (int) amount, 0, amount, balance.free() + balance.paid() + amount, null, null, null,
                    null, now);
            long lot = ids.nextId();
            writer.insertLot(new PointLedgerWriter.NewLot(lot, userId, PointLotKind.PAID, amount, null, PointReason.CHARGE.name(), null, null, line, null, now));
            writer.insertEntryLot(line, lot, amount);
            writer.saveBalance(userId, balance.free(), balance.paid() + amount, balance.debt(), now);
        });
    }

    /** 내 번호를 미리 채운다(뽑기 경로 없이 — 보관 한도 시험용) */
    private void storeDraws(long userId, int n) {
        for (int i = 0; i < n; i++) {
            // SQL 로 남김: 보관 한도 시험의 바탕 행 — 포인트 없이 한꺼번에 채운다
            jdbc.update("INSERT INTO point_game_lotto_draws (id, user_id, batch_id, mode, n1, n2, n3, n4, n5, n6, created_at) VALUES (?, ?, ?, 'AUTO', 1, 2, 3, 4, 5, 6, ?)",
                    ids.nextId(), userId, ids.nextId(), Timestamp.from(clock.instant().minusSeconds(3600L + i)));
        }
    }

    private long free(long userId) {
        List<Long> rows = jdbc.queryForList("SELECT free_balance FROM point_balances WHERE user_id = ?", Long.class, userId);
        return rows.isEmpty() ? 0 : rows.get(0);
    }

    private long paid(long userId) {
        List<Long> rows = jdbc.queryForList("SELECT paid_balance FROM point_balances WHERE user_id = ?", Long.class, userId);
        return rows.isEmpty() ? 0 : rows.get(0);
    }

    private int count(String sql, Object... args) {
        Integer n = jdbc.queryForObject(sql, Integer.class, args);
        return n == null ? 0 : n;
    }

    private void assertInvariants(long... members) {
        assertThat(PointInvariants.violations(jdbc, members)).as("포인트 불변식(commerce.md 5-5)").isEmpty();
    }

    /** 같은 일을 n 개 스레드에서 한꺼번에 — 결과(성공 값 또는 던진 예외) */
    private static List<Object> concurrently(int n, Callable<Object> work) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(n);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Object>> futures = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                futures.add(pool.submit(() -> {
                    start.await();
                    try {
                        return work.call();
                    } catch (Throwable t) {
                        return t;
                    }
                }));
            }
            start.countDown();
            List<Object> out = new ArrayList<>();
            for (Future<Object> f : futures) {
                out.add(f.get(60, TimeUnit.SECONDS));
            }
            return out;
        } finally {
            pool.shutdownNow();
        }
    }
}
