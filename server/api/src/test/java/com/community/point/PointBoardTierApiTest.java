package com.community.point;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.community.auth.dto.AuthUser;
import com.community.board.service.BoardCatalog;
import com.community.commerce.CommerceApiTestBase;
import com.community.post.dto.request.PostWriteRequest;
import com.community.post.service.PostWriteService;
import com.community.point.service.PointPolicy;
import com.community.user.entity.Role;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * 게시판 등급별 포인트의 공개 사이트 연결(point-board-tier-contract) — 글쓰기가 게시판 등급(pointMode)·사진 수를 적립에 넘기고, 추천이 등급의 보류로 이어지고, 본인 삭제가 보류를 취소하며,
 * {@code GET /api/me/points/pending}·{@code GET /api/boards/{slug}} 의 {@code pointTier} 모양. 공개 사이트 앱 문맥 + 실제 PostgreSQL.
 */
class PointBoardTierApiTest extends CommerceApiTestBase {

    private static final String LONG = "정보 등급 최소 글자 수 백 자를 넉넉히 넘기는 본문입니다. ".repeat(6);

    @Autowired
    private WebApplicationContext context;
    @Autowired
    private PostWriteService posts;
    @Autowired
    private BoardCatalog catalog;
    @Autowired
    private PointPolicy policy;

