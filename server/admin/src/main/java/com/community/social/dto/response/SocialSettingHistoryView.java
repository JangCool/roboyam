package com.community.social.dto.response;

import java.time.Instant;
import java.util.List;

/**
 * 소셜 로그인 공급자 설정 변경 이력 한 줄.
 *
 * @param id              이력 ID(문자열 Snowflake)
 * @param provider        {@code KAKAO} · {@code NAVER} · {@code GOOGLE}
 * @param providerLabel   요청 언어의 공급자 이름
 * @param action          {@code CREATE} · {@code UPDATE} · {@code DELETE}
 * @param source          {@code INSTALL}(설치 마법사) · {@code ADMIN}(관리자 설정 화면)
 * @param operatorId      바꾼 운영자 ID(문자열). 모르면 null
 * @param operatorName    바꾼 운영자 이름 — 운영자가 지워졌거나 모르면 대체 문구(「삭제된 운영자」, 요청 언어)
 * @param operatorDeleted 운영자를 찾을 수 없어 대체 문구를 넣었는가
 * @param changedAt       바꾼 시각
 * @param changes         바뀐 칸들(칸 순서 clientId → clientSecret → enabled)
 */
public record SocialSettingHistoryView(String id, String provider, String providerLabel, String action, String source, String operatorId, String operatorName,
                                       boolean operatorDeleted, Instant changedAt, List<SocialSettingHistoryChangeView> changes) {
}
