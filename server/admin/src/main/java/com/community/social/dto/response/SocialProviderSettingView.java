package com.community.social.dto.response;

import java.time.Instant;

/**
 * 소셜 로그인 공급자 한 곳의 설정 — 목록의 원소이자 {@code PUT} 의 응답. <b>시크릿은 없다</b>(입력됨 여부 {@code secretSet} 만).
 *
 * @param provider    공급자 코드 {@code KAKAO} · {@code NAVER} · {@code GOOGLE}
 * @param id          공개 API·콜백 경로에 쓰는 소문자 이름({@code kakao}) — {@code GET /api/auth/providers} 의 {@code id} 와 같다
 * @param label       요청 언어의 이름(카카오/Kakao)
 * @param callbackUrl 공급자 콘솔에 등록할 콜백 주소 {@code {공개 주소}/api/auth/social/{id}/callback}. 사이트 공개 주소가 없으면 null
 * @param source      {@code SETTINGS}(등록됨) · {@code NONE}(미등록). roboyam 에는 환경 변수 키가 없어 {@code ENV} 는 나오지 않는다
 * @param enabled     저장된 켜짐 값(등록돼 있지 않으면 false). {@code SETTINGS} + false 는 「등록했지만 꺼 둠」
 * @param active      지금 공개 사이트 로그인 화면에 버튼이 나오는가 — 켜짐 + 키 있음 + 서버 암호화 키 있음({@code GET /api/auth/providers} 와 같은 기준)
 * @param clientId    공개값이라 그대로 보인다. 미등록이면 null
 * @param secretSet   시크릿이 저장돼 있고 지금 서버 키로 풀 수 있는가(키를 바꿔 풀 수 없으면 false — 다시 입력해야 한다). 값은 주지 않는다
 * @param configured  로그인에 필요한 값(클라이언트 ID + 풀 수 있는 시크릿)이 모두 있는가
 * @param updatedAt   마지막 저장 시각(설치 마법사가 넣은 값이면 설치 시각). 미등록이면 null
 * @param updatedBy   마지막으로 저장한 운영자 이름. 미등록이거나 운영자가 지워졌으면 null
 */
public record SocialProviderSettingView(String provider, String id, String label, String callbackUrl, String source, boolean enabled, boolean active,
                                        String clientId, boolean secretSet, boolean configured, Instant updatedAt, String updatedBy) {
}
