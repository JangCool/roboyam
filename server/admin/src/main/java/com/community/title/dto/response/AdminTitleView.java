package com.community.title.dto.response;

import java.time.Instant;
import java.util.List;

/**
 * 관리자 칭호 한 줄({@code GET /api/admin/titles} — member-title-contract 「구현 결정」 2·7, admin-web {@code AdminTitle}). 고칠 수 있는 칸은 {@code TitleSpec} 이름 그대로, 꺼진 칭호도 나온다.
 *
 * @param boardId    연결 게시판 ID(기본 칭호의 처음 값 slug 가 가리키는 게시판 포함) — 없으면 null. {@code boardName} 그 게시판 이름
 * @param conditions 조건 목록 — {@code boardId} 는 조건의 게시판(처음 값 slug 를 찾은 ID), {@code boardSlug} 는 게시판을 아직 찾지 못한 처음 값 slug(찾았으면 null),
 *                   {@code label} 요청 언어 한 줄
 * @param tierMetric 단계가 세는 것 — 단계 없는 칭호는 null(문턱 셋도 null)
 * @param holders    가진 회원 수(탈퇴 빼고)
 * @param howTo      받는 방법 한 줄(도감과 같은 글)
 */
public record AdminTitleView(String key, String name, String nameEn, String icon, String chip, String method, String reviewer, String boardId, String boardName,
                             List<ConditionView> conditions, String tierMetric, Integer tierBronze, Integer tierSilver, Integer tierGold, boolean benefitTrialPriority,
                             boolean benefitMagazine, boolean benefitAvatarFrame, boolean dormantHide, boolean enabled, int sortOrder, boolean builtin, long holders, String howTo,
                             Instant updatedAt) {

    public record ConditionView(String type, Integer value, String boardId, String boardSlug, String gradeKey, String label) {
    }
}
