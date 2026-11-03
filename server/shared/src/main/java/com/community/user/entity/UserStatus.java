package com.community.user.entity;

/** 회원 상태. users.status 의 CHECK 와 같은 값이다. */
public enum UserStatus {
    ACTIVE,
    /** 기간 정지 */
    SUSPENDED,
    /** 영구 정지 */
    BANNED,
    /**
     * 탈퇴 진행 중(탈퇴 예약 — commerce.md 17-1 D1) — 남은 일(진행 중 주문·환불·광고 등)이 끝나면 배치가 익명화해 {@link #WITHDRAWN} 으로 바꾼다.
     * 로그인은 되고(gone 아님) 남은 일 처리·탈퇴 취소만 할 수 있다. 공개 노출(프로필·검색·쪽지 받기)과 새 활동(글·참여·결제 등)은 막힌다.
     */
    WITHDRAWING,
    /** 탈퇴 — 로그인할 수 없다 */
    WITHDRAWN
}
