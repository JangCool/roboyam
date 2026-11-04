package com.community.boardstaff.dto.response;

import com.community.sanction.service.BoardStaffActionService;
import java.time.Instant;

/**
 * 게시판 응답의 {@code viewer.staffTools}(user-web {@code BoardStaffTools}, board-staff-action-contract 「API > 공개」) — 그 게시판 매니저·스텝이고 계정 정상·인증이며
 * 네 처리 권한(글 숨기기 · 글 삭제 · 댓글 삭제 · 제재) 중 하나라도 있는 본인에게만(그 밖·소모임 게시판은 null).
 *
 * @param manager   매니저(그 게시판 처리·제재 전체를 보고 되돌림) · 거짓이면 스텝(내가 한 것만)
 * @param abilities 할 수 있는 일 — {@code restore} = 매니저 + {@code board.staff-action.restore}
 * @param post      오늘 글 처리 한도(숨기기 + 삭제 합산)
 * @param comment   오늘 댓글 삭제 한도
 * @param sanction  오늘 제재 한도(경고 + 쓰기 제한)
 * @param resetsAt  한도가 다시 채워지는 시각(다음 한국 시간 0시)
 */
public record BoardStaffToolsView(boolean manager, BoardStaffAbilitiesView abilities, BoardStaffQuotaView post, BoardStaffQuotaView comment,
                                  BoardStaffQuotaView sanction, Instant resetsAt) {

    public static BoardStaffToolsView of(BoardStaffActionService.Tools t) {
        return new BoardStaffToolsView(t.manager(), new BoardStaffAbilitiesView(t.postHide(), t.postDelete(), t.commentDelete(), t.sanction(), t.restore()),
                BoardStaffQuotaView.of(t.post()), BoardStaffQuotaView.of(t.comment()), BoardStaffQuotaView.of(t.sanctionQuota()), t.resetsAt());
    }
}
