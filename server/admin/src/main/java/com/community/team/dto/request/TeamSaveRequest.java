package com.community.team.dto.request;

/**
 * 팀 만들기·고치기. 이름은 앞뒤 공백을 자른 뒤 1~50자(대소문자 무시 유일), 설명은 500자 이하(비어도 된다). 사용 여부를 안 보내면 만들 때는 사용, 고칠 때는 그대로다.
 * 정렬 순서는 보내지 않는다 — 서버가 맨 뒤에 붙이고 옮기는 것은 {@code move} 다. 검사는 서비스가 한다(칸 문구가 요청 언어로 나간다).
 */
public record TeamSaveRequest(String teamName, String description, Boolean enabled) {
}
