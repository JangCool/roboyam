package com.community.title.dto.request;

/** 사유 하나 — 회수({@code DELETE …/grants/{grantId}}, 필수)·신청 거절(필수)·신청 승인(선택 메모) */
public record AdminTitleReasonRequest(String reason) {
}
