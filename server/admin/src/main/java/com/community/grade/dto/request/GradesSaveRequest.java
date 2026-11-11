package com.community.grade.dto.request;

import java.util.List;

/**
 * 등급 목록과 옵션의 <b>일괄 저장</b>(미리보기도 같은 모양) — 서버가 지금 저장된 목록과 비교해 추가·삭제·순서·수정을 처리한다. 요청에 빠진 기존 등급은 지우는 것이다.
 *
 * @param grades       서열 순서의 등급(2~11개)
 * @param manualPolicy {@code CONTINUE_AUTO} · {@code STOP_AUTO}
 * @param xpCurveBase  레벨 곡선 값 1~1000(V196 — 레벨 L 에 필요한 누적 경험치 = ceil(base × (L−1)^1.5)). null 이면 지금 값 그대로
 */
public record GradesSaveRequest(List<GradeRequest> grades, String manualPolicy, Integer xpCurveBase) {

    /** 곡선 칸 없이(그대로) */
    public GradesSaveRequest(List<GradeRequest> grades, String manualPolicy) {
        this(grades, manualPolicy, null);
    }
}
