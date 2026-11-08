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
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.ResultActions;

/**
 * 운영 게시판 설정(V166 — 비밀글 {@code secretMode}·처리 상태 {@code statusEnabled})의 관리자 쪽 — 게시판 편집 칸(보내지 않으면 그대로, 정책 묶음 {@code board.settings},
 * 감사 {@code BOARD_UPDATE}, 일반 게시판만) · 글 관리의 {@code secret}·{@code processStatus} 칸과 거르기 · 처리 상태 바꾸기({@code post.manage}, 감사 {@code POST_PROCESS_STATUS}).
 */
class BoardOpsSettingsHttpTest extends Commerce04AdminTestBase {

    @Autowired
    private BoardCatalog catalog;

    private Cookie session;
    private long board;
    private String name;
    private final List<Long> extraBoards = new java.util.ArrayList<>();

    @BeforeEach
    void setUp() throws Exception {
        session = login(operatorWith(Permission.BOARD_READ, Permission.BOARD_MANAGE, Permission.BOARD_SETTINGS, Permission.POST_READ, Permission.POST_MANAGE));
        board = ids.nextId();
        name = "운영판 " + Long.toString(board, 36).substring(6);
        boardFixture.board(board, "op" + Long.toString(board, 36), name, "운영 게시판 시험", 901);
        catalog.invalidate();
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM member_notifications WHERE post_id IN (SELECT id FROM posts WHERE board_id = ?)", board);
        jdbc.update("DELETE FROM post_process_status_history WHERE board_id = ?", board);
        jdbc.update("DELETE FROM posts WHERE board_id = ?", board);
        jdbc.update("DELETE FROM board_prefixes WHERE board_id = ?", board);
        boardFixture.deleteBoard(board);
        for (long id : extraBoards) {
            jdbc.update("DELETE FROM board_prefixes WHERE board_id = ?", id);
            boardFixture.deleteBoard(id);
        }
        catalog.invalidate();
    }

    private ResultActions save(Cookie who, long boardId, String boardName, String extra) throws Exception {
        String body = "{\"name\":\"" + boardName + "\",\"description\":\"운영 게시판 시험\"" + extra + "}";
        return mvc.perform(withCsrf(put("/api/admin/boards/" + boardId), who).content(body));
    }

    private ResultActions changeStatus(Cookie who, long postId, String status) throws Exception {
        return mvc.perform(withCsrf(put("/api/admin/posts/" + postId + "/process-status"), who).content("{\"status\":\"" + status + "\"}"));
    }

    @Test
    void 처음엔_OFF_false_바꾸면_저장_감사_카탈로그에_보이고_보내지_않으면_그대로() throws Exception {
        mvc.perform(get("/api/admin/boards/" + board).cookie(session)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.secretMode").value("OFF")).andExpect(jsonPath("$.data.statusEnabled").value(false));

        save(session, board, name, ",\"secretMode\":\"optional\",\"statusEnabled\":true").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.secretMode").value("OPTIONAL")).andExpect(jsonPath("$.data.statusEnabled").value(true))
                .andExpect(jsonPath("$.data.diffCount").value(0));
        String audit = auditLogs.where().action("BOARD_UPDATE").target(Long.toString(board)).latest().orElseThrow().detail();
        assertThat(audit).contains("secretMode").contains("statusEnabled");
        catalog.invalidate();
        assertThat(catalog.snapshot().byId().get(board).secretMode()).isEqualTo("OPTIONAL");
        assertThat(catalog.snapshot().byId().get(board).statusEnabled()).isTrue();

        save(session, board, name, "").andExpect(status().isOk()).andExpect(jsonPath("$.data.secretMode").value("OPTIONAL"))
                .andExpect(jsonPath("$.data.statusEnabled").value(true));
        save(session, board, name, ",\"secretMode\":\"SOMETIMES\"").andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.fields.secretMode").exists());
    }

    @Test
    void 정책_권한이_없으면_403_일반_게시판이_아니면_400() throws Exception {
        Cookie manager = login(operatorWith(Permission.BOARD_READ, Permission.BOARD_MANAGE));
        save(manager, board, name, ",\"statusEnabled\":true").andExpect(status().isForbidden());
        assertThat(jdbc.queryForObject("SELECT status_enabled FROM boards WHERE id = ?", Boolean.class, board)).isFalse();

        long deal = ids.nextId();
        extraBoards.add(deal);
        String dealName = "운영핫딜 " + Long.toString(deal, 36).substring(6);
        boardFixture.board(deal, "od" + Long.toString(deal, 36), dealName, 902, "DEAL", "REGULAR");
        catalog.invalidate();
        save(session, deal, dealName, ",\"secretMode\":\"ALWAYS\",\"statusEnabled\":true").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fields.secretMode").exists()).andExpect(jsonPath("$.error.fields.statusEnabled").exists());
    }

    @Test
    void 글_관리는_비밀글과_처리_상태를_싣고_거르며_바꾸면_감사와_글쓴이_알림이_남는다() throws Exception {
        boardFixture.ops(board, "OPTIONAL", true);
        catalog.invalidate();
        long author = newMember();
        long secret = ids.nextId();
        boardFixture.post(secret, board, author, "비밀 운영 글", "운영 게시판 시험 본문입니다.", true, true);
        long legacy = ids.nextId();
        boardFixture.post(legacy, board, author, "켜기 전 글", "운영 게시판 시험 본문입니다.");

        String detail = mvc.perform(get("/api/admin/posts/" + secret).cookie(session)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat((Boolean) JsonPath.read(detail, "$.data.post.secret")).isTrue();
        assertThat((String) JsonPath.read(detail, "$.data.post.processStatus")).isEqualTo("RECEIVED");

        changeStatus(session, secret, "IN_PROGRESS").andExpect(status().isOk()).andExpect(jsonPath("$.data.processStatus").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.data.processStatusAt").exists());
        String audit = auditLogs.where().action("POST_PROCESS_STATUS").target(Long.toString(secret)).latest().orElseThrow().detail();
        assertThat(audit).contains("RECEIVED").contains("IN_PROGRESS");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM member_notifications WHERE user_id = ? AND type = 'POST_STATUS_CHANGED'", Integer.class, author))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT process_status_by FROM posts WHERE id = ?", Long.class, secret)).as("운영자는 회원 칸에 남기지 않는다").isNull();

        String received = mvc.perform(get("/api/admin/posts").param("board", Long.toString(board)).param("processStatus", "received").cookie(session))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        List<String> ids = JsonPath.read(received, "$.data.items[*].id");
        assertThat(ids).containsExactly(Long.toString(legacy));
        String progress = mvc.perform(get("/api/admin/posts").param("board", Long.toString(board)).param("processStatus", "IN_PROGRESS").cookie(session))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat((List<String>) JsonPath.read(progress, "$.data.items[*].id")).containsExactly(Long.toString(secret));

        changeStatus(session, secret, "NOPE").andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.fields.status").exists());
        boardFixture.ops(board, "OPTIONAL", false);
        catalog.invalidate();
        changeStatus(session, secret, "DONE").andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("POST_STATUS_DISABLED"));

        Cookie reader = login(operatorWith(Permission.POST_READ));
        changeStatus(reader, secret, "DONE").andExpect(status().isForbidden());
    }

