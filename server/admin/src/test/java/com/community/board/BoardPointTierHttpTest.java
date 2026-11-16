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
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 게시판 포인트 등급 {@code pointMode} = OFF · NORMAL · INFO · CORE(point-board-tier-contract, V238 — 옛 ON 은 받지 않는다). 게시판 정책·기본값 저장과 카탈로그의 유효값.
 */
class BoardPointTierHttpTest extends Commerce04AdminTestBase {

    @Autowired
    private BoardCatalog catalog;

    private Cookie session;
    private long board;

    @BeforeEach
    void setUp() throws Exception {
        session = login(operatorWith(Permission.BOARD_READ, Permission.BOARD_MANAGE, Permission.BOARD_SETTINGS));
        board = ids.nextId();
        boardFixture.board(board, "pt" + Long.toString(board, 36), "등급 " + Long.toString(board, 36).substring(6), "등급 시험 게시판", 902);
        catalog.invalidate();
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("UPDATE board_defaults SET point_mode = 'NORMAL' WHERE id = 1");
        boardFixture.deleteBoard(board);
        catalog.invalidate();
    }

    private String detail() throws Exception {
        return mvc.perform(get("/api/admin/boards/" + board).cookie(session)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    private org.springframework.test.web.servlet.ResultActions savePolicy(String pointMode) throws Exception {
        DocumentContext policy = JsonPath.parse(JsonPath.<Map<String, Object>>read(detail(), "$.data.policy"));
        policy.set("$.pointMode", pointMode);
        String name = JsonPath.read(detail(), "$.data.name");
        String body = "{\"name\":\"" + name + "\",\"description\":\"등급 시험 게시판\",\"policy\":" + policy.jsonString() + "}";
        return mvc.perform(withCsrf(put("/api/admin/boards/" + board), session).header("Accept-Language", "ko").content(body));
    }

    @Test
    void 게시판_등급을_저장하면_카탈로그의_유효값이_되고_옛_ON_은_400이다() throws Exception {
        assertThat((String) JsonPath.read(detail(), "$.data.defaults.pointMode")).as("기본값 = 일반").isEqualTo("NORMAL");
        assertThat(catalog.snapshot().byId().get(board).pointMode()).isEqualTo("NORMAL");

        for (String tier : new String[] {"INFO", "CORE", "OFF", "NORMAL"}) {
            savePolicy(tier).andExpect(status().isOk()).andExpect(jsonPath("$.data.policy.pointMode").value(tier));
            catalog.invalidate();
            assertThat(catalog.snapshot().byId().get(board).pointMode()).isEqualTo(tier);
            assertThat(catalog.snapshot().byId().get(board).pointsEnabled()).isEqualTo(!"OFF".equals(tier));
        }
        savePolicy("ON").andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("VALIDATION"));
        assertThat(jdbc.queryForObject("SELECT point_mode FROM boards WHERE id = ?", String.class, board)).isEqualTo("NORMAL");
    }

    @Test
    void 기본값도_등급을_받고_칸이_빈_게시판은_기본값을_따른다() throws Exception {
        String body = mvc.perform(get("/api/admin/boards/defaults").cookie(session)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        DocumentContext defaults = JsonPath.parse(JsonPath.<Map<String, Object>>read(body, "$.data.defaults"));
        mvc.perform(withCsrf(put("/api/admin/boards/defaults"), session).header("Accept-Language", "ko").content(defaults.set("$.pointMode", "CORE").jsonString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.defaults.pointMode").value("CORE"));
        catalog.invalidate();
        assertThat(catalog.snapshot().byId().get(board).pointMode()).isEqualTo("CORE");
        mvc.perform(withCsrf(put("/api/admin/boards/defaults"), session).header("Accept-Language", "ko").content(defaults.set("$.pointMode", "ON").jsonString()))
                .andExpect(status().isBadRequest());
    }
}
