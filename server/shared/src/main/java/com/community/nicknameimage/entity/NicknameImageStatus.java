package com.community.nicknameimage.entity;

/** 닉네임 그림 신청 상태 — V72 의 CHECK 와 같은 값. 뜻은 {@code V72__nickname_images.sql} 머리말 */
public enum NicknameImageStatus {
    /** 검토 대기(회원당 하나) */
    PENDING,
    /** 지금 적용 중(회원당 하나) */
    APPROVED,
    /** 반려 — 반려 + 72시간 뒤 다시 신청 */
    REJECTED,
    /** 운영자가 적용 중인 그림을 해제 — 해제 + 7일 뒤 다시 신청 */
    REVOKED,
    /** 회원이 대기 신청을 취소했거나 새 신청으로 대체됨 */
    CANCELED,
    /** 회원이 적용 중인 그림을 해제했거나 다른 그림이 승인돼 바뀜 */
    REMOVED
}
