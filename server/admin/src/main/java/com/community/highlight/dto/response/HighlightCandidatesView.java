package com.community.highlight.dto.response;

import com.community.post.dto.response.AdminPostRefView;
import java.util.List;

/**
 * {@code GET /api/admin/highlights/candidates} — 하이라이트로 보낼 만한 글(추천 많은 순, 같으면 최신, 최대 30개). 요청 값을 표준으로 돌려준다.
 *
 * @param days    최근 며칠 안에 쓴 글(0 = 전체 기간)
 * @param boardId 한 게시판만(전체면 null)
 * @param place   이 곳에 아직 없는 글만 찾았다(보내지 않았으면 home)
 */
public record HighlightCandidatesView(int days, int minLikes, String boardId, String place, List<AdminPostRefView> items) {
}
