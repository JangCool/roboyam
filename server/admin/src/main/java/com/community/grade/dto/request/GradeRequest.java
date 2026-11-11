package com.community.grade.dto.request;

/**
 * 등급 저장 요청의 한 줄 — 목록 <b>순서가 곧 서열</b>이다. 이미 있는 등급은 {@code gradeKey} 를 보내고, 새 등급은 뺀다(서버가 발급한다).
 * 첫 줄은 기본 등급이라 {@code gradeKey} 가 그대로여야 하고 승급 조건은 보내도 무시한다. 검사는 칸 경로({@code grades[i].label} …)가 필요해 서비스가 한다.
 * 이름은 앞뒤 공백을 자른 뒤 저장한다(1~8자, 서로 달라야 한다). 아이콘이 없으면 null.
 *
 * @param minLevel 최소 레벨 조건 1~10000(V196 — 첫 등급은 무시하고 1). 칸이 없으면(null) 기존 등급은 지금 값, 새 등급은 1
 * @param sprite   도트 캐릭터 SPROUT · BOLT · ANTENNA · BATTERY · GEAR · HEART · LIGHTNING · STAR · CROWN · LEGEND · MYTH(V196). 칸이 없으면(null·빈 글자)
 *                 기존 등급은 지금 값, 새 등급은 서열 순 기본값
 */
public record GradeRequest(String gradeKey, String label, Integer minDays, Integer minActivity, String badgeColor, String badgeIcon, Integer minLevel,
                           String sprite) {

    /** 레벨·도트 칸 없이(예전 모양) */
    public GradeRequest(String gradeKey, String label, Integer minDays, Integer minActivity, String badgeColor, String badgeIcon) {
        this(gradeKey, label, minDays, minActivity, badgeColor, badgeIcon, null, null);
    }
}
