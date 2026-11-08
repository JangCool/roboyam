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
import org.springframework.test.web.servlet.ResultActions;

/**
 * 글 목록 말머리·상태 칩 위치(V169 — post-chip-position-contract)의 관리자 쪽.
 * 사이트 기본 = 게시판 기본 설정 일반 {@code defaults.listChipPosition}(기본 ABOVE, 빠지면 그대로, 모르는 값 400) ·
 * 게시판 칸 {@code chipPosition}(null = 사이트 기본, 빈 글자로 되돌림, 빠지면 그대로, 모르는 값 400, 기본 정보 묶음 {@code board.manage}, 감사 {@code BOARD_UPDATE}) ·
 * 유효값(게시판 ?? 사이트 기본)은 카탈로그가 푼다. 목록 윗줄 숫자 강조(V170 — {@code listLikeHighlight}·{@code listViewHighlight}·{@code listHighlightColor})도 같은 기본 설정 칸이다.
 */
class BoardChipPositionHttpTest extends Commerce04AdminTestBase {

    @Autowired
    private BoardCatalog catalog;

    private Cookie session;
    private long board;
    private String name;

    @BeforeEach
    void setUp() throws Exception {
        session = login(operatorWith(Permission.BOARD_READ, Permission.BOARD_MANAGE, Permission.BOARD_SETTINGS));
        board = ids.nextId();
        name = "칩위치 " + Long.toString(board, 36).substring(6);
        boardFixture.board(board, "cp" + Long.toString(board, 36), name, "칩 위치 시험", 904);
        catalog.invalidate();
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("UPDATE board_defaults SET list_chip_position = 'ABOVE', list_like_highlight = 10, list_view_highlight = 1000, list_highlight_color = '#e5484d' WHERE id = 1");
        jdbc.update("DELETE FROM board_prefixes WHERE board_id = ?", board);
        boardFixture.deleteBoard(board);
        catalog.invalidate();
    }

    private ResultActions save(Cookie who, String extra) throws Exception {
        String body = "{\"name\":\"" + name + "\",\"description\":\"칩 위치 시험\"" + extra + "}";
        return mvc.perform(withCsrf(put("/api/admin/boards/" + board), who).content(body));
    }

