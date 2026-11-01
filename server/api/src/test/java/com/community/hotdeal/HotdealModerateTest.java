package com.community.hotdeal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.community.auth.dto.AuthUser;
import com.community.hotdeal.dto.response.PostDealModerationView;
import com.community.hotdeal.dto.response.PostDealView;
import com.community.installation.service.InstallationState;
import com.community.post.dto.response.PostDetailView;
import com.community.user.entity.Role;
import jakarta.servlet.Filter;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * 게시판 매니저·스텝의 핫딜 종료·재개(공개 API — 사용자 결정 2026-11-01, 메모 4절) — 그 게시판 매니저·스텝만(다른 게시판 매니저·일반 회원·정지 매니저 403 · 비로그인 401) ·
 * 사유 필수 1~200자(재개 0~200) · 이미 종료 409 DEAL_ENDED · 진행 중 재개 409 DEAL_NOT_ENDED · 종료 = 운영진 종료 길(ENDED MANAGER · ended_by_role · 의심 구간 보상 ·
 * 작성자 알림 사유) · 재개 = 의심·keep·요청 리셋 + 기간 지남 알림 · 감사(행위자 회원 · boardId·role·reason) · 글 상세 {@code dealModeration}.
 */
class HotdealModerateTest extends HotdealStage2TestBase {

    @Autowired
    private WebApplicationContext context;
    @Autowired
    private InstallationState installation;
    @Autowired
    @Qualifier("springSecurityFilterChain")
    private Filter securityFilterChain;

    /** 그 게시판에 매니저·스텝 지정 */
    private long staffOf(String slug, String role) {
        long m = member();
        appoint(slug, m, role);
        return m;
    }

    private void appoint(String slug, long member, String role) {
        jdbc.update("INSERT INTO board_staff (board_id, user_id, role) VALUES (?, ?, ?)", boardId(slug), member, role);
    }

    private long generalPost(String slug) {
        return Long.parseLong(posts.create(new com.community.post.dto.request.PostWriteRequest(slug, null, "일반 글 " + uniq(), "TEXT", BODY, false, null, null, null, null),
                member(), "127.0.0.1").id());
    }

    private PostDealView end(long post, long actor, String reason) {
        return states.moderateEnd(Long.toString(post), actor, reason, "10.1.0.1");
    }

    private PostDealView reopen(long post, long actor, String reason) {
        return states.moderateReopen(Long.toString(post), actor, reason, "10.1.0.1");
    }

    private PostDealModerationView moderationSeenBy(long post, Long viewer) {
        return postQueries.detail(Long.toString(post), viewer, "10.1.0.2").dealModeration();
    }

