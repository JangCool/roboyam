package com.community.title.dto.response;

import java.util.List;

/**
 * 가진 칭호 하나(공개 프로필 {@code titles[]}·내 칭호 {@code owned[]} — 계약 「구현 결정」 7). 같은 칭호 여러 번(이달의 작가)은 한 줄로 묶고 {@code count}·{@code periods} 로 보인다(「이달의 작가 ×4」).
 *
 * @param label   칭호 이름(묶은 줄이라 이달의 작가도 「이달의 작가」)
 * @param tier    단계 칭호의 지금 단계 — 그 밖 null
 * @param count   받은 횟수(살아 있는 줄 수)
 * @param periods 받은 기간 최신 순(이달의 작가 {@code YYYY-MM}, 올해의 작가 {@code YYYY}) — 기간 없는 칭호는 빈 목록
 */
public record OwnedTitleView(String key, String label, String labelEn, String icon, String tier, String chip, int count, List<String> periods) {
}