    private DocumentContext defaults() throws Exception {
        String body = mvc.perform(get("/api/admin/boards/defaults").cookie(session)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JsonPath.parse(JsonPath.<Map<String, Object>>read(body, "$.data.defaults"));
    }

    private ResultActions saveDefaults(String json) throws Exception {
        return mvc.perform(withCsrf(put("/api/admin/boards/defaults"), session).content(json));
    }

    @Test
    void 게시판_칸은_처음_null_고르면_저장_감사_빈_글자면_사이트_기본으로_되돌린다() throws Exception {
        mvc.perform(get("/api/admin/boards/" + board).cookie(session)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.chipPosition").isEmpty())
                .andExpect(jsonPath("$.data.defaults.listChipPosition").value("ABOVE"));
        assertThat(catalog.snapshot().byId().get(board).chipPosition()).as("null 이면 사이트 기본").isEqualTo("ABOVE");

        save(session, ",\"chipPosition\":\"before\"").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.chipPosition").value("BEFORE")).andExpect(jsonPath("$.data.diffCount").value(0));
        assertThat(jdbc.queryForObject("SELECT chip_position FROM boards WHERE id = ?", String.class, board)).isEqualTo("BEFORE");
        String audit = auditLogs.where().action("BOARD_UPDATE").target(Long.toString(board)).latest().orElseThrow().detail();
        assertThat(audit).contains("chipPosition").contains("BEFORE");
        catalog.invalidate();
        assertThat(catalog.snapshot().byId().get(board).chipPosition()).isEqualTo("BEFORE");

        save(session, "").andExpect(status().isOk()).andExpect(jsonPath("$.data.chipPosition").value("BEFORE"));

        save(session, ",\"chipPosition\":\"SIDE\"").andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.fields.chipPosition").exists());
        assertThat(jdbc.queryForObject("SELECT chip_position FROM boards WHERE id = ?", String.class, board)).isEqualTo("BEFORE");

        save(session, ",\"chipPosition\":\"\"").andExpect(status().isOk()).andExpect(jsonPath("$.data.chipPosition").isEmpty());
        assertThat(jdbc.queryForObject("SELECT chip_position FROM boards WHERE id = ?", String.class, board)).isNull();
    }

    @Test
    void 목록_모양_칸이라_기본_정보_권한으로_저장하고_정책_권한만으로는_403() throws Exception {
        Cookie manager = login(operatorWith(Permission.BOARD_READ, Permission.BOARD_MANAGE));
        save(manager, ",\"chipPosition\":\"BELOW\"").andExpect(status().isOk()).andExpect(jsonPath("$.data.chipPosition").value("BELOW"));

        Cookie settingsOnly = login(operatorWith(Permission.BOARD_READ, Permission.BOARD_SETTINGS));
        save(settingsOnly, ",\"chipPosition\":\"ABOVE\"").andExpect(status().isForbidden());
        assertThat(jdbc.queryForObject("SELECT chip_position FROM boards WHERE id = ?", String.class, board)).isEqualTo("BELOW");
    }

    @Test
    void 사이트_기본은_게시판_기본_설정에_저장하고_빠지면_그대로_모르는_값은_400_칸이_빈_게시판이_따른다() throws Exception {
        DocumentContext current = defaults();
        assertThat((String) current.read("$.listChipPosition")).isEqualTo("ABOVE");

        saveDefaults(current.set("$.listChipPosition", "below").jsonString()).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.defaults.listChipPosition").value("BELOW"));
        assertThat(jdbc.queryForObject("SELECT list_chip_position FROM board_defaults WHERE id = 1", String.class)).isEqualTo("BELOW");
        String audit = auditLogs.where().action("BOARD_DEFAULTS_UPDATE").latest().orElseThrow().detail();
        assertThat(audit).contains("listChipPosition").contains("BELOW");
        catalog.invalidate();
        assertThat(catalog.snapshot().byId().get(board).chipPosition()).as("칸이 빈 게시판은 사이트 기본을 따른다").isEqualTo("BELOW");

        DocumentContext withoutField = defaults();
        withoutField.delete("$.listChipPosition");
        saveDefaults(withoutField.jsonString()).andExpect(status().isOk()).andExpect(jsonPath("$.data.defaults.listChipPosition").value("BELOW"));

        saveDefaults(defaults().set("$.listChipPosition", "SIDE").jsonString()).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fields.listChipPosition").exists());
        assertThat(jdbc.queryForObject("SELECT list_chip_position FROM board_defaults WHERE id = 1", String.class)).isEqualTo("BELOW");
    }

    @Test
    void 숫자_강조_기준과_색은_처음_값이_있고_범위_모양을_보며_색은_소문자로_빠지면_그대로() throws Exception {
        DocumentContext current = defaults();
        assertThat((Integer) current.read("$.listLikeHighlight")).isEqualTo(10);
        assertThat((Integer) current.read("$.listViewHighlight")).isEqualTo(1000);
        assertThat((String) current.read("$.listHighlightColor")).isEqualTo("#e5484d");

        saveDefaults(current.set("$.listLikeHighlight", 0).set("$.listViewHighlight", 10_000_000).set("$.listHighlightColor", " #1A2B3C ").jsonString())
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.defaults.listLikeHighlight").value(0))
                .andExpect(jsonPath("$.data.defaults.listViewHighlight").value(10_000_000))
                .andExpect(jsonPath("$.data.defaults.listHighlightColor").value("#1a2b3c"));
        assertThat(jdbc.queryForObject("SELECT list_highlight_color FROM board_defaults WHERE id = 1", String.class)).isEqualTo("#1a2b3c");
        String audit = auditLogs.where().action("BOARD_DEFAULTS_UPDATE").latest().orElseThrow().detail();
        assertThat(audit).contains("listLikeHighlight").contains("listViewHighlight").contains("listHighlightColor");

        DocumentContext withoutFields = defaults();
        withoutFields.delete("$.listLikeHighlight").delete("$.listViewHighlight").delete("$.listHighlightColor");
        saveDefaults(withoutFields.jsonString()).andExpect(status().isOk()).andExpect(jsonPath("$.data.defaults.listLikeHighlight").value(0))
                .andExpect(jsonPath("$.data.defaults.listViewHighlight").value(10_000_000))
                .andExpect(jsonPath("$.data.defaults.listHighlightColor").value("#1a2b3c"));

        saveDefaults(defaults().set("$.listLikeHighlight", 100_001).set("$.listViewHighlight", -1).set("$.listHighlightColor", "red").jsonString())
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.fields.listLikeHighlight").exists())
                .andExpect(jsonPath("$.error.fields.listViewHighlight").exists()).andExpect(jsonPath("$.error.fields.listHighlightColor").exists());
        saveDefaults(defaults().set("$.listHighlightColor", "#12345").jsonString()).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fields.listHighlightColor").exists());
        assertThat(jdbc.queryForObject("SELECT list_like_highlight FROM board_defaults WHERE id = 1", Integer.class)).isZero();
    }

    @Test
    void 따로_고른_게시판은_기본값_화면의_overrides_에_실린다() throws Exception {
        save(session, ",\"chipPosition\":\"BEFORE\"").andExpect(status().isOk());
        String body = mvc.perform(get("/api/admin/boards/defaults").cookie(session)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        List<String> ids = JsonPath.read(body, "$.data.overrides.listChipPosition[*].id");
        assertThat(ids).contains(Long.toString(board));
    }
}
