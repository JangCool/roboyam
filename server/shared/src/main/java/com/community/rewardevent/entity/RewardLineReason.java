package com.community.rewardevent.entity;

/** 줄 결과 사유(화면 rewardEvent.reason.<코드>) — 실패: 동결·탈퇴·정지·품목 판매 불가·서버 오류 / 건너뜀: 이미 가진 영구 아이템·이미 가진 팩 / 회수: 쓴 아이템·0P 바닥 덜 회수·직접 전달 기록만 */
public enum RewardLineReason {
    POINT_FROZEN, WITHDRAWN, SUSPENDED, ITEM_UNAVAILABLE, SERVER_ERROR, ALREADY_OWNED, PACK_ALREADY_OWNED, ITEM_USED, POINT_PARTIAL, MANUAL_RECORD_ONLY
}
