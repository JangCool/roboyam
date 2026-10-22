package com.community.social.dto.request;

/**
 * 소셜 로그인 설정 변경 이력 조회 조건(쿼리 문자열). 값 해석은 서비스가 하고 틀린 값은 칸 이름과 함께 400 으로 알린다.
 *
 * @param provider 공급자({@code KAKAO}·{@code kakao} …). 생략하면 모든 공급자
 * @param page     0 부터(기본 0)
 * @param size     1~100(기본 20)
 */
public record SocialSettingHistoryRequest(String provider, String page, String size) {
}
