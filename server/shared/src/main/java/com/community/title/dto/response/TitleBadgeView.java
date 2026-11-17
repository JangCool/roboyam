package com.community.title.dto.response;

/**
 * 대표 칭호 하나(작성자 DTO {@code title} — member-title-contract 「구현 결정」 7). 이달의 작가의 {@code label} 은 가장 최근 받은 달로 「11월의 작가」(영어 {@code labelEn} 「November Writer」).
 *
 * @param key     칭호 키
 * @param label   표시 이름
 * @param labelEn 영어 표시 이름(운영자가 넣지 않았으면 null — 화면은 {@code label} 로 되돌린다)
 * @param icon    도트 그림 키(pen·trophy·wreath·mag·mic·popular·cal·medal·bulb·heart)
 * @param tier    단계 BRONZE·SILVER·GOLD — 단계 없는 칭호는 null
 * @param chip    칩 색 PURPLE·GREEN
 */
public record TitleBadgeView(String key, String label, String labelEn, String icon, String tier, String chip) {
}
