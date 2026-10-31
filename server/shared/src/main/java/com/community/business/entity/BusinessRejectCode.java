package com.community.business.entity;

/** 반려 사유 코드(운영자가 고른다 — 글 1~200자는 따로) — 서류 판독 불가 · 정보 불일치 · 휴·폐업 · 중복 · 기타 */
public enum BusinessRejectCode {
    DOC_UNREADABLE, INFO_MISMATCH, NTS_CLOSED, DUPLICATE, OTHER
}
