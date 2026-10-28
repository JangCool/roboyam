package com.community.groupbuy.entity;

/** 종료 사유({@code group_buys.close_reason}, 결정 79) — 목록 탭·「다음 차수 알림 받기」가 이 열을 본다 */
public enum CloseReason {
    SOLD_OUT, TIME_UP, EARLY_CLOSED, CANCELED
}
