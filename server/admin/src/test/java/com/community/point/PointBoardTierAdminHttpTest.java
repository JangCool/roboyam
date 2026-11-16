package com.community.point;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.community.board.service.BoardCatalog;
import com.community.permission.entity.Permission;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 게시판 등급별 포인트의 관리자 API(point-board-tier-contract) — 정책 {@code boardTiers}·스위치 조회·저장(일반 등급의 따르는 칸 무시, 칸 오류 키 {@code boardTiers.<등급>.<칸>}, 감사는 바뀐 칸만),
 * 우수 글 선정·취소({@code post.feature}, 보류·선정 기록·감사·목록 {@code featured}), 운영자 숨김의 보류 취소, 회원 묶음 응답의 {@code pending}. 실제 필터 체인·권한·PostgreSQL.
 */
class PointBoardTierAdminHttpTest extends PointAdminHttpTestBase {

    private static final List<String> COLUMNS = List.of("tier_normal_like_bonus_points", "tier_normal_featured_points", "tier_normal_min_images", "tier_normal_hold_hours",
            "tier_info_post_points", "tier_info_like_bonus_points", "tier_info_featured_points", "tier_info_min_length", "tier_info_min_images", "tier_info_hold_hours",
            "tier_core_post_points", "tier_core_like_bonus_points", "tier_core_featured_points", "tier_core_min_length", "tier_core_min_images", "tier_core_hold_hours",
            "info_source_once", "exclude_ai_and_ops", "post_points", "post_min_length");

    @Autowired
    private BoardCatalog catalog;

    private Map<String, Object> saved;
    private Cookie settings;

    @BeforeEach
    void snapshot() throws Exception {
        saved = jdbc.queryForMap("SELECT " + String.join(", ", COLUMNS) + " FROM point_settings WHERE id = 1");
        settings = login(operatorWith(Permission.POINT_READ, Permission.POINT_SETTINGS));
    }

    @AfterEach
    void restoreSettings() {
        saved.forEach((column, value) -> jdbc.update("UPDATE point_settings SET " + column + " = ? WHERE id = 1", value));
        policy.invalidate();
    }

