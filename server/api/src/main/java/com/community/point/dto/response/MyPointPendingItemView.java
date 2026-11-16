package com.community.point.dto.response;

import java.time.Instant;

/**
 * 내 포인트 「확정 대기」 한 줄 — user-web {@code MyPointPendingItem}(point-board-tier-contract 「보류 확정 방식」). 보류 시간이 지나면 지급된다(그 사이 글이 지워지거나 숨겨지면 취소).
 *
 * @param reason    LIKE_BONUS · POST_FEATURED
 * @param amount    확정 때 받을 양(보류를 시작한 순간의 정책값)
 * @param releaseAt 확정 예정 시각
 * @param target    그 보상이 걸린 글 — 내역 줄의 {@code target} 과 같은 모양(댓글이면 그 댓글이 달린 글, 볼 수 없으면 {@code available=false}·{@code title=null})
 */
public record MyPointPendingItemView(String id, String reason, int amount, Instant releaseAt, MyPointLedgerTargetView target) {
}
