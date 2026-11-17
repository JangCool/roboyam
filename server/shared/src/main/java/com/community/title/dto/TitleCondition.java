package com.community.title.dto;

import com.community.title.entity.TitleConditionType;

/**
 * 칭호 조건 하나({@code titles.conditions} JSON 배열의 한 칸 — 칸 이름이 그대로 JSON 키다).
 *
 * @param type      조건 종류
 * @param value     세는 조건의 문턱(1 이상) — 그 밖은 null
 * @param boardId   {@code POSTS} 의 게시판 ID(없으면 {@code boardSlug}, 둘 다 없으면 모든 게시판)
 * @param boardSlug {@code POSTS} 의 게시판 slug — 기본 칭호의 처음 값(설치 마법사가 게시판을 마이그레이션 뒤에 만든다). 운영자가 게시판을 고르면 {@code boardId} 만 남는다
 * @param gradeKey  {@code GRADE_AT_LEAST} 의 등급 키
 */
public record TitleCondition(TitleConditionType type, Integer value, Long boardId, String boardSlug, String gradeKey) {
}
