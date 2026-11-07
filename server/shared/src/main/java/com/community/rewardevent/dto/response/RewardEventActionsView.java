package com.community.rewardevent.dto.response;

/**
 * 서버가 상태로 정한 동작(admin-web {@code RewardEventActions}) — 권한은 화면이 따로 본다.
 *
 * @param cancelBlockedReason {@code HAS_GRANTS}(지급이 하나라도 있다 — 취소 대신 회수) 또는 null
 * @param grantBlockedReason  {@code NO_PENDING} · {@code NOT_CLOSED} · {@code RUNNING} 또는 null
 */
public record RewardEventActionsView(String editScope, boolean canStart, boolean canClose, boolean canDraw, boolean canGrant, boolean canRetry, boolean canCancel,
                                     boolean canAddRecipients, String cancelBlockedReason, String grantBlockedReason) {
}
