package com.community.site.dto.response;

/**
 * {@code /api/site/info} 의 {@code grades} 한 줄(grade-level-contract 5절) — 화면이 작성자 {@code grade} 키로 도트 캐릭터·등급 이름을 찾는다.
 *
 * @param key    등급 키(작성자 DTO 의 {@code grade} 와 같은 값)
 * @param label  등급 이름(운영자가 정한 한 언어)
 * @param level  서열(1..N — 낮은 등급부터)
 * @param sprite 도트 캐릭터 SPROUT · BOLT · ANTENNA · BATTERY · GEAR · HEART · LIGHTNING · STAR · CROWN · LEGEND · MYTH
 */
public record SiteGradeView(String key, String label, int level, String sprite) {
}
