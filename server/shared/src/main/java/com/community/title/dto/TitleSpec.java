package com.community.title.dto;

import com.community.title.entity.TitleMethod;
import com.community.title.entity.TitleMetric;
import com.community.title.entity.TitleReviewer;
import java.util.List;

/**
 * 칭호의 고칠 수 있는 값 한 벌(관리자 만들기·고치기가 검사를 마친 값). 단계가 없으면 {@code tierMetric} 과 문턱 셋이 모두 null. {@code boardSlug} 는 연결 게시판이
 * 아직 없는 기본 칭호의 처음 값을 그대로 남길 때만(그 밖 null).
 */
public record TitleSpec(String name, String nameEn, String icon, String chip, TitleMethod method, TitleReviewer reviewer, Long boardId, List<TitleCondition> conditions,
                        TitleMetric tierMetric, Integer tierBronze, Integer tierSilver, Integer tierGold, boolean benefitTrialPriority, boolean benefitMagazine,
                        boolean benefitAvatarFrame, boolean dormantHide, boolean enabled, int sortOrder, String boardSlug) {
}
