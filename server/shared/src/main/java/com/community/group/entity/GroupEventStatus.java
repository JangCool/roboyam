package com.community.group.entity;

/** 일정의 저장된 상태 — 열려 있음(ACTIVE) · 취소(CANCELED). 시작 전/진행 중/끝남은 시각으로 정한다({@link GroupEventEntity#state}) */
public enum GroupEventStatus {
    ACTIVE,
    CANCELED
}
