package com.community.grade.dto;

import com.community.grade.entity.GradeManualPolicy;
import java.util.List;
import java.util.Map;

/**
 * 검사를 마친 저장 계획 — 저장과 미리보기가 같이 쓴다.
 *
 * @param grades    서열 순서의 최종 등급 목록
 * @param policy    저장할 옵션
 * @param removedTo 지울 등급 키 → 그 회원이 갈 등급 키(저장돼 있는 서열에서 바로 아래 남아 있는 등급). 지금 저장된 서열 순서
 */
public record GradePlan(List<PlannedGrade> grades, GradeManualPolicy policy, Map<String, String> removedTo) {
}