    private DocumentContext current() throws Exception {
        String body = mvc.perform(get("/api/admin/settings/points").cookie(settings)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        DocumentContext data = JsonPath.parse(JsonPath.<Map<String, Object>>read(body, "$.data"));
        data.delete("$.updatedAt").delete("$.updatedBy");
        return data;
    }

    private org.springframework.test.web.servlet.ResultActions save(String json) throws Exception {
        return mvc.perform(withCsrf(put("/api/admin/settings/points"), settings).header("Accept-Language", "ko").content(json));
    }

    // ── 정책 등급 칸 ────────────────────────────────────────────────────────────

    @Test
    void 조회는_등급_칸과_스위치를_주고_일반_등급의_쓰면_바로_최소_글자_수는_전역_칸이다() throws Exception {
        mvc.perform(get("/api/admin/settings/points").cookie(settings)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.boardTiers.NORMAL.postPoints").value(saved.get("post_points")))
                .andExpect(jsonPath("$.data.boardTiers.NORMAL.minLength").value(saved.get("post_min_length")))
                .andExpect(jsonPath("$.data.boardTiers.NORMAL.likeBonusPoints").value(saved.get("tier_normal_like_bonus_points")))
                .andExpect(jsonPath("$.data.boardTiers.INFO.postPoints").value(saved.get("tier_info_post_points")))
                .andExpect(jsonPath("$.data.boardTiers.INFO.holdHours").value(saved.get("tier_info_hold_hours")))
                .andExpect(jsonPath("$.data.boardTiers.CORE.minImages").value(saved.get("tier_core_min_images")))
                .andExpect(jsonPath("$.data.boardTiers.CORE.featuredPoints").value(saved.get("tier_core_featured_points")))
                .andExpect(jsonPath("$.data.infoSourceOnce").value(saved.get("info_source_once")))
                .andExpect(jsonPath("$.data.excludeAiAndOps").value(saved.get("exclude_ai_and_ops")));
    }

    @Test
    void 등급_칸을_저장하고_일반_등급의_따르는_칸은_무시하며_감사에는_바뀐_칸만() throws Exception {
        DocumentContext body = current().set("$.boardTiers.INFO.postPoints", 41).set("$.boardTiers.NORMAL.likeBonusPoints", 12)
                .set("$.boardTiers.NORMAL.postPoints", 99999).set("$.boardTiers.NORMAL.minLength", 77).set("$.infoSourceOnce", false);
        save(body.jsonString()).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.boardTiers.INFO.postPoints").value(41))
                .andExpect(jsonPath("$.data.boardTiers.NORMAL.likeBonusPoints").value(12))
                .andExpect(jsonPath("$.data.boardTiers.NORMAL.postPoints").value(saved.get("post_points")))
                .andExpect(jsonPath("$.data.boardTiers.NORMAL.minLength").value(saved.get("post_min_length")))
                .andExpect(jsonPath("$.data.infoSourceOnce").value(false));
        assertThat(jdbc.queryForMap("SELECT tier_info_post_points, tier_normal_like_bonus_points, info_source_once, post_points, post_min_length FROM point_settings WHERE id = 1"))
                .containsEntry("tier_info_post_points", 41).containsEntry("tier_normal_like_bonus_points", 12).containsEntry("info_source_once", false)
                .containsEntry("post_points", saved.get("post_points")).containsEntry("post_min_length", saved.get("post_min_length"));
        String detail = auditLogs.where().action("POINT_SETTINGS_UPDATE").latest().orElseThrow().detail();
        Map<String, Object> after = JsonPath.read(detail, "$.after");
        assertThat(after).containsOnlyKeys("boardTiers.INFO.postPoints", "boardTiers.NORMAL.likeBonusPoints", "infoSourceOnce");
        assertThat(policy.tiers().info().postPoints()).as("이 서버의 정책 사본에 바로").isEqualTo(41);
    }

    @Test
    void 등급_칸이_범위_밖이거나_빠지면_400_boardTiers_등급_칸_키이고_저장하지_않는다() throws Exception {
        save(current().set("$.boardTiers.INFO.postPoints", 10001).set("$.boardTiers.CORE.holdHours", -1).jsonString()).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION"))
                .andExpect(jsonPath("$.error.fields['boardTiers.INFO.postPoints']").value("0 ~ 10,000 사이 정수로 입력해 주세요."))
                .andExpect(jsonPath("$.error.fields['boardTiers.CORE.holdHours']").exists());
        save(current().delete("$.boardTiers.CORE").delete("$.excludeAiAndOps").jsonString()).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fields['boardTiers.CORE.postPoints']").exists())
                .andExpect(jsonPath("$.error.fields['boardTiers.CORE.minImages']").exists())
                .andExpect(jsonPath("$.error.fields.excludeAiAndOps").value("켬 또는 끔을 골라 주세요."));
        save(current().set("$.boardTiers.NORMAL.postPoints", null).set("$.boardTiers.NORMAL.minLength", null).jsonString()) // 일반 등급의 따르는 칸은 보지 않는다
                .andExpect(status().isOk());
        assertThat(jdbc.queryForMap("SELECT " + String.join(", ", COLUMNS) + " FROM point_settings WHERE id = 1")).isEqualTo(saved);
    }

    // ── 우수 글 ────────────────────────────────────────────────────────────────

    private long pointBoard(String pointMode) {
        long id = ids.nextId();
        boardFixture.pointBoard(id, "ft" + Long.toString(id, 36), "우수 " + Long.toString(id, 36).substring(6), 901, pointMode, null);
        catalog.invalidate();
        return id;
    }

    private long postRow(long board, long author) {
        long id = ids.nextId();
        jdbc.update("INSERT INTO posts (id, board_id, author_id, title, content) VALUES (?, ?, ?, '우수 글 시험', '본문')", id, board, author);
        return id;
    }

    private Map<String, Object> pending(long postId, String reason) {
        return jdbc.queryForMap("SELECT status, cancel_reason, amount FROM point_pending_grants WHERE ref_type = 'POST' AND ref_id = ? AND reason = ? ORDER BY id DESC LIMIT 1",
                postId, reason);
    }

    @Test
    void 우수_글_선정은_post_feature_권한이고_보류_등급이면_보류_취소하면_보류도_취소된다() throws Exception {
        long author = newMember();
        long postId = postRow(pointBoard("CORE"), author);
        Cookie manager = login(operatorWith(Permission.POST_READ, Permission.POST_MANAGE));
        mvc.perform(withCsrf(post("/api/admin/posts/" + postId + "/feature"), manager)).andExpect(status().isForbidden());

        Cookie curator = login(operatorWith(Permission.POST_READ, Permission.POST_FEATURE));
        mvc.perform(withCsrf(post("/api/admin/posts/" + postId + "/feature"), curator)).andExpect(status().isNoContent());
        assertThat(pending(postId, "POST_FEATURED")).containsEntry("status", "PENDING").containsEntry("amount", 500);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM post_features WHERE post_id = ? AND unfeatured_at IS NULL AND author_id = ?", Integer.class, postId, author))
                .isEqualTo(1);
        mvc.perform(get("/api/admin/posts/" + postId).cookie(curator)).andExpect(status().isOk()).andExpect(jsonPath("$.data.post.featured").value(true));
        assertThat(auditLogs.where().action("POST_FEATURE").target(Long.toString(postId)).latest().orElseThrow().detail()).contains("heldPoints");
        mvc.perform(withCsrf(post("/api/admin/posts/" + postId + "/feature"), curator)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("POST_STATE_INVALID"));

        mvc.perform(withCsrf(delete("/api/admin/posts/" + postId + "/feature"), curator)).andExpect(status().isNoContent());
        assertThat(pending(postId, "POST_FEATURED")).containsEntry("status", "CANCELLED").containsEntry("cancel_reason", "UNFEATURED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM post_features WHERE post_id = ? AND unfeatured_at IS NOT NULL AND unfeatured_by IS NOT NULL", Integer.class,
                postId)).isEqualTo(1);
        mvc.perform(get("/api/admin/posts/" + postId).cookie(curator)).andExpect(status().isOk()).andExpect(jsonPath("$.data.post.featured").value(false));
        assertThat(auditLogs.where().action("POST_UNFEATURE").target(Long.toString(postId)).count()).isEqualTo(1);
        mvc.perform(withCsrf(delete("/api/admin/posts/" + postId + "/feature"), curator)).andExpect(status().isConflict());

        mvc.perform(withCsrf(post("/api/admin/posts/" + postId + "/feature"), curator)) // 다시 선정하면 새로 보류(원장 유일이라 확정은 한 번)
                .andExpect(status().isNoContent());
        assertThat(pending(postId, "POST_FEATURED")).containsEntry("status", "PENDING");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM post_features WHERE post_id = ?", Integer.class, postId)).as("이력은 남는다").isEqualTo(2);
    }

    @Test
    void 보류_없는_등급은_바로_주고_숨긴_글은_선정할_수_없고_끈_게시판은_선정만() throws Exception {
        jdbc.update("UPDATE point_settings SET tier_normal_featured_points = 70 WHERE id = 1");
        policy.invalidate();
        Cookie curator = login(operatorWith(Permission.POST_READ, Permission.POST_FEATURE));
        long author = newMember();
        long normal = postRow(pointBoard("NORMAL"), author);
        mvc.perform(withCsrf(post("/api/admin/posts/" + normal + "/feature"), curator)).andExpect(status().isNoContent());
        assertThat(jdbc.queryForList("SELECT delta FROM point_ledger WHERE user_id = ? AND reason = 'POST_FEATURED'", Integer.class, author)).containsExactly(70);

        long hidden = postRow(pointBoard("NORMAL"), author);
        jdbc.update("UPDATE posts SET hidden_at = now(), hidden_reason = 'STAFF' WHERE id = ?", hidden);
        mvc.perform(withCsrf(post("/api/admin/posts/" + hidden + "/feature"), curator)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("POST_STATE_INVALID"));

        long off = postRow(pointBoard("OFF"), author);
        mvc.perform(withCsrf(post("/api/admin/posts/" + off + "/feature"), curator)).andExpect(status().isNoContent());
        assertThat(jdbc.queryForList("SELECT delta FROM point_ledger WHERE user_id = ? AND reason = 'POST_FEATURED'", Integer.class, author)).containsExactly(70);
        mvc.perform(withCsrf(post("/api/admin/posts/" + ids.nextId() + "/feature"), curator)).andExpect(status().isNotFound());
    }

    @Test
    void 운영자가_글을_숨기면_대기_중인_추천_보너스_보류가_취소된다() throws Exception {
        long author = newMember();
        long postId = postRow(pointBoard("INFO"), author);
        jdbc.update("INSERT INTO point_pending_grants (id, user_id, reason, ref_type, ref_id, amount, release_at, created_at) "
                + "VALUES (?, ?, 'LIKE_BONUS', 'POST', ?, 50, now() + interval '1 day', now())", ids.nextId(), author, postId);
        Cookie manager = login(operatorWith(Permission.POST_READ, Permission.POST_MANAGE));
        mvc.perform(withCsrf(post("/api/admin/posts/" + postId + "/hide"), manager).content("{\"reason\":\"숨김 시험\"}")).andExpect(status().isNoContent());
        assertThat(pending(postId, "LIKE_BONUS")).containsEntry("status", "CANCELLED").containsEntry("cancel_reason", "HIDDEN");
    }

    @Test
    void 회원_묶음_응답에_확정_대기_목록이_있다() throws Exception {
        long author = newMember();
        long postId = postRow(pointBoard("INFO"), author);
        jdbc.update("INSERT INTO point_pending_grants (id, user_id, reason, ref_type, ref_id, amount, release_at, created_at) "
                + "VALUES (?, ?, 'LIKE_BONUS', 'POST', ?, 50, now() + interval '2 hour', now())", ids.nextId(), author, postId);
        Cookie reader = login(operatorWith(Permission.POINT_READ));
        mvc.perform(get("/api/admin/members/" + author + "/point-lots").cookie(reader)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pending.length()").value(1))
                .andExpect(jsonPath("$.data.pending[0].reason").value("LIKE_BONUS"))
                .andExpect(jsonPath("$.data.pending[0].refType").value("POST"))
                .andExpect(jsonPath("$.data.pending[0].refId").value(Long.toString(postId)))
                .andExpect(jsonPath("$.data.pending[0].postTitle").value("우수 글 시험"))
                .andExpect(jsonPath("$.data.pending[0].amount").value(50))
                .andExpect(jsonPath("$.data.pending[0].releaseAt").exists())
                .andExpect(jsonPath("$.data.pending[0].createdAt").exists());
        mvc.perform(get("/api/admin/members/" + newMember() + "/point-lots").cookie(reader)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pending.length()").value(0));
    }
}
