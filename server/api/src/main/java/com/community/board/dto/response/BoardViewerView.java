package com.community.board.dto.response;

import com.community.boardstaff.dto.response.BoardPendingWarningView;
import com.community.boardstaff.dto.response.BoardStaffToolsView;
import com.community.boardstaff.dto.response.BoardWriteRestrictionView;
import java.util.List;

/**
 * 지금 보는 사람 기준의 판정(user-web {@code BoardViewer}) — 화면은 이것으로 숨기거나 안내만 하고 실제 거절은 쓰기 API 가 같은 규칙으로 한다.
 *
 * @param guest            로그인하지 않았다
 * @param staff            이 게시판의 매니저·스텝(운영진 전용 말머리를 고를 수 있다). 운영자는 공개 사이트에 로그인하지 않는다
 * @param staffTools       그 게시판 매니저·스텝 본인에게만(계정 정상·인증 + 처리 권한 하나 이상) — 할 수 있는 일 · 오늘 남은 글·댓글·제재 한도 · 매니저 여부
 *                         (board-staff-action-contract). 그 밖·소모임 게시판은 null
 * @param writeRestriction 보는 내가 이 게시판에서 쓰기 제한 중이면 끝·사유(처리자 없음), 아니면 null. 그때 {@code writeBlockedReason}·{@code commentBlockedReason} 은
 *                         다른 까닭이 없으면 {@code BOARD_WRITE_RESTRICTED}
 * @param pendingWarnings  보는 내가 이 게시판에서 받고 아직 확인하지 않은 경고(최근 것이 위, 처리자 없음) — 게시판 머리 안내. 비로그인·소모임 게시판은 빈 목록
 */
public record BoardViewerView(boolean guest, boolean staff, boolean canWrite, String writeBlockedReason, boolean canComment, String commentBlockedReason,
                              BoardStaffToolsView staffTools, BoardWriteRestrictionView writeRestriction,
                              List<BoardPendingWarningView> pendingWarnings) {
}
