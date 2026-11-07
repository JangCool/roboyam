package com.community.rewardevent.dto.response;

/** 일괄 대상 미리 보기(admin-web {@code RewardTargetPreview}) — 대상 수 · 늘 빼는 수(정지·탈퇴) · 운영자 회원 계정 */
public record RewardTargetPreviewView(long count, long excludedSuspended, long excludedOperators) {
}
