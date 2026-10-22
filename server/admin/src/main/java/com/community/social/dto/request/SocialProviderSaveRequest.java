package com.community.social.dto.request;

/**
 * 소셜 로그인 공급자 설정 등록·수정 요청({@code PUT /api/admin/social-providers/{provider}}). 검사 문구가 칸(clientId·clientSecret·enabled)별이고
 * 시크릿 필수 여부가 저장된 값에 달려 있어 서비스가 한꺼번에 검사한다. {@link #toString()} 은 시크릿을 감춘다.
 *
 * @param clientId     앞뒤 공백을 자른 뒤 1~200자, 공백·제어 문자 없음. 필수
 * @param clientSecret 앞뒤 공백을 자른 뒤 1~500자, 공백·제어 문자 없음. 생략하거나 비우면 저장된 값을 유지한다(저장된 것이 없거나 읽을 수 없으면 필수)
 * @param enabled      생략하면 true
 */
public record SocialProviderSaveRequest(String clientId, String clientSecret, Boolean enabled) {

    @Override
    public String toString() {
        return "SocialProviderSaveRequest[clientId=(감춤), clientSecret=" + (clientSecret == null ? "null" : "(감춤)") + ", enabled=" + enabled + "]";
    }
}
