package com.community.bizapplication.dto;

/** 처리한 운영자 — 감사·처리 기록에 남긴다(관리자 앱의 {@code AdminActor} 에서 뽑는다) */
public record BizOperator(long operatorId, String ip) {
}
