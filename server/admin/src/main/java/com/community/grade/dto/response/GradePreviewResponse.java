package com.community.grade.dto.response;

import java.util.List;

/**
 * 저장 전 영향 — 이 기준으로 저장하면 승급될 회원 수({@code items}·{@code total})와, 지우는 등급에서 아래 등급으로 이동할 회원 수({@code willMove}).
 * 승급 인원에는 이동하는 회원을 넣지 않는다. 일수 조건으로 올라갈 회원도 센다(그 회원은 저장 직후가 아니라 다음 배치에서 올라간다).
 */
public record GradePreviewResponse(List<GradePreviewItem> items, long total, List<GradeMoveItem> willMove) {
}
