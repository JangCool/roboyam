package com.community.role.dto.response;

/**
 * 역할 유형 한 건 — admin-web 의 {@code RoleTypeView} 모양이다. 유형은 고정이라 추가·수정·삭제 API 가 없다.
 *
 * @param description 유형 설명(회원 역할 유형만, 요청 언어로 푼 문구). 운영자 유형은 null
 * @param axis        {@code MEMBER} · {@code OPERATOR}
 * @param area        운영자 역할 유형이 속한 영역 id — 회원 역할 유형은 null
 * @param derived     등급 설정에서 파생되는 유형(등급만 true)
 * @param creatable   운영자가 이 유형에 역할을 새로 만들 수 있나
 * @param roleCount   이 유형에 든 역할 수
 */
public record RoleTypeView(String roleTypeId, String description, String axis, String area, boolean derived, boolean creatable, long roleCount) {
}
