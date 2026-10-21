package com.community.grade.dto.request;

import java.util.List;

/**
 * 등급 목록과 옵션의 <b>일괄 저장</b>(미리보기도 같은 모양) — 서버가 지금 저장된 목록과 비교해 추가·삭제·순서·수정을 처리한다. 요청에 빠진 기존 등급은 지우는 것이다.
 *
 * @param grades       서열 순서의 등급(2~10개)
 * @param manualPolicy {@code CONTINUE_AUTO} · {@code STOP_AUTO}
 */
public record GradesSaveRequest(List<GradeRequest> grades, String manualPolicy) {
}
