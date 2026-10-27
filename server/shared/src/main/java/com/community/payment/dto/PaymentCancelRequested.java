package com.community.payment.dto;

/** 취소를 대기열에 넣었다 — 커밋 뒤 그 자리에서 한 번 시도한다({@code PaymentCancelImmediateAttempt}, D10 「즉시 시도」) */
public record PaymentCancelRequested(long cancelId) {
}
