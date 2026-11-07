package com.community.rewardevent.dto;

import java.util.List;

/** 당첨자 선정 설정(admin-web {@code RewardEventSelectionConfig}) — 후보 방법 {@code MANUAL}·{@code COMMENT_DRAW}(하나 이상) · 댓글 추첨 설정 */
public record RewardSelectionConfig(List<String> sources, RewardCommentDrawConfig commentDraw) {
}
