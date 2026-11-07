package com.community.rewardevent.dto;

import java.time.LocalDate;

/**
 * 일괄 설정(admin-web {@code RewardEventBulkConfig}) — {@code ALL}(정상 회원 전체) / {@code CONDITION}(등급 서열 이상 · 가입일 범위(표시 시간대 날짜) · 본인인증만 · 최근 N일 로그인).
 */
public record RewardBulkConfig(String scope, Integer minGradeLevel, LocalDate joinedFrom, LocalDate joinedTo, Boolean identityVerifiedOnly, Integer activeWithinDays) {
}
