package com.community.avatarupload.entity;

/**
 * 아바타 올리기 신청 상태 — V157 {@code ck_avatar_upload_requests_status}(닉네임 이미지 V72 와 같은 뜻, commerce-motion-avatar-contract 규칙 0·0-2).
 */
public enum AvatarUploadStatus {

    /** 심사 대기 — 회원당 하나. 변경권 한 장이 예약돼 있다 */
    PENDING,
    /** 「내가 올린 아바타」 — 회원당 하나. 변경권을 썼다 */
    APPROVED,
    /** 반려 — 변경권 반환, 파일은 다음 신청 때까지 둔다 */
    REJECTED,
    /** 운영자가 내가 올린 아바타를 해제 — 변경권 반환 없음 */
    REVOKED,
    /** 회원이 대기 신청을 취소(변경권 반환)했거나 새 신청으로 대체됨(예약된 장은 새 신청으로) */
    CANCELED,
    /** 회원이 지웠거나(반환 없음) 다른 그림이 승인돼 바뀜 */
    REMOVED
}
