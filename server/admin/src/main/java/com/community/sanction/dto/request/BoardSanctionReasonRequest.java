package com.community.sanction.dto.request;

/** 운영자의 게시판 제재 해제·무효 요청 — 사유 1~200자(결정 11) */
public record BoardSanctionReasonRequest(String reason) {
}