    // ── V168 — 소모임 게시판 허용 · 메모 · 이력 ─────────────────────────────

    @Test
    void 소모임_게시판도_운영_설정을_켜고_핫딜은_계속_막는다() throws Exception {
        long group = ids.nextId();
        extraBoards.add(group);
        String groupName = "운영소모임 " + Long.toString(group, 36).substring(6);
        boardFixture.board(group, "og" + Long.toString(group, 36), groupName, 903, "GROUP", null);
        catalog.invalidate();
        save(session, group, groupName, ",\"secretMode\":\"ALWAYS\",\"statusEnabled\":true").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.secretMode").value("ALWAYS")).andExpect(jsonPath("$.data.statusEnabled").value(true));
        catalog.invalidate();
        assertThat(catalog.snapshot().byId().get(group).secretAlways()).as("카탈로그도 소모임 게시판 값을 받는다").isTrue();
        assertThat(catalog.snapshot().byId().get(group).statusEnabled()).isTrue();
    }

    @Test
    void 메모와_함께_바꾸면_이력_감사_알림에_남고_이력은_조회_권한으로_이름까지_읽는다() throws Exception {
        boardFixture.ops(board, "OFF", true);
        catalog.invalidate();
        long author = newMember();
        long post = ids.nextId();
        boardFixture.post(post, board, author, "메모 운영 글", "운영 게시판 시험 본문입니다.", false, true);

        mvc.perform(withCsrf(put("/api/admin/posts/" + post + "/process-status"), session).content("{\"status\":\"CHECKING\",\"note\":\"  다음 배포에서 고쳐요  \"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.processStatus").value("CHECKING"))
                .andExpect(jsonPath("$.data.processStatusNote").value("다음 배포에서 고쳐요"));
        // 글 상세(서랍)에도 마지막 메모가 실린다
        Cookie noteReader = login(operatorWith(Permission.POST_READ));
        mvc.perform(get("/api/admin/posts/" + post).cookie(noteReader)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.post.processStatusNote").value("다음 배포에서 고쳐요"));
        String audit = auditLogs.where().action("POST_PROCESS_STATUS").target(Long.toString(post)).latest().orElseThrow().detail();
        assertThat(audit).contains("다음 배포에서 고쳐요");
        String payload = jdbc.queryForObject("SELECT payload::text FROM member_notifications WHERE user_id = ? AND type = 'POST_STATUS_CHANGED'", String.class, author);
        assertThat(payload).contains("다음 배포에서 고쳐요");

        mvc.perform(withCsrf(put("/api/admin/posts/" + post + "/process-status"), session).content("{\"status\":\"DONE\",\"note\":\"" + "가".repeat(201) + "\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.fields.note").exists());
        changeStatus(session, post, "DONE").andExpect(status().isOk()).andExpect(jsonPath("$.data.processStatusNote").doesNotExist());

        Cookie reader = login(operatorWith(Permission.POST_READ));
        String history = mvc.perform(get("/api/admin/posts/" + post + "/process-status/history").cookie(reader)).andExpect(status().isOk()).andReturn()
                .getResponse().getContentAsString();
        assertThat((List<String>) JsonPath.read(history, "$.data.items[*].toStatus")).containsExactly("DONE", "CHECKING");
        assertThat((List<String>) JsonPath.read(history, "$.data.items[*].fromStatus")).containsExactly("CHECKING", "RECEIVED");
        assertThat((List<String>) JsonPath.read(history, "$.data.items[*].actor")).containsOnly("OPERATOR");
        assertThat((String) JsonPath.read(history, "$.data.items[1].note")).isEqualTo("다음 배포에서 고쳐요");
        assertThat((String) JsonPath.read(history, "$.data.items[0].actorName")).isNotBlank();

        Cookie nobody = login(operatorWith(Permission.BOARD_READ));
        mvc.perform(get("/api/admin/posts/" + post + "/process-status/history").cookie(nobody)).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/posts/1/process-status/history").cookie(reader)).andExpect(status().isNotFound());
    }
}
