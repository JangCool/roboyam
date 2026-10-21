package com.community.operator.dto.request;

import jakarta.validation.constraints.Size;

/** 승인 대기(신청) 반려 — 사유는 선택이다(비어 있어도 된다, 200자 이하). 반려하면 신청이 목록에서 사라진다 */
public record OperatorRejectRequest(@Size(max = 200, message = "{operator.reject.reason.max}") String reason) {
}
