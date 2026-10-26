package com.community.group.entity;

/** 소모임 상태 — V90 {@code groups.status} CHECK 와 같은 값. 목록·검색에는 {@link #ACTIVE} 만 나온다 */
public enum GroupStatus {
    /** 개설 신청 중(신청자만 본다) */
    PENDING,
    /** 열림 */
    ACTIVE,
    /** 개설 반려(신청자만 본다) */
    REJECTED,
    /** 운영 조치로 비활성(상세는 안내만) */
    INACTIVE,
    /** 해산·신청 취소 */
    DELETED;

    /** 모임장 1인 상한·같은 이름 막기에 드는 상태(신청 중·열림·비활성) */
    public boolean live() {
        return this == PENDING || this == ACTIVE || this == INACTIVE;
    }
}
