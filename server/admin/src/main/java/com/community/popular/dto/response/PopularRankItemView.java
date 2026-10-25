package com.community.popular.dto.response;

import com.community.post.dto.response.AdminPostRefView;

/**
 * 인기글 순위 한 줄.
 *
 * @param rank         공개 인기글과 같은 번호 — 스냅샷에서 지금 볼 수 없는 글을 거른 뒤 1부터 이어서. 뺀 글({@code excluded})이면 null
 * @param snapshotRank 계산한 스냅샷의 순위 그대로
 * @param score        계산 시점의 점수(소수)
 * @param fill         채운 줄의 출처 YESTERDAY(어제 글로 채움) · LAST_WEEK(지난주 글로 채움), 그 기간 자기 글이면 null
 * @param excluded     계산 뒤에 인기글에서 뺀 글 — 다시 계산하면 사라진다(공개 인기글에는 이미 나오지 않는다)
 * @param post         글 한 줄(추천·댓글·조회는 <b>지금</b> 값 — 점수만 계산 시점 값)
 */
public record PopularRankItemView(Integer rank, int snapshotRank, double score, String fill, boolean excluded, AdminPostRefView post) {
}
