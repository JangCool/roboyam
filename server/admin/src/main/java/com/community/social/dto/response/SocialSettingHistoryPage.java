package com.community.social.dto.response;

import java.util.List;

/** 소셜 로그인 설정 변경 이력 한 쪽 — {@code page} 는 0 부터, 최신이 위(바꾼 시각 내림차순) */
public record SocialSettingHistoryPage(List<SocialSettingHistoryView> items, long total, int page, int size) {
}
