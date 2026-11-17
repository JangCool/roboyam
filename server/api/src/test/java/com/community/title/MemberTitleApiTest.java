package com.community.title;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.community.auth.dto.AuthUser;
import com.community.board.service.BoardCatalog;
import com.community.commerce.CommerceApiTestBase;
import com.community.title.dto.response.TitleBadgeView;
import com.community.title.service.TitleDisplayService;
import com.community.user.entity.Role;
import com.jayway.jsonpath.JsonPath;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * 회원 칭호 공개 API(member-title-contract 「구현 결정」 4·5·7) — 작성자 DTO {@code title}(글 목록·글 보기·댓글, 대표 칭호 규칙·숨김), 공개 프로필 {@code titles},
 * 도감·명예의 전당, 내 칭호(대표 고르기·축하 창·진행), 신청, 게시판 매니저 심사. 공개 사이트 앱 문맥 + 실제 PostgreSQL. 기본 칭호는 고치지 않고 시험용 칭호를 따로 만든다.
 */
class MemberTitleApiTest extends CommerceApiTestBase {

    @Autowired
    private BoardCatalog catalog;
    @Autowired
    private TitleDisplayService display;
    @Autowired
    private org.springframework.web.context.WebApplicationContext webContext;

    private MockMvc mvc;
    private long board;
    private String slug;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(webContext).build();
        board = ids.nextId();
        slug = "mt" + Long.toString(board, 36);
        boardFixture.board(board, slug, "칭호 " + slug.substring(slug.length() - 6), 950);
        catalog.invalidate();
    }

    private static MockHttpServletRequestBuilder as(long memberId, MockHttpServletRequestBuilder request) {
        AuthUser user = new AuthUser(memberId, Role.USER);
        return request.principal(new UsernamePasswordAuthenticationToken(user, null, user.authorities())).header("Accept-Language", "ko")
                .contentType(MediaType.APPLICATION_JSON);
    }

    private long member() {
        return member(null);
    }

    private long member(String gradeKey) {
        long id = newMember();
        memberFixture.verifyEmail(id);
        String b36 = Long.toString(id, 36);
        String nick = "t" + b36.substring(Math.max(0, b36.length() - 11));
        if (gradeKey == null) {
            memberFixture.profile(id, nick);
        } else {
            memberFixture.profile(id, nick, gradeKey);
        }
        return id;
    }

    private long titleId(String key) {
        return jdbc.queryForObject("SELECT id FROM titles WHERE title_key = ?", Long.class, key);
    }

    /** 받은 줄을 바로 둔다 — 수여·자동 판정은 관리자 서버의 일이다(공개 사이트 시험은 표시만 본다) */
    private long give(long member, String key, String period, String tier) {
        long id = ids.nextId();
        // SQL 로 남김: 칭호 지급은 관리자 서버(수여·배치)만 한다 — 표시 규칙만 보려고 줄을 바로 둔다
        jdbc.update("INSERT INTO member_titles (id, user_id, title_id, tier, period, source, granted_at) VALUES (?, ?, ?, ?, ?, 'AWARD', now())", id, member, titleId(key),
                tier, period == null ? "" : period);
        return id;
    }

    /** 시험용 칭호 — 신청형, 이 시험 게시판에 연결(매니저 심사), 조건 = 이 게시판 글 {@code posts} 편 */
    private String applyTitle(int posts, boolean dormant) {
        long id = ids.nextId();
        String key = "ta" + Long.toString(id, 36);
        String name = "신" + Long.toString(id % 1_000_000L, 36);
        // SQL 로 남김: 칭호 만들기는 관리자 서버 API — 공개 사이트 시험에서는 정의 줄만 둔다
        jdbc.update("INSERT INTO titles (id, title_key, name, icon, chip, method, reviewer, board_id, conditions, dormant_hide, sort_order) "
                        + "VALUES (?, ?, ?, 'bulb', 'PURPLE', 'APPLY', 'OPERATOR_AND_BOARD_MANAGER', ?, ?::jsonb, ?, 900)", id, key, name, board,
                "[{\"type\":\"POSTS\",\"value\":" + posts + ",\"boardId\":\"" + board + "\"}]", dormant);
        return key;
    }

    private long writePost(long author) {
        long id = ids.nextId();
        boardFixture.post(id, board, author, "칭호 시험 글", "본문");
        return id;
    }

    // ── 작성자 DTO ─────────────────────────────────────────────────────────

    @Test
    void 작성자_줄은_대표_칭호_하나를_글_목록_글_보기_댓글에서_같은_모양으로_준다_이달의_작가는_최근_달() throws Exception {
        long writer = member();
        give(writer, "monthly-writer", "2190-03", null);
        give(writer, "monthly-writer", "2190-11", null);
        long plain = member();
        long postId = writePost(writer);
        long plainPost = writePost(plain);
        boardFixture.comment(ids.nextId(), postId, board, writer, "댓글");

        String list = mvc.perform(get("/api/boards/" + slug + "/posts")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        Map<String, Object> title = JsonPath.<List<Map<String, Object>>>read(list, "$.data.items[?(@.id == '" + postId + "')].author.title").get(0);
        assertThat(title).containsEntry("key", "monthly-writer").containsEntry("label", "11월의 작가").containsEntry("labelEn", "November Writer")
                .containsEntry("icon", "pen").containsEntry("chip", "PURPLE");
        assertThat(JsonPath.<List<Object>>read(list, "$.data.items[?(@.id == '" + plainPost + "')].author.title")).containsOnlyNulls();

        mvc.perform(get("/api/posts/" + postId)).andExpect(status().isOk()).andExpect(jsonPath("$.data.author.title.label").value("11월의 작가"));
        mvc.perform(get("/api/posts/" + postId + "/comments")).andExpect(status().isOk()).andExpect(jsonPath("$.data.threads[0].author.title.key").value("monthly-writer"));
    }

    @Test
    void 기본_대표는_명예_작가_올해의_작가_이달의_작가_차례이고_고른_칭호가_이기며_안_보이기면_없다() throws Exception {
        long m = member();
        give(m, "monthly-writer", "2191-01", null);
        give(m, "yearly-writer", "2191", null);
        assertThat(display.representative(m).key()).isEqualTo("yearly-writer");
        give(m, "honorary-writer", null, null);
        give(m, "popular", null, "GOLD");
        assertThat(display.representative(m).key()).isEqualTo("honorary-writer");

        mvc.perform(as(m, put("/api/me/titles/representative")).content("{\"key\":\"popular\",\"hidden\":false}")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.representative.key").value("popular"));
        TitleBadgeView picked = display.representative(m);
        assertThat(picked.key()).isEqualTo("popular");
        assertThat(picked.tier()).isEqualTo("GOLD");

        mvc.perform(as(m, put("/api/me/titles/representative")).content("{\"key\":\"reporter\",\"hidden\":false}")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("TITLE_NOT_OWNED"));
        mvc.perform(as(m, put("/api/me/titles/representative")).content("{\"key\":null,\"hidden\":true}")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.hidden").value(true)).andExpect(jsonPath("$.data.representative").doesNotExist());
        assertThat(display.representative(m)).isNull();
        mvc.perform(as(m, put("/api/me/titles/representative")).content("{\"key\":null,\"hidden\":false}")).andExpect(status().isOk());
        assertThat(display.representative(m).key()).as("기본으로 돌아온다").isEqualTo("honorary-writer");
        // 작가 칭호가 아닌 것만 가지면 기본은 표시 없음
        long other = member();
        give(other, "popular", null, "BRONZE");
        assertThat(display.representative(other)).isNull();
    }

    @Test
    void 제재_기간과_휴면이면_대표_칭호를_숨기고_다시_쓰면_돌아온다() {
        long suspended = member();
        give(suspended, "monthly-writer", "2192-01", null);
        assertThat(display.representative(suspended)).isNotNull();
        // SQL 로 남김: 정지는 제재 서비스(관리자)가 회원 상태를 바꾼다 — 상태만 본다
        jdbc.update("UPDATE users SET status = 'SUSPENDED' WHERE id = ?", suspended);
        assertThat(display.representative(suspended)).isNull();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM member_titles WHERE user_id = ? AND revoked_at IS NULL", Integer.class, suspended)).as("회수 아님").isEqualTo(1);

        String key = applyTitle(1, true);
        long dormant = member();
        give(dormant, key, null, null);
        jdbc.update("INSERT INTO member_title_prefs (user_id, representative_title_id, hidden, updated_at) VALUES (?, ?, false, now())", dormant, titleId(key));
        long old = writePost(dormant);
        Instant sevenMonths = Instant.now().minus(220, ChronoUnit.DAYS);
        jdbc.update("UPDATE posts SET created_at = ? WHERE id = ?", Timestamp.from(sevenMonths), old);
        assertThat(display.representative(dormant)).as("6개월 활동 없음 — 숨김").isNull();
        writePost(dormant);
        assertThat(display.representative(dormant).key()).as("다시 쓰면 돌아온다").isEqualTo(key);
    }

    // ── 공개 프로필 ────────────────────────────────────────────────────────

    @Test
    void 공개_프로필은_칭호를_묶어_주고_비공개면_남에게는_대표_하나만_본인은_전부() throws Exception {
        long m = member();
        give(m, "monthly-writer", "2193-01", null);
        give(m, "monthly-writer", "2193-02", null);
        give(m, "popular", null, "SILVER");
        mvc.perform(get("/api/users/" + m + "/profile")).andExpect(status().isOk()).andExpect(jsonPath("$.data.titles.length()").value(2))
                .andExpect(jsonPath("$.data.titles[0].key").value("monthly-writer")).andExpect(jsonPath("$.data.titles[0].count").value(2))
                .andExpect(jsonPath("$.data.titles[0].periods[0]").value("2193-02")).andExpect(jsonPath("$.data.titles[1].tier").value("SILVER"));
        // SQL 로 남김: 공개 범위는 내 정보 API 가 바꾼다 — 칸만 본다
        jdbc.update("UPDATE profiles SET profile_visibility = 'PRIVATE' WHERE user_id = ?", m);
        mvc.perform(as(member(), get("/api/users/" + m + "/profile"))).andExpect(status().isOk()).andExpect(jsonPath("$.data.titles.length()").value(1))
                .andExpect(jsonPath("$.data.titles[0].label").value("2월의 작가"));
        mvc.perform(as(m, get("/api/users/" + m + "/profile"))).andExpect(status().isOk()).andExpect(jsonPath("$.data.titles.length()").value(2));
    }

    // ── 도감·명예의 전당 ────────────────────────────────────────────────────

    @Test
    void 도감은_켜진_칭호와_받는_방법_보유자_비율_단계를_주고_명예의_전당은_작가_칭호를_묶는다() throws Exception {
        long m = member();
        give(m, "honorary-writer", null, null);
        give(m, "yearly-writer", "2194", null);
        give(m, "monthly-writer", "2194-07", null);
        String body = mvc.perform(get("/api/titles").header("Accept-Language", "ko")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<String>>read(body, "$.data[*].key")).contains("monthly-writer", "popular", "attendance-100");
        assertThat(JsonPath.<List<Integer>>read(body, "$.data[?(@.key == 'honorary-writer')].holders").get(0)).isGreaterThanOrEqualTo(1);
        assertThat(JsonPath.<List<Double>>read(body, "$.data[?(@.key == 'honorary-writer')].ratio").get(0)).isBetween(0.0, 1.0);
        assertThat(JsonPath.<List<Object>>read(body, "$.data[?(@.key == 'popular')].tiers[*].threshold")).containsExactly(10, 30, 100);
        assertThat(JsonPath.<List<String>>read(body, "$.data[?(@.key == 'attendance-100')].howTo").get(0)).contains("출석 100일");

        String hall = mvc.perform(get("/api/hall-of-fame")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<String>>read(hall, "$.data.honorary[*].id")).contains(Long.toString(m));
        assertThat(JsonPath.<List<String>>read(hall, "$.data.yearly[?(@.year == '2194')].members[*].id")).contains(Long.toString(m));
        assertThat(JsonPath.<List<String>>read(hall, "$.data.monthly[?(@.month == '2194-07')].members[*].nickname")).contains(memberFixture.nickname(m));
    }

    // ── 내 칭호 ──────────────────────────────────────────────────────────

    @Test
    void 내_칭호는_로그인이_필요하고_축하_창은_보고_나면_비며_다음_목표를_준다() throws Exception {
        mvc.perform(get("/api/me/titles")).andExpect(status().isUnauthorized());
        long m = member();
        give(m, "popular", null, "BRONZE");
        // SQL 로 남김: 출석은 하루 한 번 API 로만 쌓인다 — 날 수만 본다
        for (int i = 0; i < 3; i++) {
            jdbc.update("INSERT INTO point_attendances (id, user_id, day, streak) VALUES (?, ?, ?, 1)", ids.nextId(), m, java.time.LocalDate.of(2001, 1, 1).plusDays(i));
        }
        String body = mvc.perform(as(m, get("/api/me/titles"))).andExpect(status().isOk()).andExpect(jsonPath("$.data.unseen.length()").value(1))
                .andExpect(jsonPath("$.data.unseen[0].key").value("popular")).andExpect(jsonPath("$.data.unseen[0].tier").value("BRONZE"))
                .andExpect(jsonPath("$.data.representative").doesNotExist()).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<String>>read(body, "$.data.progress[?(@.key == 'popular')].tier")).containsExactly("SILVER");
        assertThat(JsonPath.<List<Integer>>read(body, "$.data.progress[?(@.key == 'popular')].target")).containsExactly(30);
        assertThat(JsonPath.<List<Integer>>read(body, "$.data.progress[?(@.key == 'attendance-100')].current")).containsExactly(3);
        assertThat(JsonPath.<List<Integer>>read(body, "$.data.progress[?(@.key == 'attendance-100')].target")).containsExactly(100);
        mvc.perform(as(m, post("/api/me/titles/seen"))).andExpect(status().isNoContent());
        mvc.perform(as(m, get("/api/me/titles"))).andExpect(status().isOk()).andExpect(jsonPath("$.data.unseen.length()").value(0));
    }

    // ── 신청 ─────────────────────────────────────────────────────────────

    @Test
    void 신청은_조건을_채워야_하고_대기는_하나이며_거절_뒤에는_다시_신청할_날까지_막는다() throws Exception {
        String key = applyTitle(2, false);
        long m = member();
        mvc.perform(as(m, post("/api/me/titles/" + key + "/applications"))).andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("TITLE_CONDITIONS_UNMET"));
        mvc.perform(as(m, post("/api/me/titles/monthly-writer/applications"))).andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("TITLE_NOT_APPLICABLE"));
        mvc.perform(as(m, post("/api/me/titles/reporter/applications"))).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("TITLE_CONDITIONS_UNMET")); // 새싹 — 정식 이상 아님
        writePost(m);
        writePost(m);
        String mine = mvc.perform(as(m, get("/api/me/titles"))).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<Boolean>>read(mine, "$.data.applicable[?(@.key == '" + key + "')].canApply")).containsExactly(true);
        String created = mvc.perform(as(m, post("/api/me/titles/" + key + "/applications"))).andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("PENDING")).andReturn().getResponse().getContentAsString();
        mvc.perform(as(m, post("/api/me/titles/" + key + "/applications"))).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("TITLE_APPLICATION_PENDING"));
        String appId = JsonPath.read(created, "$.data.id");
        // SQL 로 남김: 거절은 심사자(운영자·매니저)가 한다 — 다시 신청할 날만 본다
        jdbc.update("UPDATE title_applications SET status = 'REJECTED', reason = '보완', reapply_at = now() + interval '30 days', reviewed_at = now() WHERE id = ?",
                Long.parseLong(appId));
        mvc.perform(as(m, post("/api/me/titles/" + key + "/applications"))).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("TITLE_REAPPLY_TOO_SOON"));
        mvc.perform(as(m, get("/api/me/titles"))).andExpect(jsonPath("$.data.applications[0].status").value("REJECTED"))
                .andExpect(jsonPath("$.data.applications[0].reason").value("보완"));
    }

    @Test
    void AI_회원과_운영팀_계정은_신청할_수_없다() throws Exception {
        String key = applyTitle(1, false);
        long ops = member();
        writePost(ops);
        jdbc.update("INSERT INTO operator_member_accounts (operator_id, user_id, linked_at) VALUES (?, ?, now())", newOperator(), ops);
        mvc.perform(as(ops, post("/api/me/titles/" + key + "/applications"))).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("TITLE_MEMBER_EXCLUDED"));
    }

    // ── 게시판 매니저 심사 ───────────────────────────────────────────────────

    @Test
    void 게시판_매니저는_자기_게시판_칭호_신청만_보고_처리하며_자기_신청은_못_하고_승인은_회원_행위자로_감사한다() throws Exception {
        String key = applyTitle(1, false);
        long manager = member();
        long staff = member();
        // SQL 로 남김: 게시판 운영진 지정은 관리자 서버 API — 역할만 둔다
        jdbc.update("INSERT INTO board_staff (board_id, user_id, role) VALUES (?, ?, 'MANAGER')", board, manager);
        jdbc.update("INSERT INTO board_staff (board_id, user_id, role) VALUES (?, ?, 'STAFF')", board, staff);
        long applicant = member();
        writePost(applicant);
        writePost(manager);
        String created = mvc.perform(as(applicant, post("/api/me/titles/" + key + "/applications"))).andExpect(status().isCreated()).andReturn().getResponse()
                .getContentAsString();
        String app = JsonPath.read(created, "$.data.id");
        String own = JsonPath.read(mvc.perform(as(manager, post("/api/me/titles/" + key + "/applications"))).andExpect(status().isCreated()).andReturn().getResponse()
                .getContentAsString(), "$.data.id");

        String path = "/api/boards/" + slug + "/staff/title-applications";
        mvc.perform(get(path)).andExpect(status().isUnauthorized());
        mvc.perform(as(staff, get(path))).andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("TITLE_REVIEW_FORBIDDEN"));
        String list = mvc.perform(as(manager, get(path))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<String>>read(list, "$.data.items[*].id")).containsExactlyInAnyOrder(app, own);
        assertThat(JsonPath.<List<Boolean>>read(list, "$.data.items[?(@.id == '" + own + "')].canReview")).containsExactly(false);
        assertThat(JsonPath.<List<Boolean>>read(list, "$.data.items[?(@.id == '" + app + "')].conditions[0].met")).containsExactly(true);

        mvc.perform(as(manager, post(path + "/" + own + "/approve")).content("{}")).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("TITLE_REVIEW_SELF"));
        // 다른 게시판의 매니저는 이 칭호를 심사할 수 없다
        long otherBoard = ids.nextId();
        String otherSlug = "mo" + Long.toString(otherBoard, 36);
        boardFixture.board(otherBoard, otherSlug, "다른 " + otherSlug.substring(otherSlug.length() - 6), 951);
        catalog.invalidate();
        long otherManager = member();
        jdbc.update("INSERT INTO board_staff (board_id, user_id, role) VALUES (?, ?, 'MANAGER')", otherBoard, otherManager);
        mvc.perform(as(otherManager, post("/api/boards/" + otherSlug + "/staff/title-applications/" + app + "/approve")).content("{}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("TITLE_REVIEW_FORBIDDEN"));

        mvc.perform(as(manager, post(path + "/" + app + "/approve")).content("{\"reason\":\"좋아요\"}")).andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("SELECT granted_by_member FROM member_titles WHERE user_id = ? AND title_id = ?", Long.class, applicant, titleId(key)))
                .isEqualTo(manager);
        Map<String, Object> audit = lastAudit("MEMBER", Long.toString(applicant), "TITLE_APPLICATION_APPROVE");
        assertThat(audit).containsEntry("actor_type", "MEMBER").containsEntry("actor_id", manager);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM member_notifications WHERE user_id = ? AND type = 'TITLE_GRANTED'", Integer.class, applicant)).isEqualTo(1);
        mvc.perform(as(manager, post(path + "/" + app + "/reject")).content("{\"reason\":\"늦음\"}")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("TITLE_APPLICATION_CLOSED"));
    }
}
