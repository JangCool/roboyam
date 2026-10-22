package com.community.board.dto.response;

/**
 * 지금 보는 사람 기준의 판정(user-web {@code BoardViewer}) — 화면은 이것으로 숨기거나 안내만 하고 실제 거절은 쓰기 API 가 같은 규칙으로 한다.
 *
 * @param guest 로그인하지 않았다
 * @param staff 이 게시판의 매니저·스텝(운영진 전용 말머리를 고를 수 있다). 운영자는 공개 사이트에 로그인하지 않는다
 */
public record BoardViewerView(boolean guest, boolean staff, boolean canWrite, String writeBlockedReason, boolean canComment, String commentBlockedReason) {
}
