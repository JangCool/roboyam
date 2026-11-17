package com.community.title;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.community.batch.core.BatchParameters;
import com.community.batch.core.BatchRunner;
import com.community.batch.entity.BatchRequestKind;
import com.community.batch.entity.BatchRunStatus;
import com.community.board.service.BoardCatalog;
import com.community.commerce.Commerce04AdminTestBase;
import com.community.menu.service.AdminScreenRegistry;
import com.community.permission.entity.Permission;
import com.community.title.batch.TitleAutoGrantJob;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 회원 칭호 관리자 API·자동 판정 배치(member-title-contract 「구현 결정」 1·2·3·7·8) — 실제 필터 체인·권한·PostgreSQL. DB 를 다른 시험과 같이 쓰므로 기간은 먼 미래 해(2100~)로,
 * 인기글 날짜는 먼 과거로 겹치지 않게 고르고, 칭호 설정은 시험마다 되돌린다.
 */
class MemberTitleAdminHttpTest extends Commerce04AdminTestBase {

    private static final AtomicInteger DAY_SEQ = new AtomicInteger(ThreadLocalRandom.current().nextInt(0, 20_000));

    @Autowired
    private BatchRunner runner;
    @Autowired
    private TitleAutoGrantJob autoGrant;
    @Autowired
    private BoardCatalog catalog;

    private Cookie manager;
    private Map<String, Object> savedSettings;

    @BeforeEach
    void setUp() throws Exception {
        savedSettings = jdbc.queryForMap("SELECT monthly_max, honorary_count, reapply_days FROM title_settings WHERE id = 1");
        manager = login(operatorWith(Permission.TITLE_READ, Permission.TITLE_MANAGE, Permission.TITLE_REVIEW, Permission.MEMBER_READ, Permission.POST_READ));
    }

    @AfterEach
    void restore() {
        jdbc.update("UPDATE title_settings SET monthly_max = ?, honorary_count = ?, reapply_days = ? WHERE id = 1", savedSettings.get("monthly_max"),
                savedSettings.get("honorary_count"), savedSettings.get("reapply_days"));
    }

    /** 겹치지 않는 먼 미래의 달 */
    private static String month() {
        return (2100 + ThreadLocalRandom.current().nextInt(0, 899)) + "-" + String.format("%02d", ThreadLocalRandom.current().nextInt(1, 13));
    }

    private org.springframework.test.web.servlet.ResultActions grant(String key, long member, String period) throws Exception {
        String body = "{\"memberId\":\"" + member + "\"" + (period == null ? "" : ",\"period\":\"" + period + "\"") + ",\"reason\":\"시험 수여\"}";
        return mvc.perform(withCsrf(post("/api/admin/titles/" + key + "/grants"), manager).header("Accept-Language", "ko").content(body));
    }

    private long titleId(String key) {
        return jdbc.queryForObject("SELECT id FROM titles WHERE title_key = ?", Long.class, key);
    }

    private int active(long member, String key) {
        return jdbc.queryForObject("SELECT count(*) FROM member_titles WHERE user_id = ? AND title_id = ? AND revoked_at IS NULL", Integer.class, member, titleId(key));
    }

    private String tier(long member, String key) {
        return jdbc.queryForObject("SELECT tier FROM member_titles WHERE user_id = ? AND title_id = ? AND revoked_at IS NULL", String.class, member, titleId(key));
    }

    private void aiMember(long user) {
        // SQL 로 남김: AI 회원 만들기는 관리자 API(제공사 키·모델 검사)를 거친다 — 판별만 보면 되므로 프로필 줄만 둔다
        jdbc.update("INSERT INTO ai_member_profiles (id, user_id, provider, model, gender, age_band, personality, tone, interests, morning_start, morning_end, "
                + "afternoon_start, afternoon_end, created_at, updated_at) VALUES (?, ?, 'OPENAI', 'gpt-test', 'MALE', 'THIRTIES', '시험', '시험', '시험', "
                + "time '08:00', time '11:00', time '13:00', time '18:00', now(), now())", ids.nextId(), user);
    }

    // ── 목록·기본 칭호·권한 ─────────────────────────────────────────────────

