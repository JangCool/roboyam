package com.community.board;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.community.board.service.BoardCatalog;
import com.community.commerce.Commerce04AdminTestBase;
import com.community.permission.entity.Permission;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 게시판 「이 게시판에 광고 숨김」 {@code adsHidden}(V117 — ad-contract 「공개 노출 목업」 확정 결정 2026-11-01) — 관리자 게시판 편집 요청·응답의 칸(기본 false), 보내지 않으면 그대로,
 * 바꾸면 감사 {@code BOARD_UPDATE} before/after 에 담기고 카탈로그 유효값({@code EffectiveBoard.adsHidden})이 따른다. 정책 묶음이라 {@code board.settings} 가 없으면 403(가정).
 */
class BoardAdsHiddenHttpTest extends Commerce04AdminTestBase {

    @Autowired
    private BoardCatalog catalog;

    private Cookie session;
    private long board;
    private String name;

    @BeforeEach
    void setUp() throws Exception {
        session = login(operatorWith(Permission.BOARD_READ, Permission.BOARD_MANAGE, Permission.BOARD_SETTINGS));
        board = ids.nextId();
        name = "광고숨김 " + Long.toString(board, 36).substring(6);
        boardFixture.board(board, "ah" + Long.toString(board, 36), name, "광고 숨김 시험", 900);
        catalog.invalidate();
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM board_prefixes WHERE board_id = ?", board);
        boardFixture.deleteBoard(board);
        catalog.invalidate();
    }

    private org.springframework.test.web.servlet.ResultActions save(Cookie who, String extra) throws Exception {
        String body = "{\"name\":\"" + name + "\",\"description\":\"광고 숨김 시험\"" + extra + "}";
        return mvc.perform(withCsrf(put("/api/admin/boards/" + board), who).content(body));
    }

    @Test
    void 처음엔_false_켜면_저장되고_응답_감사_카탈로그에_보인다_보내지_않으면_그대로() throws Exception {
        mvc.perform(get("/api/admin/boards/" + board).cookie(session)).andExpect(status().isOk()).andExpect(jsonPath("$.data.adsHidden").value(false));
        assertThat(catalog.snapshot().byId().get(board).adsHidden()).isFalse();

        save(session, ",\"adsHidden\":true").andExpect(status().isOk()).andExpect(jsonPath("$.data.adsHidden").value(true));
        assertThat(jdbc.queryForObject("SELECT ads_hidden FROM boards WHERE id = ?", Boolean.class, board)).isTrue();
        String audit = auditLogs.where().action("BOARD_UPDATE").target(Long.toString(board)).latest().orElseThrow().detail();
        assertThat(audit).contains("adsHidden");
        catalog.invalidate();
        assertThat(catalog.snapshot().byId().get(board).adsHidden()).isTrue();

        // 칸을 보내지 않으면 그대로
        save(session, "").andExpect(status().isOk()).andExpect(jsonPath("$.data.adsHidden").value(true));
        save(session, ",\"adsHidden\":false").andExpect(status().isOk()).andExpect(jsonPath("$.data.adsHidden").value(false));
        assertThat(jdbc.queryForObject("SELECT ads_hidden FROM boards WHERE id = ?", Boolean.class, board)).isFalse();
    }

    @Test
    void 정책_권한_board_settings_가_없으면_광고_숨김을_바꿀_수_없다() throws Exception {
        Cookie manager = login(operatorWith(Permission.BOARD_READ, Permission.BOARD_MANAGE));
        save(manager, ",\"adsHidden\":true").andExpect(status().isForbidden());
        assertThat(jdbc.queryForObject("SELECT ads_hidden FROM boards WHERE id = ?", Boolean.class, board)).isFalse();
        String detail = mvc.perform(get("/api/admin/boards/" + board).cookie(manager)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat((Boolean) JsonPath.read(detail, "$.data.adsHidden")).isFalse();
    }
}
