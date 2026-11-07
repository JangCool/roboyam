package com.community.rewardevent.dto;

/**
 * 보상 이벤트를 다루는 운영자(관리자 서버의 {@code AdminActor} 에서 뽑은 값) — 고액 권한({@code point.adjust.large}) 여부와, 고액이 필요한데 없을 때 남길 거부 감사(따로 커밋 — 권한 거부 관례).
 *
 * @param recordLargeDenied 고액 권한이 없어 막을 때 부른다(관리자 서버의 {@code AccessDeniedRecorder}) — 부른 뒤 서비스가 403 을 던진다
 */
public record RewardOperator(long id, String ip, boolean canLarge, Runnable recordLargeDenied) {

    @Override
    public String toString() {
        return "RewardOperator[id=" + id + ", canLarge=" + canLarge + "]";
    }
}