    private Map<String, Object> lastAudit(long actor, String action) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT actor_type, actor_id, target_type, target_id, ip, detail FROM audit_logs WHERE actor_id = ? AND action = ? "
                + "ORDER BY occurred_at DESC, id DESC LIMIT 1", actor, action);
        return rows.isEmpty() ? null : rows.get(0);
    }

    @Test
    void 그_게시판_매니저는_운영진_종료와_같은_길로_끝낸다_보상_작성자_알림_사유() {
        long manager = staffOf(board, "MANAGER");
        long author = member();
        long post = newDeal(author);
        List<Long> requesters = requestBy(post, 3); // 의심 구간 요청자

        PostDealView ended = end(post, manager, "  판매처에서 품절됐어요  ");
        assertThat(ended.status()).isEqualTo("ENDED");
        assertThat(ended.endedBy()).isEqualTo("MANAGER");
        assertThat(ended.reopenUntil()).isNull(); // 작성자 되돌리기 대상이 아니다
        assertThat(dealRow(post)).containsEntry("ended_by", "MANAGER").containsEntry("ended_by_id", manager).containsEntry("ended_by_role", "BOARD_MANAGER")
                .containsEntry("end_note", "판매처에서 품절됐어요");
        for (long r : requesters) {
            assertThat(rewards(r)).isEqualTo(1);
        }
        assertInvariants(requesters);
        assertThat(notices(author, "DEAL_MANAGER_ENDED", post)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT payload->>'note' FROM member_notifications WHERE user_id = ? AND type = 'DEAL_MANAGER_ENDED' AND post_id = ?",
                String.class, author, post)).isEqualTo("판매처에서 품절됐어요");

        // 이미 종료 — 409, 보상·알림은 다시 없다
        assertThat(error(() -> end(post, manager, "다시")).getErrorCode()).isEqualTo("DEAL_ENDED");
        assertThat(rewards(requesters.get(0))).isEqualTo(1);
        assertThat(notices(author, "DEAL_MANAGER_ENDED", post)).isEqualTo(1);
    }

    @Test
    void 그_게시판_스텝도_종료하고_기간_지난_딜도_끝낸다() {
        long staff = staffOf(board, "STAFF");
        long post = newDeal(member());
        clock.set(clock.instant().plus(Duration.ofDays(30)).plusSeconds(1)); // 기간 지남(autoExpireDays 30)
        PostDealView ended = end(post, staff, "기간이 지났어요");
        assertThat(ended.status()).isEqualTo("ENDED");
        assertThat(dealRow(post)).containsEntry("ended_by_role", "BOARD_STAFF").containsEntry("ended_by_id", staff);
    }

    @Test
    void 다른_게시판_매니저_일반_회원_정지_매니저는_403_비로그인은_401() {
        String other = dealBoard();
        long otherManager = staffOf(other, "MANAGER");
        long suspended = staffOf(board, "MANAGER");
        jdbc.update("UPDATE users SET status = 'SUSPENDED' WHERE id = ?", suspended);
        long banned = staffOf(board, "STAFF");
        jdbc.update("UPDATE users SET status = 'BANNED' WHERE id = ?", banned);
        long author = member();
        long post = newDeal(author);

        for (long who : List.of(otherManager, member(), suspended, banned, author)) {
            assertThat(error(() -> end(post, who, "종료")).getErrorCode()).as("회원 %d", who).isEqualTo("DEAL_MODERATE_FORBIDDEN");
            assertThat(error(() -> end(post, who, "종료")).getStatus().value()).isEqualTo(403);
        }
        long ghost = ids.nextId();
        assertThat(error(() -> end(post, ghost, "종료")).getErrorCode()).isEqualTo("DEAL_MODERATE_FORBIDDEN"); // 없는 회원
        assertThat(error(() -> states.moderateEnd(Long.toString(post), null, "종료", null)).getErrorCode()).isEqualTo("UNAUTHENTICATED");
        assertThat(error(() -> states.moderateReopen(Long.toString(post), null, null, null)).getErrorCode()).isEqualTo("UNAUTHENTICATED");
        assertThat(dealRow(post)).containsEntry("status", "ACTIVE");

        // 재개도 같은 판정
        long manager = staffOf(board, "MANAGER");
        end(post, manager, "종료");
        for (long who : List.of(otherManager, member(), suspended, banned, author)) {
            assertThat(error(() -> reopen(post, who, null)).getErrorCode()).isEqualTo("DEAL_MODERATE_FORBIDDEN");
        }
        // 지정이 풀리면 바로 막힌다(요청마다 다시 판정)
        jdbc.update("DELETE FROM board_staff WHERE user_id = ?", manager);
        assertThat(error(() -> reopen(post, manager, null)).getErrorCode()).isEqualTo("DEAL_MODERATE_FORBIDDEN");
        assertThat(dealRow(post)).containsEntry("status", "ENDED");
    }

    @Test
    void 핫딜_글이_아니면_404_DEAL_NOT_FOUND() {
        String general = generalBoard();
        long manager = staffOf(general, "MANAGER");
        long post = generalPost(general);
        assertThat(error(() -> end(post, manager, "종료")).getErrorCode()).isEqualTo("DEAL_NOT_FOUND");
    }

    @Test
    void 종료_사유는_필수_1_200자_재개는_0_200자() {
        long manager = staffOf(board, "MANAGER");
        long post = newDeal(member());
        for (String bad : new String[]{null, "", "   ", "\u0000", "가".repeat(201)}) {
            assertThat(fieldKeys(() -> end(post, manager, bad))).containsKey("reason");
        }
        assertThat(dealRow(post)).containsEntry("status", "ACTIVE");
        end(post, manager, "가".repeat(200));
        assertThat(dealRow(post)).containsEntry("end_note", "가".repeat(200));

        assertThat(fieldKeys(() -> reopen(post, manager, "나".repeat(201)))).containsKey("reason");
        assertThat(reopen(post, manager, null).status()).isEqualTo("ACTIVE"); // 재개 사유는 없어도 된다
    }

    @Test
    void 진행_중_재개는_409_DEAL_NOT_ENDED() {
        long staff = staffOf(board, "STAFF");
        long post = newDeal(member());
        assertThat(error(() -> reopen(post, staff, null)).getErrorCode()).isEqualTo("DEAL_NOT_ENDED");
        requestBy(post, 3); // 의심이어도 진행 중
        assertThat(error(() -> reopen(post, staff, "다시")).getErrorCode()).isEqualTo("DEAL_NOT_ENDED");
    }

    @Test
    void 재개는_의심_keep_요청을_리셋하고_기간이_지났으면_작성자에게_알린다() {
        long manager = staffOf(board, "MANAGER");
        long author = member();
        long post = newDeal(author);
        requestBy(post, 3);
        states.keep(Long.toString(post), author);
        clock.set(clock.instant().plusSeconds(1));
        requestBy(post, 3);
        assertThat(dealRow(post)).containsEntry("keep_count", 1);
        assertThat(dealRow(post).get("flagged_at")).isNotNull();
        end(post, manager, "품절");

        PostDealView reopened = reopen(post, manager, "판매 재개됐어요");
        assertThat(reopened.status()).isEqualTo("ACTIVE");
        assertThat(reopened.view()).isEqualTo("ACTIVE");
        assertThat(reopened.flaggedAt()).isNull();
        assertThat(reopened.keepCount()).isZero();
        assertThat(reopened.endRequests().total()).isZero();
        assertThat(dealRow(post)).containsEntry("keep_count", 0).containsEntry("ended_by", null).containsEntry("ended_by_role", null)
                .containsEntry("ended_by_id", null);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM deal_end_requests WHERE post_id = ?", Integer.class, post)).isZero();
        assertThat(notices(author, "DEAL_REOPENED_FIX_ENDS", post)).isZero(); // 기간이 남았다

        // 기간이 지난 딜을 재개하면 「마감을 고쳐 주세요」
        long expired = newDeal(author);
        clock.set(clock.instant().plus(Duration.ofDays(31)));
        end(expired, manager, "끝");
        reopen(expired, manager, null);
        assertThat(notices(author, "DEAL_REOPENED_FIX_ENDS", expired)).isEqualTo(1);
    }

    @Test
    void 감사는_행위자_회원_대상_글_boardId_role_reason() {
        long staff = staffOf(board, "STAFF");
        long post = newDeal(member());
        requestBy(post, 3);
        end(post, staff, "가격이 바뀌었어요");
        Map<String, Object> endRow = lastAudit(staff, "HOTDEAL_END");
        assertThat(endRow).isNotNull().containsEntry("actor_type", "MEMBER").containsEntry("actor_id", staff).containsEntry("target_type", "POST")
                .containsEntry("target_id", Long.toString(post)).containsEntry("ip", "10.1.0.1");
        Map<String, Object> endDetail = JSON.readValue((String) endRow.get("detail"), Map.class);
        assertThat(endDetail).containsEntry("boardId", Long.toString(boardId(board))).containsEntry("role", "STAFF").containsEntry("reason", "가격이 바뀌었어요")
                .containsEntry("before", "FLAGGED");

        reopen(post, staff, null);
        Map<String, Object> reopenRow = lastAudit(staff, "HOTDEAL_REOPEN");
        assertThat(reopenRow).isNotNull().containsEntry("actor_type", "MEMBER").containsEntry("target_id", Long.toString(post));
        Map<String, Object> reopenDetail = JSON.readValue((String) reopenRow.get("detail"), Map.class);
        assertThat(reopenDetail).containsEntry("boardId", Long.toString(boardId(board))).containsEntry("role", "STAFF").containsEntry("endedBy", "MANAGER")
                .doesNotContainKey("reason");

        // 거절은 남기지 않는다(상태가 바뀌지 않았다)
        long outsider = member();
        error(() -> end(post, outsider, "종료"));
        assertThat(lastAudit(outsider, "HOTDEAL_END")).isNull();
    }

    @Test
    void 글_상세_dealModeration_은_그_게시판_매니저_스텝만() {
        long manager = staffOf(board, "MANAGER");
        long staff = staffOf(board, "STAFF");
        long otherManager = staffOf(dealBoard(), "MANAGER");
        long suspended = staffOf(board, "MANAGER");
        jdbc.update("UPDATE users SET status = 'SUSPENDED' WHERE id = ?", suspended);
        long author = member();
        long post = newDeal(author);

        assertThat(moderationSeenBy(post, manager)).isEqualTo(new PostDealModerationView("MANAGER", true, false));
        assertThat(moderationSeenBy(post, staff)).isEqualTo(new PostDealModerationView("STAFF", true, false));
        assertThat(moderationSeenBy(post, member())).isNull();
        assertThat(moderationSeenBy(post, author)).isNull();
        assertThat(moderationSeenBy(post, otherManager)).isNull();
        assertThat(moderationSeenBy(post, suspended)).isNull();
        assertThat(moderationSeenBy(post, null)).isNull();

        requestBy(post, 3); // 의심도 종료할 수 있다
        assertThat(moderationSeenBy(post, staff)).isEqualTo(new PostDealModerationView("STAFF", true, false));
        end(post, manager, "종료");
        assertThat(moderationSeenBy(post, staff)).isEqualTo(new PostDealModerationView("STAFF", false, true));

        // 기간 지남 — 종료할 수 있다
        long expired = newDeal(author);
        clock.set(clock.instant().plus(Duration.ofDays(31)));
        assertThat(moderationSeenBy(expired, manager)).isEqualTo(new PostDealModerationView("MANAGER", true, false));

        // 일반 게시판 글은 매니저여도 null
        String general = generalBoard();
        long generalManager = staffOf(general, "MANAGER");
        long plain = generalPost(general);
        assertThat(moderationSeenBy(plain, generalManager)).isNull();
    }

    @Test
    void 작성자_본인이_매니저여도_작성자_조치와_따로_싣는다() {
        long authorManager = staffOf(board, "MANAGER");
        long post = newDeal(authorManager);
        PostDetailView detail = postQueries.detail(Long.toString(post), authorManager, "10.1.0.3");
        assertThat(detail.mine()).isTrue();
        assertThat(detail.dealModeration()).isEqualTo(new PostDealModerationView("MANAGER", true, false));

        states.end(Long.toString(post), authorManager); // 작성자 종료 — 되돌리기 기한이 생긴다
        detail = postQueries.detail(Long.toString(post), authorManager, "10.1.0.3");
        assertThat(detail.deal().reopenUntil()).isNotNull();
        assertThat(detail.dealModeration()).isEqualTo(new PostDealModerationView("MANAGER", false, true));
    }

    // ── HTTP ────────────────────────────────────────────────────────────────

    private static MockHttpServletRequestBuilder as(long memberId, MockHttpServletRequestBuilder request) {
        AuthUser user = new AuthUser(memberId, Role.USER);
        return request.principal(new UsernamePasswordAuthenticationToken(user, null, user.authorities())).contentType(MediaType.APPLICATION_JSON);
    }

    @Test
    void HTTP_종료_재개_모양과_오류_코드_글_상세_칸() throws Exception {
        installation.markCompleted();
        MockMvc mvc = MockMvcBuilders.webAppContextSetup(context).build();
        long manager = staffOf(board, "MANAGER");
        long post = newDeal(member());

        mvc.perform(as(manager, get("/api/posts/" + post))).andExpect(status().isOk()).andExpect(jsonPath("$.data.dealModeration.role").value("MANAGER"))
                .andExpect(jsonPath("$.data.dealModeration.canEnd").value(true)).andExpect(jsonPath("$.data.dealModeration.canReopen").value(false));
        mvc.perform(as(member(), get("/api/posts/" + post))).andExpect(status().isOk()).andExpect(jsonPath("$.data.dealModeration").value(org.hamcrest.Matchers.nullValue()));

        mvc.perform(as(manager, post("/api/posts/" + post + "/deal/moderate/end")).content("{}")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION")).andExpect(jsonPath("$.error.fields.reason").exists());
        mvc.perform(as(member(), post("/api/posts/" + post + "/deal/moderate/end")).content("{\"reason\":\"종료\"}")).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("DEAL_MODERATE_FORBIDDEN"));
        mvc.perform(as(manager, post("/api/posts/" + post + "/deal/moderate/end")).content("{\"reason\":\"품절\"}")).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store")).andExpect(jsonPath("$.data.status").value("ENDED"))
                .andExpect(jsonPath("$.data.endedBy").value("MANAGER"));
        mvc.perform(as(manager, post("/api/posts/" + post + "/deal/moderate/end")).content("{\"reason\":\"품절\"}")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("DEAL_ENDED"));
        mvc.perform(as(manager, post("/api/posts/" + post + "/deal/moderate/reopen"))).andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("ACTIVE"));
        mvc.perform(as(manager, post("/api/posts/" + post + "/deal/moderate/reopen")).content("{}")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("DEAL_NOT_ENDED"));
        mvc.perform(as(manager, post("/api/posts/" + ids.nextId() + "/deal/moderate/end")).content("{\"reason\":\"품절\"}")).andExpect(status().isNotFound());
    }

    /** CSRF 쿠키·헤더(SPA 방식 — 같은 값) — 비로그인 거절이 CSRF 403 이 아니라 401 로 나오는지 본다 */
    private static MockHttpServletRequestBuilder csrf(MockHttpServletRequestBuilder request) {
        String token = "t" + uniq();
        return request.cookie(new jakarta.servlet.http.Cookie("XSRF-TOKEN", token)).header("X-XSRF-TOKEN", token).contentType(MediaType.APPLICATION_JSON);
    }

    @Test
    void HTTP_비로그인은_401() throws Exception {
        installation.markCompleted();
        MockMvc secured = MockMvcBuilders.webAppContextSetup(context).addFilters(securityFilterChain).build();
        long post = newDeal(member());
        secured.perform(csrf(post("/api/posts/" + post + "/deal/moderate/end")).content("{\"reason\":\"종료\"}"))
                .andExpect(status().isUnauthorized());
        secured.perform(csrf(post("/api/posts/" + post + "/deal/moderate/reopen")).content("{}"))
                .andExpect(status().isUnauthorized());
        assertThat(dealRow(post)).containsEntry("status", "ACTIVE");
    }
}
