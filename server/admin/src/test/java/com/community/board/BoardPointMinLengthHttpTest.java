package com.community.board;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.community.board.service.BoardCatalog;
import com.community.commerce.Commerce04AdminTestBase;
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
 * 게시판 정책 「적립 최소 글자 수 적용」 {@code pointMinLengthApplied}(커머스 1-3 — commerce-stage1-contract 「1-3 화면 목업」) — 게시판 칸은 nullable(null = 기본값 따름),
 * 기본값은 boolean(기본 켬), 기본값 화면의 {@code overrides} 에 따로 정한 게시판이 실린다, 유효값(게시판 ?? 기본값)과 유효 {@code pointMode}(ON 이면 적립)를 카탈로그가 만든다.
 */
class BoardPointMinLengthHttpTest extends Commerce04AdminTestBase {

    @Autowired
    private BoardCatalog catalog;

    private Cookie session;
    private long board;

    @BeforeEach
    void setUp() throws Exception {
        session = login(operatorWith(Permission.BOARD_READ, Permission.BOARD_MANAGE, Permission.BOARD_SETTINGS));
        board = ids.nextId();
        jdbc.update("INSERT INTO boards (id, slug, name, description, sort_order) VALUES (?, ?, ?, '적립 시험 게시판', 900)", board, "pm" + Long.toString(board, 36),
                "적립 " + Long.toString(board, 36).substring(6));
        catalog.invalidate();
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("UPDATE board_defaults SET point_min_length_applied = true WHERE id = 1");
        jdbc.update("DELETE FROM board_prefixes WHERE board_id = ?", board);
        jdbc.update("DELETE FROM boards WHERE id = ?", board);
        catalog.invalidate();
    }

    private String detail() throws Exception {
        return mvc.perform(get("/api/admin/boards/" + board).cookie(session)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    /** 게시판 저장 — 이름·설명은 그대로, 정책만 바꾼다 */
    private org.springframework.test.web.servlet.ResultActions savePolicy(Object pointMinLengthApplied, String pointMode) throws Exception {
        DocumentContext policy = JsonPath.parse(JsonPath.<Map<String, Object>>read(detail(), "$.data.policy"));
        policy.set("$.pointMinLengthApplied", pointMinLengthApplied).set("$.pointMode", pointMode);
        String name = JsonPath.read(detail(), "$.data.name");
        String body = "{\"name\":\"" + name + "\",\"description\":\"적립 시험 게시판\",\"policy\":" + policy.jsonString() + "}";
        return mvc.perform(withCsrf(put("/api/admin/boards/" + board), session).header("Accept-Language", "ko").content(body));
    }

    @Test
    void 게시판_칸은_처음엔_null_기본값_따름이고_끄면_저장되며_다시_null로_돌릴_수_있다() throws Exception {
        assertThat((Object) JsonPath.read(detail(), "$.data.policy.pointMinLengthApplied")).isNull();
        assertThat((Boolean) JsonPath.read(detail(), "$.data.defaults.pointMinLengthApplied")).isTrue();
        assertThat(catalog.snapshot().byId().get(board).pointMinLengthApplied()).as("유효값 = 기본값(켬)").isTrue();
        assertThat(catalog.snapshot().byId().get(board).pointsEnabled()).as("기본값 pointMode ON").isTrue();

        savePolicy(false, "OFF").andExpect(status().isOk()).andExpect(jsonPath("$.data.policy.pointMinLengthApplied").value(false))
                .andExpect(jsonPath("$.data.policy.pointMode").value("OFF"));
        assertThat(jdbc.queryForMap("SELECT point_min_length_applied, point_mode FROM boards WHERE id = ?", board)).containsEntry("point_min_length_applied", false)
                .containsEntry("point_mode", "OFF");
        catalog.invalidate();
        assertThat(catalog.snapshot().byId().get(board).pointMinLengthApplied()).isFalse();
        assertThat(catalog.snapshot().byId().get(board).pointsEnabled()).as("pointMode OFF 면 적립하지 않는다").isFalse();
        String audit = jdbc.queryForObject("SELECT detail::text FROM audit_logs WHERE action = 'BOARD_UPDATE' AND target_id = ? ORDER BY occurred_at DESC, id DESC LIMIT 1",
                String.class, Long.toString(board));
        assertThat(audit).contains("policy.pointMinLengthApplied");

        savePolicy(null, null).andExpect(status().isOk()).andExpect(jsonPath("$.data.policy.pointMinLengthApplied").isEmpty());
        assertThat(jdbc.queryForObject("SELECT point_min_length_applied FROM boards WHERE id = ?", Boolean.class, board)).isNull();
        catalog.invalidate();
        assertThat(catalog.snapshot().byId().get(board).pointMinLengthApplied()).isTrue();
    }

    @Test
    void 따로_정한_게시판은_기본값_화면의_overrides_에_실린다() throws Exception {
        savePolicy(false, null).andExpect(status().isOk());
        String body = mvc.perform(get("/api/admin/boards/defaults").cookie(session)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        List<String> ids = JsonPath.read(body, "$.data.overrides.pointMinLengthApplied[*].id");
        assertThat(ids).contains(Long.toString(board));
    }

    @Test
    void 기본값은_boolean으로_저장하고_빈_값은_400이며_칸이_빈_게시판이_따른다() throws Exception {
        String body = mvc.perform(get("/api/admin/boards/defaults").cookie(session)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.defaults.pointMinLengthApplied").value(true)).andReturn().getResponse().getContentAsString();
        DocumentContext defaults = JsonPath.parse(JsonPath.<Map<String, Object>>read(body, "$.data.defaults"));

        mvc.perform(withCsrf(put("/api/admin/boards/defaults"), session).header("Accept-Language", "ko").content(defaults.set("$.pointMinLengthApplied", null)
                .jsonString())).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.fields.pointMinLengthApplied").exists());
        assertThat(jdbc.queryForObject("SELECT point_min_length_applied FROM board_defaults WHERE id = 1", Boolean.class)).isTrue();

        mvc.perform(withCsrf(put("/api/admin/boards/defaults"), session).content(defaults.set("$.pointMinLengthApplied", false).jsonString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.defaults.pointMinLengthApplied").value(false));
        assertThat(jdbc.queryForObject("SELECT point_min_length_applied FROM board_defaults WHERE id = 1", Boolean.class)).isFalse();
        catalog.invalidate();
        assertThat(catalog.snapshot().byId().get(board).pointMinLengthApplied()).as("칸이 빈 게시판은 기본값을 따른다").isFalse();
        String audit = jdbc.queryForObject("SELECT detail::text FROM audit_logs WHERE action = 'BOARD_DEFAULTS_UPDATE' ORDER BY occurred_at DESC, id DESC LIMIT 1",
                String.class);
        assertThat(audit).contains("pointMinLengthApplied");
    }
}
