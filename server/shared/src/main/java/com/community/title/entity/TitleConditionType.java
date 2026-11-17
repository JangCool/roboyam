package com.community.title.entity;

import java.util.Optional;

/**
 * 칭호 조건 종류({@code titles.conditions} JSON 의 {@code type}) — 모두 채우면 받는다.
 *
 * <ul>
 *   <li>{@link #GRADE_AT_LEAST} — 등급이 {@code gradeKey} 이상(서열로 비교)</li>
 *   <li>{@link #POSTS} — 게시판({@code boardId}, 없으면 {@code boardSlug}, 둘 다 없으면 모든 게시판)에 지우지 않은 글 {@code value} 편 이상</li>
 *   <li>{@link #POPULAR_DAYS} — 하루 인기글 상위 진입 {@code value} 일 이상({@code point_popular_rewards} 의 날짜 수)</li>
 *   <li>{@link #ATTENDANCE_DAYS} — 출석한 날 {@code value} 일 이상</li>
 *   <li>{@link #NO_REPORT_HIDDEN} — 신고(신고 숨김·불법·권리 침해)로 숨겨진 글이 지금 없다</li>
 * </ul>
 */
public enum TitleConditionType {
    GRADE_AT_LEAST,
    POSTS,
    POPULAR_DAYS,
    ATTENDANCE_DAYS,
    NO_REPORT_HIDDEN;

    /** 수를 세는 조건인가(값 1 이상이 필요하다) */
    public boolean counted() {
        return this == POSTS || this == POPULAR_DAYS || this == ATTENDANCE_DAYS;
    }

    public static Optional<TitleConditionType> parse(String value) {
        if (value == null) {
            return Optional.empty();
        }
        for (TitleConditionType t : values()) {
            if (t.name().equals(value.strip())) {
                return Optional.of(t);
            }
        }
        return Optional.empty();
    }
}