    @Test
    void 기본_칭호_7개가_마이그레이션으로_있고_목록이_TitleSpec_칸과_단계를_준다() throws Exception {
        String body = mvc.perform(get("/api/admin/titles").cookie(manager)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        List<String> keys = JsonPath.read(body, "$.data[?(@.builtin == true)].key");
        assertThat(keys).containsExactly("monthly-writer", "yearly-writer", "honorary-writer", "reviewer", "reporter", "popular", "attendance-100");
        assertThat(JsonPath.<List<String>>read(body, "$.data[?(@.key == 'monthly-writer')].icon")).containsExactly("pen");
        assertThat(JsonPath.<List<String>>read(body, "$.data[?(@.key == 'popular')].tierMetric")).containsExactly("POPULAR_DAYS");
        assertThat(JsonPath.<List<Integer>>read(body, "$.data[?(@.key == 'popular')].tierGold")).containsExactly(100);
        assertThat(JsonPath.<List<String>>read(body, "$.data[?(@.key == 'popular')].chip")).containsExactly("GREEN");
        assertThat(JsonPath.<List<String>>read(body, "$.data[?(@.key == 'reviewer')].reviewer")).containsExactly("OPERATOR_AND_BOARD_MANAGER");
        assertThat(JsonPath.<List<String>>read(body, "$.data[?(@.key == 'reporter')].conditions[0].type")).containsExactly("GRADE_AT_LEAST");
        assertThat(JsonPath.<List<String>>read(body, "$.data[?(@.key == 'reporter')].conditions[2].type")).containsExactly("NO_REPORT_HIDDEN");
        assertThat(JsonPath.<List<String>>read(body, "$.data[?(@.key == 'attendance-100')].name")).containsExactly("출석 100일");
    }

    @Test
    void 읽기_권한이_없으면_403_읽기만_있으면_만들기_수여_심사가_403() throws Exception {
        Cookie none = login(operatorWith(Permission.MEMBER_READ));
        mvc.perform(get("/api/admin/titles").cookie(none)).andExpect(status().isForbidden());
        Cookie reader = login(operatorWith(Permission.TITLE_READ));
        mvc.perform(get("/api/admin/titles").cookie(reader)).andExpect(status().isOk());
        mvc.perform(get("/api/admin/titles/applications").cookie(reader)).andExpect(status().isOk());
        mvc.perform(withCsrf(post("/api/admin/titles"), reader).content("{}")).andExpect(status().isForbidden());
        mvc.perform(withCsrf(post("/api/admin/titles/monthly-writer/grants"), reader).content("{}")).andExpect(status().isForbidden());
        mvc.perform(withCsrf(post("/api/admin/titles/applications/1/approve"), reader).content("{}")).andExpect(status().isForbidden());
        Cookie reviewer = login(operatorWith(Permission.TITLE_REVIEW));
        mvc.perform(get("/api/admin/titles/applications").cookie(reviewer)).andExpect(status().isOk());
    }

    // ── 만들기·고치기 ───────────────────────────────────────────────────────

    private String spec(String name, String method) {
        return "{\"name\":\"" + name + "\",\"icon\":\"bulb\",\"chip\":\"PURPLE\",\"method\":\"" + method + "\",\"reviewer\":\"OPERATOR\",\"conditions\":[],"
                + "\"tierMetric\":null,\"tierBronze\":null,\"tierSilver\":null,\"tierGold\":null,\"benefitTrialPriority\":true,\"benefitMagazine\":false,"
                + "\"benefitAvatarFrame\":false,\"dormantHide\":false,\"enabled\":true,\"sortOrder\":500}";
    }

    @Test
    void 이름은_공백_뺀_6자까지이고_등급_이름이면_400_다른_칭호와_같으면_409() throws Exception {
        mvc.perform(withCsrf(post("/api/admin/titles"), manager).header("Accept-Language", "ko").content(spec("일곱글자칭호다", "AWARD")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.fields.name").value("칭호 이름은 공백을 빼고 6자까지예요."));
        String grade = catalog.snapshot().grades().values().iterator().next().label();
        mvc.perform(withCsrf(post("/api/admin/titles"), manager).header("Accept-Language", "ko").content(spec(grade, "AWARD")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.fields.name").value("등급 이름과 같은 이름은 쓸 수 없어요."));
        mvc.perform(withCsrf(post("/api/admin/titles"), manager).content(spec("기 자 단", "AWARD"))).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("TITLE_NAME_TAKEN"));
        mvc.perform(withCsrf(post("/api/admin/titles"), manager).content(spec("자동빈칸", "AUTO"))).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fields.conditions").exists());
    }

    @Test
    void 만들면_201_키를_발급하고_감사_TITLE_CREATE_고치면_바뀐_칸만_감사() throws Exception {
        String name = "시" + Long.toString(ids.nextId() % 10000, 36);
        String body = mvc.perform(withCsrf(post("/api/admin/titles"), manager).content(spec(name, "AWARD"))).andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.builtin").value(false)).andExpect(jsonPath("$.data.benefitTrialPriority").value(true))
                .andReturn().getResponse().getContentAsString();
        String key = JsonPath.read(body, "$.data.key");
        assertThat(key).startsWith("t");
        assertThat(auditLogs.where().action("TITLE_CREATE").target(key).count()).isEqualTo(1);

        String changed = spec(name, "AWARD").replace("\"key\"", "").replace("{\"name\"", "{\"key\":\"" + key + "\",\"name\"").replace("\"bulb\"", "\"heart\"");
        mvc.perform(withCsrf(put("/api/admin/titles"), manager).content(changed)).andExpect(status().isOk()).andExpect(jsonPath("$.data.icon").value("heart"));
        String detail = auditLogs.where().action("TITLE_UPDATE").target(key).latest().orElseThrow().detail();
        assertThat(JsonPath.<Map<String, Object>>read(detail, "$.after")).containsOnlyKeys("icon");
    }

    @Test
    void 기본_칭호의_받는_방법은_바꿀_수_없다_409() throws Exception {
        String body = mvc.perform(get("/api/admin/titles").cookie(manager)).andReturn().getResponse().getContentAsString();
        Map<String, Object> row = JsonPath.<List<Map<String, Object>>>read(body, "$.data[?(@.key == 'monthly-writer')]").get(0);
        String json = new tools.jackson.databind.ObjectMapper().writeValueAsString(row).replace("\"method\":\"AWARD\"", "\"method\":\"APPLY\"");
        mvc.perform(withCsrf(put("/api/admin/titles/monthly-writer"), manager).content(json)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("TITLE_BUILTIN_LOCKED"));
    }

    // ── 수여·명예 작가·회수 ─────────────────────────────────────────────────

    @Test
    void 이달의_작가는_기간이_없으면_400_한_달_최대_인원이_차면_409_수여는_감사와_알림을_남긴다() throws Exception {
        jdbc.update("UPDATE title_settings SET monthly_max = 1 WHERE id = 1");
        String m = month();
        long first = newMember();
        long second = newMember();
        grant("monthly-writer", first, null).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.fields.period").exists());
        grant("monthly-writer", first, m).andExpect(status().isCreated()).andExpect(jsonPath("$.data.period").value(m))
                .andExpect(jsonPath("$.data.title.label").value(Integer.parseInt(m.substring(5)) + "월의 작가"))
                .andExpect(jsonPath("$.data.member.memberNo").value(Long.toString(first)))
                .andExpect(jsonPath("$.data.source").value("AWARD"));
        grant("monthly-writer", second, m).andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("TITLE_MONTHLY_FULL"));
        grant("monthly-writer", first, m).andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("TITLE_MONTHLY_FULL"));
        assertThat(auditLogs.where().action("TITLE_GRANT").targetType("MEMBER").target(first).count()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM member_notifications WHERE user_id = ? AND type = 'TITLE_GRANTED'", Integer.class, first)).isEqualTo(1);
        jdbc.update("UPDATE title_settings SET monthly_max = 3 WHERE id = 1");
        grant("monthly-writer", first, m).andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("TITLE_ALREADY_GRANTED"));
    }

    @Test
    void 명예_작가는_이달의_작가_수여_트랜잭션에서_바로_따라온다() throws Exception {
        jdbc.update("UPDATE title_settings SET honorary_count = 2 WHERE id = 1");
        long member = newMember();
        grant("monthly-writer", member, "2190-01").andExpect(status().isCreated()).andExpect(jsonPath("$.data.honorary").doesNotExist());
        grant("monthly-writer", member, "2190-02").andExpect(status().isCreated()).andExpect(jsonPath("$.data.honorary.title.key").value("honorary-writer"))
                .andExpect(jsonPath("$.data.honorary.source").value("AUTO"));
        assertThat(active(member, "honorary-writer")).isEqualTo(1);
        assertThat(auditLogs.where().action("TITLE_GRANT").target(member).latest().orElseThrow().detail()).contains("honoraryGrantId");
    }

    @Test
    void 명예_작가_기준을_내리면_바로_소급하고_올려도_회수하지_않는다() throws Exception {
        jdbc.update("UPDATE title_settings SET honorary_count = 24 WHERE id = 1");
        long member = newMember();
        grant("monthly-writer", member, "2191-03").andExpect(status().isCreated());
        grant("monthly-writer", member, "2191-04").andExpect(status().isCreated());
        assertThat(active(member, "honorary-writer")).isZero();
        mvc.perform(withCsrf(put("/api/admin/titles/settings"), manager).content("{\"monthlyMax\":3,\"honoraryCount\":2}")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.honoraryCount").value(2)).andExpect(jsonPath("$.data.honoraryGranted").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)));
        assertThat(active(member, "honorary-writer")).isEqualTo(1);
        assertThat(auditLogs.where().action("TITLE_SETTINGS_UPDATE").latest().orElseThrow().detail()).contains("honoraryCount");
        mvc.perform(withCsrf(put("/api/admin/titles/settings"), manager).content("{\"monthlyMax\":3,\"honoraryCount\":10}")).andExpect(status().isOk());
        assertThat(active(member, "honorary-writer")).as("올려도 회수 없음").isEqualTo(1);
        mvc.perform(withCsrf(put("/api/admin/titles/settings"), manager).header("Accept-Language", "ko").content("{\"monthlyMax\":21,\"honoraryCount\":0}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.fields.monthlyMax").exists()).andExpect(jsonPath("$.error.fields.honoraryCount").exists());
    }

    @Test
    void 올해의_작가는_그해_이달의_작가만_받는다() throws Exception {
        long member = newMember();
        grant("yearly-writer", member, "2192").andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("TITLE_YEARLY_NOT_ELIGIBLE"));
        grant("monthly-writer", member, "2192-05").andExpect(status().isCreated());
        grant("yearly-writer", member, "2192-05").andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.fields.period").exists());
        grant("yearly-writer", member, "2192").andExpect(status().isCreated()).andExpect(jsonPath("$.data.period").value("2192"));
    }

    @Test
    void AI_회원과_운영팀_계정은_수여도_막는다_409() throws Exception {
        long ai = newMember();
        aiMember(ai);
        grant("monthly-writer", ai, month()).andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("TITLE_MEMBER_EXCLUDED"));
        long ops = newMember();
        jdbc.update("INSERT INTO operator_member_accounts (operator_id, user_id, linked_at) VALUES (?, ?, now())", operatorWith(), ops);
        grant("reporter", ops, null).andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("TITLE_MEMBER_EXCLUDED"));
        grant("reporter", ids.nextId(), null).andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value("TITLE_MEMBER_NOT_FOUND"));
    }

    @Test
    void 회수는_사유가_필요하고_줄은_남기며_감사를_남긴다_두_번이면_409() throws Exception {
        long member = newMember();
        String body = grant("reporter", member, null).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String grantId = JsonPath.read(body, "$.data.grantId");
        mvc.perform(withCsrf(delete("/api/admin/titles/reporter/grants/" + grantId), manager).content("{}")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fields.reason").exists());
        mvc.perform(withCsrf(delete("/api/admin/titles/reporter/grants/" + grantId), manager).content("{\"reason\":\"규칙 위반\"}")).andExpect(status().isNoContent());
        assertThat(active(member, "reporter")).isZero();
        assertThat(jdbc.queryForObject("SELECT revoked_reason FROM member_titles WHERE id = ?", String.class, Long.parseLong(grantId))).isEqualTo("규칙 위반");
        assertThat(auditLogs.where().action("TITLE_REVOKE").target(member).count()).isEqualTo(1);
        mvc.perform(withCsrf(delete("/api/admin/titles/reporter/grants/" + grantId), manager).content("{\"reason\":\"또\"}")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("TITLE_GRANT_NOT_ACTIVE"));
        mvc.perform(get("/api/admin/titles/grants").param("memberId", Long.toString(member)).cookie(manager)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1)).andExpect(jsonPath("$.data.items[0].active").value(false))
                .andExpect(jsonPath("$.data.items[0].revokeReason").value("규칙 위반"));
        mvc.perform(get("/api/admin/members/" + member).cookie(manager)).andExpect(status().isOk()).andExpect(jsonPath("$.data.titles.length()").value(1))
                .andExpect(jsonPath("$.data.titles[0].title.key").value("reporter"));
    }

    // ── 신청 심사(운영자) ────────────────────────────────────────────────────

    private long application(long member, String key) {
        long id = ids.nextId();
        // SQL 로 남김: 신청은 공개 사이트 API(조건 검사)로 들어온다 — 심사만 보려고 대기 줄을 바로 둔다
        jdbc.update("INSERT INTO title_applications (id, user_id, title_id, status, created_at) VALUES (?, ?, ?, 'PENDING', now())", id, member, titleId(key));
        return id;
    }

    @Test
    void 운영자_승인은_신청_승인_칭호를_주고_거절은_사유와_30일_뒤_다시_신청할_날을_남긴다() throws Exception {
        long approved = newMember();
        long app = application(approved, "reporter");
        String list = mvc.perform(get("/api/admin/titles/applications").param("status", "PENDING").param("key", "reporter").cookie(manager)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<Object>>read(list, "$.data.items[?(@.id == '" + app + "')].conditions[*]")).hasSize(3);
        assertThat(JsonPath.<List<String>>read(list, "$.data.items[?(@.id == '" + app + "')].member.memberNo")).containsExactly(Long.toString(approved));
        mvc.perform(withCsrf(post("/api/admin/titles/applications/" + app + "/approve"), manager).content("{}")).andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("SELECT source FROM member_titles WHERE user_id = ? AND title_id = ?", String.class, approved, titleId("reporter")))
                .isEqualTo("APPLICATION");
        assertThat(auditLogs.where().action("TITLE_APPLICATION_APPROVE").target(approved).count()).isEqualTo(1);
        mvc.perform(withCsrf(post("/api/admin/titles/applications/" + app + "/approve"), manager).content("{}")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("TITLE_APPLICATION_CLOSED"));

        long rejected = newMember();
        long app2 = application(rejected, "reporter");
        mvc.perform(withCsrf(post("/api/admin/titles/applications/" + app2 + "/reject"), manager).content("{}")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fields.reason").exists());
        mvc.perform(withCsrf(post("/api/admin/titles/applications/" + app2 + "/reject"), manager).content("{\"reason\":\"뉴스 글이 부족해요\"}"))
                .andExpect(status().isNoContent());
        Map<String, Object> row = jdbc.queryForMap("SELECT status, reason, reapply_at, reviewed_at FROM title_applications WHERE id = ?", app2);
        assertThat(row).containsEntry("status", "REJECTED").containsEntry("reason", "뉴스 글이 부족해요");
        Instant reapply = ((Timestamp) row.get("reapply_at")).toInstant();
        Instant reviewed = ((Timestamp) row.get("reviewed_at")).toInstant();
        assertThat(ChronoUnit.DAYS.between(reviewed, reapply)).isEqualTo(30);
        assertThat(auditLogs.where().action("TITLE_APPLICATION_REJECT").target(rejected).count()).isEqualTo(1);
    }

    // ── 이달의 작가 후보 ─────────────────────────────────────────────────────

    @Test
    void 후보는_그_달_우수_글_글쓴이와_넣은_글의_글쓴이이고_AI_글은_넣을_수_없다() throws Exception {
        String m = month();
        long board = ids.nextId();
        boardFixture.board(board, "tt" + Long.toString(board, 36), "칭호 후보 " + Long.toString(board, 36).substring(0, 5), 990);
        long featuredAuthor = newMember();
        long manualAuthor = newMember();
        long featuredPost = ids.nextId();
        long manualPost = ids.nextId();
        boardFixture.post(featuredPost, board, featuredAuthor, "우수 글", "본문");
        boardFixture.post(manualPost, board, manualAuthor, "넣은 글", "본문");
        Instant inMonth = java.time.YearMonth.parse(m).atDay(10).atStartOfDay(com.community.zconfiguration.time.ServiceTime.ZONE).toInstant();
        // SQL 로 남김: 우수 글 선정은 글 관리 API 가 지금 시각으로만 남긴다 — 먼 미래 달의 선정을 두려고 줄을 바로 둔다
        jdbc.update("INSERT INTO post_features (id, post_id, author_id, featured_by, featured_at) VALUES (?, ?, ?, ?, ?)", ids.nextId(), featuredPost, featuredAuthor,
                operatorWith(), Timestamp.from(inMonth));

        mvc.perform(withCsrf(post("/api/admin/titles/monthly-candidates"), manager).content("{\"postId\":\"" + manualPost + "\",\"month\":\"" + m + "\"}"))
                .andExpect(status().isNoContent());
        mvc.perform(withCsrf(post("/api/admin/titles/monthly-candidates"), manager).content("{\"postId\":\"" + manualPost + "\",\"month\":\"" + m + "\"}"))
                .andExpect(status().isNoContent()); // 두 번 넣어도 하나
        assertThat(auditLogs.where().action("TITLE_CANDIDATE_ADD").target(manualPost).count()).isEqualTo(1);

        String body = mvc.perform(get("/api/admin/titles/monthly-candidates").param("month", m).cookie(manager)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.month").value(m)).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<String>>read(body, "$.data.candidates[*].member.id")).containsExactlyInAnyOrder(Long.toString(featuredAuthor), Long.toString(manualAuthor));
        assertThat(JsonPath.<List<Boolean>>read(body, "$.data.candidates[?(@.member.id == '" + featuredAuthor + "')].posts[0].featured")).containsExactly(true);
        assertThat(JsonPath.<List<Boolean>>read(body, "$.data.candidates[?(@.member.id == '" + manualAuthor + "')].posts[0].manual")).containsExactly(true);

        mvc.perform(withCsrf(delete("/api/admin/titles/monthly-candidates"), manager).content("{\"postId\":\"" + manualPost + "\",\"month\":\"" + m + "\"}"))
                .andExpect(status().isNoContent());
        body = mvc.perform(get("/api/admin/titles/monthly-candidates").param("month", m).cookie(manager)).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<String>>read(body, "$.data.candidates[*].member.id")).containsExactly(Long.toString(featuredAuthor));

        long ai = newMember();
        aiMember(ai);
        long aiPost = ids.nextId();
        boardFixture.post(aiPost, board, ai, "AI 글", "본문");
        mvc.perform(withCsrf(post("/api/admin/titles/monthly-candidates"), manager).content("{\"postId\":\"" + aiPost + "\"}")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("TITLE_CANDIDATE_INVALID"));
    }

    @Test
    void 글_관리_목록_줄은_이번_달_후보로_넣은_글을_monthlyCandidate_로_준다() throws Exception {
        long board = ids.nextId();
        String slug = "tc" + Long.toString(board, 36);
        boardFixture.board(board, slug, "후보 표시 " + Long.toString(board, 36).substring(0, 5), 991);
        long author = newMember();
        long postId = ids.nextId();
        boardFixture.post(postId, board, author, "후보 표시 글", "본문");
        catalog.invalidate();
        mvc.perform(withCsrf(post("/api/admin/titles/monthly-candidates"), manager).content("{\"postId\":\"" + postId + "\"}")).andExpect(status().isNoContent());
        mvc.perform(get("/api/admin/posts").param("board", Long.toString(board)).cookie(manager)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].id").value(Long.toString(postId))).andExpect(jsonPath("$.data.items[0].monthlyCandidate").value(true));
    }

    // ── 자동 판정 배치 ──────────────────────────────────────────────────────

    private BatchRunner.RunReport runAuto(String titleKey) {
        Map<String, String> params = titleKey == null ? Map.of("day", "1999-01-01") : Map.of("day", "1999-01-01", "title", titleKey);
        return runner.runRequested(new BatchRunner.RunRequest(autoGrant.definition(), BatchRequestKind.NOW, null, BatchParameters.of(params), null, null, null));
    }

    private void popularDays(long author, int days) {
        long postId = ids.nextId();
        for (int i = 0; i < days; i++) {
            LocalDate day = LocalDate.of(1800, 1, 1).plusDays(DAY_SEQ.getAndIncrement());
            // SQL 로 남김: 인기글 날짜별 목록은 DB 함수 뽑기(인기글 보상 배치)만 넣는다 — 진입 일수만 보려고 줄을 바로 둔다
            jdbc.update("INSERT INTO point_popular_rewards (day, rank, post_id, author_id, score, created_at) VALUES (?, 1, ?, ?, 1, now())", day, postId, author);
        }
    }

    private void attendance(long member, int days) {
        for (int i = 0; i < days; i++) {
            // SQL 로 남김: 출석은 하루 한 번 API 로만 쌓인다 — 날 수만 보려고 줄을 바로 둔다
            jdbc.update("INSERT INTO point_attendances (id, user_id, day, streak) VALUES (?, ?, ?, 1)", ids.nextId(), member, LocalDate.of(2000, 1, 1).plusDays(i));
        }
    }

    @Test
    void 자동_판정은_출석_100일과_인기글_단계를_주고_올리며_AI와_운영자가_회수한_칭호는_건너뛴다() {
        long attender = newMember();
        attendance(attender, 100);
        long popular = newMember();
        popularDays(popular, 10);
        long ai = newMember();
        aiMember(ai);
        attendance(ai, 100);
        long revoked = newMember();
        popularDays(revoked, 12);

        assertThat(runAuto(null).status()).isEqualTo(BatchRunStatus.OK);
        assertThat(active(attender, "attendance-100")).isEqualTo(1);
        assertThat(tier(popular, "popular")).isEqualTo("BRONZE");
        assertThat(active(ai, "attendance-100")).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM member_notifications WHERE user_id = ? AND type = 'TITLE_GRANTED'", Integer.class, popular)).isEqualTo(1);

        // 운영자가 회수하면 다시 주지 않는다
        jdbc.update("UPDATE member_titles SET revoked_at = now(), revoked_by = ?, revoked_reason = '시험' WHERE user_id = ? AND title_id = ?", operatorWith(), revoked,
                titleId("popular"));
        // 단계 오름 — 같은 줄의 단계를 올리고 축하 창을 다시 띄운다
        jdbc.update("UPDATE member_titles SET seen_at = now() WHERE user_id = ?", popular);
        popularDays(popular, 20);
        assertThat(runAuto("popular").status()).isEqualTo(BatchRunStatus.OK);
        assertThat(tier(popular, "popular")).isEqualTo("SILVER");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM member_titles WHERE user_id = ? AND title_id = ?", Integer.class, popular, titleId("popular"))).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT seen_at FROM member_titles WHERE user_id = ? AND revoked_at IS NULL", Timestamp.class, popular)).isNull();
        assertThat(active(revoked, "popular")).as("운영자가 회수한 칭호는 다시 주지 않는다").isZero();

        // 다시 돌려도 그대로(한 번)
        assertThat(runAuto(null).status()).as("바뀐 것이 없으면 SKIP").isIn(BatchRunStatus.OK, BatchRunStatus.SKIP);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM member_titles WHERE user_id = ? AND title_id = ?", Integer.class, attender, titleId("attendance-100")))
                .isEqualTo(1);
    }

    @Test
    void 포인트_인기글_보상이_꺼져도_그날_인기글_목록을_같은_뽑기로_남기고_title_파라미터는_자동_칭호만() {
        Map<String, Object> saved = jdbc.queryForMap("SELECT popular_reward_points, popular_reward_top_n FROM point_settings WHERE id = 1");
        try {
            jdbc.update("UPDATE point_settings SET popular_reward_points = 0, popular_reward_top_n = 0 WHERE id = 1");
            // 그날(며칠 전 — 글 월 조각이 있는 범위) 올라온 인기 글 하나
            LocalDate day = LocalDate.now(com.community.zconfiguration.time.ServiceTime.ZONE).minusDays(ThreadLocalRandom.current().nextInt(2, 26));
            Instant at = day.atTime(12, 0).atZone(com.community.zconfiguration.time.ServiceTime.ZONE).toInstant();
            long board = ids.nextId();
            boardFixture.board(board, "tp" + Long.toString(board, 36), "칭호 인기 " + Long.toString(board, 36).substring(0, 5), 992);
            long author = newMember();
            long postId = ((at.toEpochMilli() - 1767225600000L) << 22) | ThreadLocalRandom.current().nextLong(1, 1L << 22);
            boardFixture.post(postId, board, author, "인기 글", "본문");
            // SQL 로 남김: 그날의 글·반응 수를 만드는 공개 API 가 없다(지금 시각으로만 쓴다)
            jdbc.update("UPDATE posts SET created_at = ?, like_count = 500, comment_count = 100, view_count = 10000 WHERE id = ?", Timestamp.from(at), postId);
            BatchRunner.RunReport report = runner.runRequested(new BatchRunner.RunRequest(autoGrant.definition(), BatchRequestKind.NOW, null,
                    BatchParameters.of(Map.of("day", day.toString(), "title", "attendance-100")), null, null, null));
            assertThat(report.status()).isIn(BatchRunStatus.OK, BatchRunStatus.SKIP);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM point_popular_rewards WHERE day = ? AND post_id = ?", Integer.class, day, postId))
                    .as("포인트와 상관없이 뽑는다").isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM point_ledger WHERE user_id = ? AND reason = 'POPULAR'", Integer.class, author)).isZero();
            assertThat(autoGrant.validate(BatchParameters.of(Map.of("title", "reporter")))).containsKey("title");
            assertThat(autoGrant.validate(BatchParameters.of(Map.of("title", "popular")))).isEmpty();
        } finally {
            jdbc.update("UPDATE point_settings SET popular_reward_points = ?, popular_reward_top_n = ? WHERE id = 1", saved.get("popular_reward_points"),
                    saved.get("popular_reward_top_n"));
        }
    }

    @Test
    void 저장_시_소급은_자동_칭호만_그_칭호_파라미터로_배치_요청을_넣는다() throws Exception {
        String body = mvc.perform(get("/api/admin/titles").cookie(manager)).andReturn().getResponse().getContentAsString();
        Map<String, Object> row = JsonPath.<List<Map<String, Object>>>read(body, "$.data[?(@.key == 'attendance-100')]").get(0);
        jdbc.update("DELETE FROM batch_run_requests WHERE job_key = 'title-auto-grant' AND status = 'PENDING'");
        mvc.perform(withCsrf(put("/api/admin/titles/attendance-100"), manager).content(new tools.jackson.databind.ObjectMapper().writeValueAsString(row)))
                .andExpect(status().isOk());
        String params = jdbc.queryForObject("SELECT params::text FROM batch_run_requests WHERE job_key = 'title-auto-grant' AND status = 'PENDING' ORDER BY requested_at DESC LIMIT 1",
                String.class);
        assertThat(params).contains("attendance-100");
        jdbc.update("DELETE FROM batch_run_requests WHERE job_key = 'title-auto-grant' AND status = 'PENDING'");
    }

    // ── 카탈로그 사본 ───────────────────────────────────────────────────────

    @Test
    void 회원_칭호_메뉴_줄은_등급_바로_아래이고_admin_web_사본과_같다() throws IOException {
        AdminScreenRegistry.Screen screen = AdminScreenRegistry.screen("memberTitles").orElseThrow();
        assertThat(screen.path()).isEqualTo("/members/titles");
        assertThat(screen.permission()).isEqualTo(Permission.TITLE_READ);
        Path web = Paths.get("").toAbsolutePath().getParent().getParent().resolve("apps/admin-web/app/utils/util-admin-menu.ts");
        String source = Files.readString(web);
        assertThat(source).contains("{ key: 'memberTitles', to: '/members/titles', icon: 'pi pi-star', labelKey: 'menu.memberTitles', permission: 'title.read' }");
        assertThat(source.indexOf("key: 'grades'")).isLessThan(source.indexOf("key: 'memberTitles'"));
    }
}
