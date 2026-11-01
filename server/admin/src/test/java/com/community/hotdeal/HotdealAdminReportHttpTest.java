package com.community.hotdeal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.community.audit.dto.AuditActions;
import com.community.permission.entity.Permission;
import com.community.report.entity.ReportReasons;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/**
 * 신고 사건의 핫딜 조치(3단계) — 줄·상세 {@code deal: {view}|null} · {@code DEAL_END}(핫딜 글만 — 일반 글 409, 처리 글이 작성자 종료 사유, 글은 숨기지 않음) · 핫딜 사유 2개(핫딜 글만),
 * 관리자 글 목록 줄 {@code deal: boolean}, 위장 홍보 의심 후보 목록·상세 모양·무시(409·404·감사).
 */
class HotdealAdminReportHttpTest extends HotdealAdminTestBase {

    /** 글 신고 사건 하나(열림) */
    private long reportCase(long postId, long boardId, long authorId) {
        long id = ids.nextId();
        jdbc.update("INSERT INTO report_cases (id, target_type, target_id, post_id, board_id, target_author_id, status, report_count, first_reported_at, last_reported_at) "
                + "VALUES (?, 'POST', ?, ?, ?, ?, 'PENDING', 1, now(), now())", id, postId, postId, boardId, authorId);
        return id;
    }

    private static String resolve(String action, String reason, String note) {
        return "{\"action\":\"" + action + "\",\"reasonCode\":\"" + reason + "\",\"note\":\"" + note + "\"}";
    }

