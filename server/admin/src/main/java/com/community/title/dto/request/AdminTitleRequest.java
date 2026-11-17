package com.community.title.dto.request;

import java.util.List;

/**
 * 칭호 만들기·고치기({@code POST /api/admin/titles} · {@code PUT /api/admin/titles}(본문의 {@code key} 로 찾는다) · {@code PUT /api/admin/titles/{key}} — member-title-contract 「화면」 관리자 서랍).
 * 칸 이름은 서버 {@code TitleSpec} 그대로(admin-web {@code AdminTitleSaveRequest}). 검사는 서비스가 칸 경로 키로 모은다({@code name}·{@code conditions[0].value}·{@code tierBronze}…).
 * 기본 칭호는 {@code method} 를 바꿀 수 없다(409 {@code TITLE_BUILTIN_LOCKED}).
 *
 * @param key         만들기: 칭호 키(선택 — 소문자·숫자·하이픈 2~32자, 비우면 서버가 발급) · 고치기: 찾을 칭호
 * @param name        이름(공백 뺀 6자까지, 등급 이름과 겹치면 400)
 * @param icon        pen·trophy·wreath·mag·mic·popular·cal·medal·bulb·heart
 * @param chip        PURPLE(수여·심사)·GREEN(자동)
 * @param method      AUTO·APPLY·AWARD
 * @param reviewer    OPERATOR·OPERATOR_AND_BOARD_MANAGER(연결 게시판 필수) — 없으면 OPERATOR
 * @param boardId     연결 게시판 ID(글자)
 * @param conditions  조건 목록(최대 10개)
 * @param tierMetric  단계가 세는 것 POPULAR_DAYS·ATTENDANCE_DAYS·POSTS·TRIAL_KEPT — 없으면 단계 없음(문턱 셋도 비운다)
 * @param retroactive 저장 뒤 기존 활동으로 소급 지급(자동 칭호만, 없으면 켬 — 그 칭호만 자동 판정 배치를 한 번 돌린다)
 */
public record AdminTitleRequest(String key, String name, String nameEn, String icon, String chip, String method, String reviewer, String boardId, List<Condition> conditions,
                                String tierMetric, Integer tierBronze, Integer tierSilver, Integer tierGold, Boolean benefitTrialPriority, Boolean benefitMagazine,
                                Boolean benefitAvatarFrame, Boolean dormantHide, Boolean enabled, Integer sortOrder, Boolean retroactive) {

    /**
     * 조건 하나 — type GRADE_AT_LEAST(gradeKey)·POSTS(value, boardId? — 없으면 boardSlug, 둘 다 없으면 모든 게시판)·POPULAR_DAYS(value)·ATTENDANCE_DAYS(value)·NO_REPORT_HIDDEN.
     * {@code boardSlug} 는 기본 칭호의 처음 값(게시판이 아직 없을 때) — 조회 응답의 값을 그대로 돌려 보내면 된다
     */
    public record Condition(String type, Integer value, String boardId, String boardSlug, String gradeKey) {
    }
}