    private MockMvc mvc;
    private Integer threshold;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
        threshold = jdbc.queryForObject("SELECT like_bonus_threshold FROM point_settings WHERE id = 1", Integer.class);
        jdbc.update("UPDATE point_settings SET like_bonus_threshold = 1 WHERE id = 1");
        policy.invalidate();
    }

    @AfterEach
    void restore() {
        jdbc.update("UPDATE point_settings SET like_bonus_threshold = ? WHERE id = 1", threshold);
        policy.invalidate();
    }

    private String board(String pointMode, Boolean minLengthApplied) {
        long id = ids.nextId();
        String slug = "tr" + Long.toString(id, 36);
        boardFixture.pointBoard(id, slug, "등급 " + slug.substring(slug.length() - 6), 900, pointMode, minLengthApplied);
        catalog.invalidate();
        return slug;
    }

    private long member() {
        long id = newMember();
        memberFixture.verifyEmail(id);
        memberFixture.profile(id, "t" + Long.toString(id, 36).substring(Math.max(0, Long.toString(id, 36).length() - 11)));
        return id;
    }

    /** 추천이 인정되는 회원 — 새싹보다 한 단계 높은 등급 */
    private long liker() {
        long id = member();
        jdbc.update("UPDATE profiles SET grade_key = (SELECT grade_key FROM grades ORDER BY level LIMIT 1 OFFSET 1) WHERE user_id = ?", id);
        return id;
    }

    private long write(long member, String slug, String content) {
        return Long.parseLong(posts.create(new PostWriteRequest(slug, null, "등급 시험 글", "TEXT", content, false, null, null, null), member, "127.0.0.1").id());
    }

    private List<Integer> earned(long member, String reason) {
        return jdbc.queryForList("SELECT delta FROM point_ledger WHERE user_id = ? AND reason = ?", Integer.class, member, reason);
    }

    private static MockHttpServletRequestBuilder as(long memberId, MockHttpServletRequestBuilder request) {
        AuthUser user = new AuthUser(memberId, Role.USER);
        return request.principal(new UsernamePasswordAuthenticationToken(user, null, user.authorities())).header("Accept-Language", "ko");
    }

    @Test
    void 글쓰기는_게시판_등급의_금액과_사진_최소를_쓴다() {
        String info = board("INFO", null);
        String core = board("CORE", null);
        long writer = member();
        long coreWriter = member();
        write(writer, info, LONG);
        write(coreWriter, core, LONG.repeat(3)); // 평문 글 — 올린 사진 0장이라 핵심 글(사진 1장)은 0
        assertThat(earned(writer, "POST")).containsExactly(30);
        assertThat(earned(coreWriter, "POST")).isEmpty();
    }

    @Test
    void 정보_게시판_글의_추천_보너스는_보류로_남고_본인이_지우면_취소된다() {
        String info = board("INFO", null);
        long writer = member();
        long postId = write(writer, info, LONG);
        posts.like(Long.toString(postId), liker());

        Map<String, Object> pending = jdbc.queryForMap("SELECT reason, amount, status FROM point_pending_grants WHERE ref_type = 'POST' AND ref_id = ?", postId);
        assertThat(pending).containsEntry("reason", "LIKE_BONUS").containsEntry("amount", 50).containsEntry("status", "PENDING");
        assertThat(earned(writer, "LIKE_BONUS")).isEmpty();

        posts.delete(Long.toString(postId), null, writer, "127.0.0.1");
        assertThat(jdbc.queryForMap("SELECT status, cancel_reason FROM point_pending_grants WHERE ref_type = 'POST' AND ref_id = ?", postId))
                .containsEntry("status", "CANCELLED").containsEntry("cancel_reason", "DELETED");
    }

    @Test
    void 확정_대기는_로그인한_본인의_대기_중_보류만_내역_줄과_같은_target_으로_준다() throws Exception {
        mvc.perform(get("/api/me/points/pending")).andExpect(status().isUnauthorized());

        String info = board("INFO", null);
        long writer = member();
        long postId = write(writer, info, LONG);
        posts.like(Long.toString(postId), liker());

        mvc.perform(as(writer, get("/api/me/points/pending"))).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")))
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].reason").value("LIKE_BONUS"))
                .andExpect(jsonPath("$.data.items[0].amount").value(50))
                .andExpect(jsonPath("$.data.items[0].releaseAt").exists())
                .andExpect(jsonPath("$.data.items[0].target.postId").value(Long.toString(postId)))
                .andExpect(jsonPath("$.data.items[0].target.title").value("등급 시험 글"))
                .andExpect(jsonPath("$.data.items[0].target.available").value(true));
        mvc.perform(as(member(), get("/api/me/points/pending"))).andExpect(status().isOk()).andExpect(jsonPath("$.data.items.length()").value(0));
    }

    @Test
    void 게시판_정보는_등급의_금액과_조건을_pointTier_로_준다() throws Exception {
        String info = board("INFO", null);
        mvc.perform(get("/api/boards/" + info)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pointTier.tier").value("INFO"))
                .andExpect(jsonPath("$.data.pointTier.postPoints").value(30))
                .andExpect(jsonPath("$.data.pointTier.likeBonusPoints").value(50))
                .andExpect(jsonPath("$.data.pointTier.likeThreshold").value(1))
                .andExpect(jsonPath("$.data.pointTier.featuredPoints").value(100))
                .andExpect(jsonPath("$.data.pointTier.minLength").value(100))
                .andExpect(jsonPath("$.data.pointTier.minImages").value(0))
                .andExpect(jsonPath("$.data.pointTier.holdHours").value(24));
        mvc.perform(get("/api/boards/" + board("CORE", false))).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pointTier.tier").value("CORE"))
                .andExpect(jsonPath("$.data.pointTier.minLength").value(0))
                .andExpect(jsonPath("$.data.pointTier.minImages").value(1));
        mvc.perform(get("/api/boards/" + board(null, null))).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pointTier.tier").value("NORMAL"))
                .andExpect(jsonPath("$.data.pointTier.postPoints").value(jdbc.queryForObject("SELECT post_points FROM point_settings WHERE id = 1", Integer.class)));
        mvc.perform(get("/api/boards/" + board("OFF", null))).andExpect(status().isOk()).andExpect(jsonPath("$.data.pointTier").doesNotExist());
    }

    @Test
    void 설치_프리셋의_게시판_포인트_값은_새_값이고_기본_배정을_따른다() throws Exception {
        tools.jackson.databind.JsonNode root;
        try (java.io.InputStream in = PointBoardTierApiTest.class.getResourceAsStream("/install-presets/irobit.structure.json")) {
            root = new tools.jackson.databind.ObjectMapper().readTree(in);
        }
        java.util.Map<String, String> modes = new java.util.HashMap<>();
        for (tools.jackson.databind.JsonNode table : root.get("tables")) {
            String name = table.get("table").asString();
            if (!name.equals("boards") && !name.equals("board_defaults")) {
                continue;
            }
            for (tools.jackson.databind.JsonNode row : table.get("rows")) {
                tools.jackson.databind.JsonNode mode = row.get("point_mode");
                String value = mode == null || mode.isNull() ? null : mode.asString();
                assertThat(value).as(name + " " + row.path("slug").asString()).isIn(null, "OFF", "NORMAL", "INFO", "CORE");
                modes.put(name.equals("boards") ? row.get("slug").asString() : "__defaults__", value);
            }
        }
        assertThat(modes).containsEntry("__defaults__", "NORMAL").containsEntry("news", "INFO").containsEntry("hotdeal", "INFO").containsEntry("tip", "CORE")
                .containsEntry("review", "CORE");
    }
}
