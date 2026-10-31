package com.community.bizapplication.entity;

/** 신청 건 반려 사유 코드 — 관리자 화면 값이 정본(소재 규격·정책 위반·링크 오류·기간 불가·허위 가격·기타). 글은 따로 1~200자 */
public enum BizRejectCode {
    CREATIVE_SPEC, POLICY_VIOLATION, LINK_BROKEN, PERIOD_UNAVAILABLE, FALSE_PRICE, OTHER
}
