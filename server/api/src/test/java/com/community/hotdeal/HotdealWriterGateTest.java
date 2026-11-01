package com.community.hotdeal;

import static org.assertj.core.api.Assertions.assertThat;

import com.community.board.dto.response.BoardDetailView;
import com.community.board.dto.response.BoardView;
import com.community.board.service.PublicBoardService;
import com.community.zconfiguration.exception.CommonException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 핫딜 쓰기 자격이 게시판 쓰기 판정에 들었는지(3단계 공개 손질 ①②) — 새싹·이메일 미인증·비회원은 게시판 응답 {@code viewer.writeBlockedReason}·쓸 수 있는 게시판 목록에서 미리 막히고
 * (폼을 다 채운 뒤에야 403 을 받지 않게), 핫딜 {@code 403 GRADE_TOO_LOW} 는 필요한 등급({@code minGrade}·{@code minGradeLabel})을 싣는다. 일반 게시판 판정은 그대로다.
 */
class HotdealWriterGateTest extends HotdealApiTestBase {

    @Autowired
    private PublicBoardService boards;

    private String reason(String slug, Long userId) {
        BoardDetailView view = boards.board(slug, userId);
        return view.viewer().writeBlockedReason();
    }

    private boolean writable(String slug, Long userId) {
        return boards.writable(userId).boards().stream().map(BoardView::slug).anyMatch(slug::equals);
    }

    @Test
    void 새싹_미인증은_핫딜_게시판_응답과_쓸_수_있는_목록에서_미리_막힌다() {
        jdbc.update("UPDATE boards SET write_min = NULL WHERE slug = ?", board); // 게시판 기본(첫 등급)이어도 핫딜은 새싹을 막는다
        catalog.invalidate();
        long sprout = member("SEEDLING", true);
        long unverified = member("REGULAR", false);
        long regular = member();

        assertThat(reason(board, sprout)).isEqualTo("GRADE_TOO_LOW");
        assertThat(boards.board(board, sprout).viewer().canWrite()).isFalse();
        assertThat(writable(board, sprout)).isFalse();
        assertThat(reason(board, unverified)).isEqualTo("EMAIL_NOT_VERIFIED");
        assertThat(writable(board, unverified)).isFalse();
        assertThat(reason(board, regular)).isNull();
        assertThat(writable(board, regular)).isTrue();

        // 일반 게시판(같은 최소 자격)은 새싹도 쓴다 — 핫딜 게시판만의 자격이다
        String general = generalBoard();
        jdbc.update("UPDATE boards SET write_min = NULL WHERE slug = ?", general);
        catalog.invalidate();
        assertThat(reason(general, sprout)).isNull();
    }

    @Test
    void 비회원은_로그인이_필요하고_매니저_스텝은_등급_면제() {
        // 핫딜 게시판은 비회원 글쓰기(GUEST)를 DB 가 막는다(ck_boards_guest_kind) — 판정도 비회원이면 LOGIN_REQUIRED
        jdbc.update("UPDATE boards SET write_min = NULL WHERE slug = ?", board);
        catalog.invalidate();
        assertThat(reason(board, null)).isEqualTo("LOGIN_REQUIRED");
        assertThat(writable(board, null)).isFalse();
        long staffSprout = member("SEEDLING", true);
        jdbc.update("INSERT INTO board_staff (board_id, user_id, role) VALUES (?, ?, 'STAFF')", boardId(board), staffSprout);
        assertThat(reason(board, staffSprout)).isNull();
        assertThat(reason(board, member("SEEDLING", true))).isEqualTo("GRADE_TOO_LOW");
    }

    @Test
    void 핫딜_GRADE_TOO_LOW_는_필요한_등급을_싣는다() {
        jdbc.update("UPDATE boards SET write_min = NULL WHERE slug = ?", board);
        catalog.invalidate();
        CommonException e = error(() -> write(member("SEEDLING", true), board, deal(url("shop.example.com"))));
        assertThat(e.getErrorCode()).isEqualTo("GRADE_TOO_LOW");
        assertThat(e.getExtras()).containsEntry("minGrade", "REGULAR");
        String label = jdbc.queryForObject("SELECT label FROM grades WHERE grade_key = 'REGULAR'", String.class);
        assertThat(e.getExtras()).containsEntry("minGradeLabel", label);

        // 게시판 최소 자격이 더 높으면 그 등급
        jdbc.update("UPDATE boards SET write_min = 'LOYAL' WHERE slug = ?", board);
        catalog.invalidate();
        CommonException higher = error(() -> write(member("REGULAR", true), board, deal(url("shop.example.com"))));
        assertThat(higher.getErrorCode()).isEqualTo("GRADE_TOO_LOW");
        assertThat(higher.getExtras()).containsEntry("minGrade", "LOYAL");
    }
}
