package com.community.batch.dto.response;

/**
 * 배치가 받을 수 있는 실행 파라미터 하나(작업이 정의한다).
 *
 * @param name     키 — 요청의 {@code params} 이름이자 오류 {@code fields.params.<이름>}
 * @param label    서버가 요청 언어로 푼 이름
 * @param type     {@code DATE}(YYYY-MM-DD) · {@code LONG} · {@code STRING} — 값은 모두 글자로 보낸다
 * @param help     입력칸 아래 안내(서버 글). 없으면 null
 */
public record BatchParamDefView(String name, String label, String type, boolean required, String help) {
}