    @Test
    void 핫딜_글_사건은_DEAL_END_로_딜을_끝내고_글은_숨기지_않는다() throws Exception {
        assertThat(ReportReasons.EVERY).contains(ReportReasons.DEAL_FALSE_PRICE, ReportReasons.DEAL_HIDDEN_AD);
        long board = dealBoard();
        long author = newMember();
        long postId = deal(board, author, "r" + uniq() + ".shop.example");
        flag(postId);
        long requester = flaggedRequest(postId);
        long caseId = reportCase(postId, board, author);
        long operator = operatorWith(Permission.REPORT_READ, Permission.REPORT_MANAGE);
        Cookie cookie = login(operator);

        JsonNode before = data(mvc.perform(get("/api/admin/reports/" + caseId).cookie(cookie)).andExpect(status().isOk()).andReturn());
        assertThat(before.path("case").path("deal").path("view").asString()).isEqualTo("FLAGGED");
        // 처리 글 200자 넘음(작성자에게 가는 사유)
        mvc.perform(withCsrf(post("/api/admin/reports/" + caseId + "/resolve"), cookie).content(resolve("DEAL_END", "DEAL_FALSE_PRICE", "가".repeat(201))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.fields.note").exists());

        JsonNode after = data(mvc.perform(withCsrf(post("/api/admin/reports/" + caseId + "/resolve"), cookie)
                .content(resolve("DEAL_END", "DEAL_FALSE_PRICE", "가격이 사실과 달라 종료해요"))).andExpect(status().isOk()).andReturn());
        assertThat(after.path("case").path("action").asString()).isEqualTo("DEAL_END");
        assertThat(after.path("case").path("status").asString()).isEqualTo("RESOLVED");
        assertThat(after.path("case").path("deal").path("view").asString()).isEqualTo("ENDED");
        assertThat(dealRow(postId).get("ended_by")).isEqualTo("MANAGER");
        assertThat(dealRow(postId).get("end_note")).isEqualTo("가격이 사실과 달라 종료해요");
        assertThat(jdbc.queryForObject("SELECT hidden_at IS NULL FROM posts WHERE id = ?", Boolean.class, postId)).isTrue();
        assertThat(notices(author, "DEAL_MANAGER_ENDED", postId)).isEqualTo(1);
        assertThat(rewards(requester)).isEqualTo(1);
        assertThat(lastAudit(operator, AuditActions.REPORT_RESOLVE)).isNotNull();
        assertThat((String) lastAudit(operator, AuditActions.HOTDEAL_END).get("detail")).contains(Long.toString(caseId));

        // 이미 끝난 딜의 새 사건 — DEAL_END 는 409
        long again = reportCase(postId, board, author);
        mvc.perform(withCsrf(post("/api/admin/reports/" + again + "/resolve"), cookie).content(resolve("DEAL_END", "DEAL_HIDDEN_AD", "다시")))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("REPORT_ACTION_INVALID"));
        // 핫딜 글에는 핫딜 사유로 숨김도 된다
        mvc.perform(withCsrf(post("/api/admin/reports/" + again + "/resolve"), cookie).content(resolve("HIDE", "DEAL_HIDDEN_AD", "광고를 숨겼어요")))
                .andExpect(status().isOk());
    }

    @Test
    void 일반_글_사건은_DEAL_END_409_핫딜_사유_400_deal_은_null() throws Exception {
        long board = generalBoard();
        long author = newMember();
        long postId = boardPost(board, author, "일반 글");
        long caseId = reportCase(postId, board, author);
        Cookie cookie = login(operatorWith(Permission.REPORT_READ, Permission.REPORT_MANAGE));

        JsonNode list = data(mvc.perform(get("/api/admin/reports").param("postId", Long.toString(postId)).cookie(cookie)).andExpect(status().isOk()).andReturn());
        assertThat(list.path("items").get(0).has("deal")).isTrue();
        assertThat(list.path("items").get(0).path("deal").isNull()).isTrue();
        mvc.perform(withCsrf(post("/api/admin/reports/" + caseId + "/resolve"), cookie).content(resolve("DEAL_END", "SPAM_AD", "종료")))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("REPORT_ACTION_INVALID"));
        mvc.perform(withCsrf(post("/api/admin/reports/" + caseId + "/resolve"), cookie).content(resolve("HIDE", "DEAL_FALSE_PRICE", "숨김")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.fields.reasonCode").exists());
        mvc.perform(withCsrf(post("/api/admin/reports/" + caseId + "/dismiss"), cookie).content("{\"reasonCode\":\"DEAL_HIDDEN_AD\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.fields.reasonCode").exists());
        assertThat(jdbc.queryForObject("SELECT status FROM report_cases WHERE id = ?", String.class, caseId)).isEqualTo("PENDING");
    }

    @Test
    void 관리자_글_목록_줄은_핫딜_글이면_deal_true() throws Exception {
        long dealBoard = dealBoard();
        long author = newMember();
        long dealPost = deal(dealBoard, author, "p" + uniq() + ".shop.example");
        long bare = boardPost(dealBoard, author, "딜 줄 없는 옛 글");
        long general = boardPost(generalBoard(), author, "일반 글");
        Cookie cookie = login(operatorWith(Permission.POST_READ));
        mvc.perform(get("/api/admin/posts").param("board", Long.toString(dealBoard)).cookie(cookie)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[?(@.id == '" + dealPost + "')].deal").value(true))
                .andExpect(jsonPath("$.data.items[?(@.id == '" + bare + "')].deal").value(false));
        mvc.perform(get("/api/admin/posts/" + general).cookie(cookie)).andExpect(status().isOk()).andExpect(jsonPath("$.data.post.deal").value(false));
    }

    // ── 의심 후보 ─────────────────────────────────────────────────────────

    private long suspicion(String kind, Long memberId, String host, long... postIds) {
        long id = ids.nextId();
        StringBuilder arr = new StringBuilder("{");
        for (int i = 0; i < postIds.length; i++) {
            arr.append(i == 0 ? "" : ",").append(postIds[i]);
        }
        arr.append("}");
        jdbc.update("INSERT INTO deal_suspicions (id, kind, status, member_id, host, store_name, post_count, member_count, other_ratio_percent, window_days, post_ids, "
                        + "detected_at, updated_at) VALUES (?, ?, 'OPEN', ?, ?, '시험몰', ?, ?, ?, ?, ?::bigint[], now(), now())", id, kind, memberId, host, postIds.length,
                "STORE".equals(kind) ? 2 : 1, "STORE".equals(kind) ? 10 : null, "STORE".equals(kind) ? 30 : 7, arr.toString());
        return id;
    }

    @Test
    void 의심_후보_목록과_상세는_종류별_칸을_싣고_무시는_한_번만() throws Exception {
        long board = dealBoard();
        long a = newMember();
        long b = newMember();
        String host = domain("sp");
        long p1 = deal(board, a, host);
        long p2 = deal(board, a, host);
        long p3 = deal(board, b, host);
        long other = deal(board, b, "o" + uniq() + ".shop.example");
        Cookie reader = login(operatorWith(Permission.HOTDEAL_READ));
        JsonNode base = data(mvc.perform(get("/api/admin/hotdeal-suspicions").param("status", "OPEN").cookie(reader)).andReturn());

        long member = suspicion("MEMBER", a, host, p2, p1);
        long store = suspicion("STORE", null, host, p3, p2, p1);
        JsonNode page = data(mvc.perform(get("/api/admin/hotdeal-suspicions").param("status", "OPEN").param("size", "50").cookie(reader))
                .andExpect(status().isOk()).andReturn());
        assertThat(page.path("counts").path("OPEN").asLong() - base.path("counts").path("OPEN").asLong()).isEqualTo(2);
        assertThat(page.path("kindCounts").path("MEMBER").asLong() - base.path("kindCounts").path("MEMBER").asLong()).isEqualTo(1);
        assertThat(page.path("kindCounts").path("ALL").asLong() - base.path("kindCounts").path("ALL").asLong()).isEqualTo(2);
        JsonNode m = item(page, member);
        assertThat(m.path("kind").asString()).isEqualTo("MEMBER");
        assertThat(m.path("member").path("id").asString()).isEqualTo(Long.toString(a));
        assertThat(m.path("othersCount").asInt()).isEqualTo(1); // b 가 같은 판매처에
        assertThat(m.has("memberCount")).isFalse();
        assertThat(m.path("host").asString()).isEqualTo(host);
        assertThat(m.path("lastPostAt").isNull()).isFalse();
        assertThat(m.path("dismissedBy").isNull()).isTrue();
        JsonNode s = item(page, store);
        assertThat(s.path("memberCount").asInt()).isEqualTo(2);
        assertThat(s.path("otherRatioPercent").asInt()).isEqualTo(10);
        assertThat(s.has("member")).isFalse();
        assertThat(s.has("othersCount")).isFalse();

        JsonNode memberDetail = data(mvc.perform(get("/api/admin/hotdeal-suspicions/" + member).cookie(reader)).andExpect(status().isOk()).andReturn());
        assertThat(memberDetail.path("posts")).hasSize(2);
        assertThat(memberDetail.path("posts").get(0).path("postId").asString()).isEqualTo(Long.toString(p2));
        assertThat(memberDetail.path("posts").get(0).path("view").asString()).isEqualTo("ACTIVE");
        assertThat(memberDetail.path("posts").get(0).path("author").path("id").asString()).isEqualTo(Long.toString(a));
        assertThat(memberDetail.path("members")).isEmpty();
        JsonNode storeDetail = data(mvc.perform(get("/api/admin/hotdeal-suspicions/" + store).cookie(reader)).andReturn());
        assertThat(storeDetail.path("posts")).hasSize(3);
        assertThat(storeDetail.path("members")).hasSize(2);
        JsonNode first = storeDetail.path("members").get(0);
        assertThat(first.path("member").path("id").asString()).isEqualTo(Long.toString(a)); // 이 판매처 글이 많은 회원부터
        assertThat(first.path("postCount").asLong()).isEqualTo(2);
        assertThat(storeDetail.path("members").get(1).path("otherDealCount").asLong()).isEqualTo(1); // b 의 다른 판매처 글
        assertThat(other).isPositive();

        long operator = operatorWith(Permission.HOTDEAL_MANAGE);
        Cookie manager = login(operator);
        mvc.perform(withCsrf(post("/api/admin/hotdeal-suspicions/" + member + "/dismiss"), reader).content("{}")).andExpect(status().isForbidden());
        mvc.perform(withCsrf(post("/api/admin/hotdeal-suspicions/" + member + "/dismiss"), manager).content("{\"note\":\"" + "가".repeat(201) + "\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.fields.note").exists());
        mvc.perform(withCsrf(post("/api/admin/hotdeal-suspicions/" + member + "/dismiss"), manager).content("{\"note\":\"지인 추천이라 괜찮아요\"}"))
                .andExpect(status().isNoContent());
        var row = jdbc.queryForMap("SELECT status, dismissed_at, dismissed_by, dismiss_note FROM deal_suspicions WHERE id = ?", member);
        assertThat(row.get("status")).isEqualTo("DISMISSED");
        assertThat(row.get("dismissed_at")).isNotNull();
        assertThat(row.get("dismissed_by")).isEqualTo(operator);
        assertThat(row.get("dismiss_note")).isEqualTo("지인 추천이라 괜찮아요");
        assertThat(lastAudit(operator, AuditActions.HOTDEAL_SUSPICION_DISMISS)).isNotNull();
        mvc.perform(withCsrf(post("/api/admin/hotdeal-suspicions/" + member + "/dismiss"), manager)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("HOTDEAL_STATE_CHANGED"));
        mvc.perform(withCsrf(post("/api/admin/hotdeal-suspicions/" + ids.nextId() + "/dismiss"), manager)).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("HOTDEAL_SUSPICION_NOT_FOUND"));
        JsonNode dismissed = data(mvc.perform(get("/api/admin/hotdeal-suspicions/" + member).cookie(reader)).andReturn()).path("suspicion");
        assertThat(dismissed.path("status").asString()).isEqualTo("DISMISSED");
        assertThat(dismissed.path("dismissedBy").asString()).isEqualTo(operatorName(operator));
        assertThat(dismissed.path("note").asString()).isEqualTo("지인 추천이라 괜찮아요");
        jdbc.update("DELETE FROM deal_suspicions WHERE id IN (?, ?)", member, store);
    }

    private static JsonNode item(JsonNode page, long id) {
        for (JsonNode i : page.path("items")) {
            if (i.path("id").asString().equals(Long.toString(id))) {
                return i;
            }
        }
        throw new AssertionError("후보가 없다: " + id);
    }
}
