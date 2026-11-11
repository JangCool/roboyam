package com.community.auth.dto.response;

import java.util.List;

/**
 * 화면에 내려가는 로그인한 회원 정보. ID 는 <b>문자열</b>이다(Snowflake 64비트라 JavaScript 숫자로 못 다룬다).
 *
 * @param grade          등급 설정의 등급 키 — 기본은 {@code SEEDLING} · {@code REGULAR} · {@code LOYAL} · {@code VETERAN}, 운영자가 더한 등급은 {@code GRADE_5} …
 * @param role           {@code USER} · {@code STAFF} · {@code ADMIN}
 * @param email          소셜 가입자가 이메일을 안 줬으면 null
 * @param authMethods    비밀번호가 있으면 {@code EMAIL}, 연결된 소셜 공급자마다 {@code KAKAO} · {@code NAVER} · {@code GOOGLE}
 * @param businessStatus 사업자 신청 상태 — 사업자 기능이 없는 동안 항상 null
 * @param level          레벨(V196 — 경험치로 오른다, Lv.1~ 끝 없음)
 * @param xp             누적 경험치
 * @param levelXp        이 레벨이 시작한 누적 경험치
 * @param nextLevelXp    다음 레벨이 되는 누적 경험치(막대 = (xp − levelXp) / (nextLevelXp − levelXp))
 */
public record UserView(String id, String nickname, String grade, String gradeLabel, String role, String email, boolean emailVerified,
                       List<String> authMethods, String businessStatus, int level, long xp, long levelXp, long nextLevelXp) {
}
