package com.community.bizapplication.dto.request;

/** 사유 하나만 받는 본문 — 환불 요청(1~300자) · 운영자 취소·환불 거절(1~200자) */
public record BizReasonRequest(String reason) {
}
