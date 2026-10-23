package com.community.batch.dto.response;

/**
 * 배치의 단계 하나(작업이 정한 차례대로) — 한 단계짜리 배치도 1개가 있다.
 *
 * @param name       키 — 요청의 {@code steps} 값
 * @param label      서버가 요청 언어로 푼 이름
 * @param selectable 이 단계만 골라 돌릴 수 있나. false 인 단계는 고르지 못하고 늘 돈다(확정 규칙 2)
 */
public record BatchStepDefView(String name, String label, boolean selectable) {
}
