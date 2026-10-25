import type { Translate } from '#layers/i18n/utils/util-i18n';
import { ApiError } from '~/composables/common/useApiClient';
import type { GroupDetail, GroupJoinType, GroupLeaderLoad, GroupRecruitState } from '~/composables/group/api/useGroupAdminApi.types';
import type { GroupCategoryNameProblem } from '~/utils/util-group-category';
import type { GroupRuleProblem } from '~/utils/util-group-rule';
import UtilAdminAuthMessages from '~/utils/util-admin-auth-messages';

/** 단위 키(`group.unit.*`) */
export type GroupRuleUnit = 'day' | 'hour' | 'times' | 'count' | 'chars' | 'people' | 'places' | 'items' | 'events';

/**
 * 소모임 관리 화면의 계산이 낀 문구 — 상수 문구는 컴포넌트가 `t('group.…')` 로 부른다. 키 규칙은 `i18n.md`.
 * community 의 `util-group-admin-messages`·`util-admin-group-*-messages` 의 분기 문구를 옮겼다.
 */
export default class UtilGroupMessages {
    /**
     * 잡은 오류를 보일 한 줄 — `ApiError` 면 서버 문구 그대로, 아니면(네트워크 등) 지금 언어의 대체 문구.
     * @param {Translate} t - 번역 함수.
     * @param {unknown} e - 잡은 오류.
     * @returns {string} 한 줄.
     */
    static of(t: Translate, e: unknown): string {
        return e instanceof ApiError ? e.message : UtilAdminAuthMessages.fallback(t, 0, 'NETWORK');
    }

    /**
     * 「무엇을 못 했어요 — 서버 문구」 한 줄(토스트).
     * @param {Translate} t - 번역 함수.
     * @param {string} key - 앞 말의 키.
     * @param {unknown} e - 잡은 오류.
     * @returns {string} 한 줄.
     */
    static failed(t: Translate, key: string, e: unknown): string {
        return `${t(key)} — ${UtilGroupMessages.of(t, e)}`;
    }

    // ── 목록·서랍 ─

    /** 승인 대기 줄 */
    static pendingLine(t: Translate, n: number): string {
        return n > 0 ? t('group.pendingLine', { n }) : t('group.pendingNone');
    }

    /** 「자유 가입 · 모집 중」 */
    static joinLine(t: Translate, joinType: GroupJoinType, recruitState: GroupRecruitState): string {
        return t('group.joinLine', { join: t(`group.join.${joinType}`), recruit: t(`group.recruit.${recruitState}`) });
    }

    /** 채팅 링크 줄 — 있는 것만 이름으로 */
    static chatLine(t: Translate, chat: GroupDetail['chat']): string {
        const names = [chat.kakao ? t('group.chatKakao') : '', chat.discord ? t('group.chatDiscord') : ''].filter(Boolean);
        return names.length ? t('group.chatSome', { names: names.join('·') }) : t('group.chatNone');
    }

    /** 개설 줄 — 「날짜 · 누구 승인 · 신청자 누구」 */
    static openedLine(t: Translate, date: string, reviewer: string, creator: string): string {
        return [date, reviewer ? t('group.openedBy', { name: reviewer }) : '', creator ? t('group.openedRequester', { name: creator }) : ''].filter(Boolean).join(' · ');
    }

    /** 모임장이 가진 소모임 줄 */
    static leaderLoadLine(t: Translate, load: GroupLeaderLoad): string {
        const parts = [t('group.leaderLoadActive', { n: load.active })];
        if (load.inactive > 0) parts.push(t('group.leaderLoadInactive', { n: load.inactive }));
        return t('group.leaderLoadLine', { total: load.total, parts: parts.join(' · '), max: load.max });
    }

    /** 공개 범위의 마지막 변경 줄 — 변경이 없으면 「개설 때 정한 값이에요」 */
    static visibilityChangedLine(t: Translate, date: string, name: string | null): string {
        if (!date) return t('group.visibilityInitial');
        return name ? t('group.visibilityChanged', { date, name }) : t('group.visibilityChangedNoName', { date });
    }

    /** 비활성 띠 */
    static band(t: Translate, date: string, caseNo: number | null): string {
        return caseNo !== null ? t('group.bandCase', { date, no: caseNo }) : t('group.band', { date });
    }

    /** 다시 열기 확인 본문 — 신고로 비활성이 됐으면 그 기록이 남는다는 말을 덧붙인다 */
    static reactivateBody(t: Translate, leader: string, caseNo: number | null): string {
        const base = t('group.reactivateConfirmBody', { leader });
        return caseNo !== null ? `${base} ${t('group.reactivateConfirmCase', { no: caseNo })}` : base;
    }

    // ── 분류 ─

    /** 분류 줄의 보조 글 */
    static categoryMeta(t: Translate, boards: number, groups: number, locale: string): string {
        return boards === 0 ? t('group.categoryMetaEmpty') : t('group.categoryMeta', { boards, groups: groups.toLocaleString(locale) });
    }

    /** 옮겨 오는 게시판 안내 */
    static moveNote(t: Translate, board: string, from: string, n: number): string {
        return n > 0 ? t('group.moveNote', { board, from, n }) : t('group.moveNoteNoGroups', { board, from });
    }

    /** 빼는 게시판 안내 */
    static removeNote(t: Translate, board: string, n: number): string {
        return n > 0 ? t('group.removeNote', { board, n }) : t('group.removeNoteNoGroups', { board });
    }

    /** 분류 삭제 확인 본문 */
    static deleteBody(t: Translate, boards: number): string {
        return boards > 0 ? t('group.categoryDeleteBody', { n: boards }) : t('group.categoryDeleteBodyEmpty');
    }

    /** 이름 문제 */
    static nameProblem(t: Translate, problem: GroupCategoryNameProblem): string {
        return t(`group.categoryNameProblem.${problem}`);
    }

    // ── 설정 구획 공통 ─

    /** 숫자 칸의 범위 글 — 「0~90일 · 기본 7」 */
    static range(t: Translate, min: number, max: number, unit: GroupRuleUnit, base: number): string {
        return t('group.ruleRange', { min, max, unit: t(`group.unit.${unit}`), base });
    }

    /** 칸 문제 → 문구. 순서 문제는 구획이 준 문구 키 */
    static problem(t: Translate, problem: GroupRuleProblem, unit: GroupRuleUnit, orderKey: string): string {
        if (problem.kind === 'ORDER') return t(orderKey);
        return t('group.ruleRangeProblem', { min: problem.min, max: problem.max, unit: t(`group.unit.${unit}`) });
    }

    /** 구획 머리의 상태 알약 — 오류 > 저장 전 > 기본값으로 도는 중 */
    static pill(t: Translate, fromDefaults: boolean, changes: number, errors: number): string {
        if (errors > 0) return t('group.rulePill.invalid');
        if (changes > 0) return t('group.rulePill.dirty');
        return fromDefaults ? t('group.rulePill.fromDefaults') : '';
    }

    /** 저장 줄 왼쪽 글 */
    static note(t: Translate, changes: number, saving: boolean, justSaved: boolean): string {
        if (saving) return t('common.saving');
        if (changes > 0) return t('group.ruleNoteChanged', { n: changes });
        return justSaved ? t('group.ruleNoteSaved') : t('group.ruleNoteClean');
    }

    /** 마지막 저장 줄 */
    static updatedLine(t: Translate, who: string | null, when: string): string {
        return who ? t('group.ruleUpdated', { who, when }) : t('group.ruleUpdatedNoName', { when });
    }

    // ── 개설 규칙 ─

    /** 승인제 → 자유 개설 확인 본문 */
    static creationConfirm(t: Translate, gradeLabel: string | null, pending: number): string {
        const who = gradeLabel === null ? t('group.creation.confirmBodyAll') : t('group.creation.confirmBodyGrade', { grade: gradeLabel });
        const wait = pending > 0 ? t('group.creation.confirmPending', { n: pending }) : t('group.creation.confirmPendingNone');
        return `${who} ${wait} ${t('group.creation.confirmTail')}`;
    }

    // ── 매니저 ─

    /** 사용 현황 줄 */
    static managerUsage(t: Translate, managers: number, groups: number): string {
        return managers > 0 ? t('group.manager.usage', { managers, groups }) : t('group.manager.usageNone');
    }

    /** 끄기 확인 본문 */
    static managerConfirmOff(t: Translate, managers: number, groups: number): string {
        return managers > 0 ? t('group.manager.confirmOffBody', { managers, groups }) : t('group.manager.confirmOffBodyNone');
    }

    // ── 공개 범위 ─

    /** 사용 현황 줄 */
    static visibilityUsage(t: Translate, publicGroups: number, privateGroups: number, pending: number): string {
        return [
            publicGroups > 0 ? t('group.visibilityRule.usagePublic', { n: publicGroups }) : t('group.visibilityRule.usagePublicNone'),
            privateGroups > 0 ? t('group.visibilityRule.usagePrivate', { n: privateGroups }) : t('group.visibilityRule.usagePrivateNone'),
            pending > 0 ? t('group.visibilityRule.usagePending', { n: pending }) : t('group.visibilityRule.usagePendingNone'),
        ].join(' · ');
    }

    /** 매니저 안내 알림 줄 */
    static visibilityNotice(t: Translate, sentAt: string | null, targets: number): string {
        if (sentAt) return t('group.visibilityRule.noticeSent', { at: sentAt });
        return targets > 0 ? t('group.visibilityRule.noticeWill', { n: targets }) : t('group.visibilityRule.noticeNone');
    }

    /** 켜졌을 때의 미리보기 — 기능 설명 + 숫자 규칙 */
    static visibilityRules(t: Translate, v: { cooldownDays: number; retryHours: number; rejectLimit: number; dailyLimit: number; textMax: number }): string {
        const rules = t('group.visibilityRule.previewRules', { reject: v.rejectLimit, daily: v.dailyLimit, text: v.textMax });
        const retry = v.retryHours > 0 ? t('group.visibilityRule.previewRetry', { n: v.retryHours }) : t('group.visibilityRule.previewRetryNow');
        const cooldown = v.cooldownDays > 0 ? t('group.visibilityRule.previewCooldown', { n: v.cooldownDays }) : t('group.visibilityRule.previewCooldownNow');
        return `${rules} ${retry} ${cooldown}`;
    }

    /** 켜기 확인 본문 */
    static visibilityConfirmOn(t: Translate, targets: number, alreadySent: boolean): string {
        const notice = alreadySent ? t('group.visibilityRule.confirmOnNoticeSent') : targets > 0 ? t('group.visibilityRule.confirmOnNoticeWill', { n: targets }) : t('group.visibilityRule.confirmOnNoticeNone');
        return `${t('group.visibilityRule.confirmOnBody')} ${notice}`;
    }

    /** 끄기 확인 본문 */
    static visibilityConfirmOff(t: Translate, publicGroups: number, pending: number): string {
        const parts = [t('group.visibilityRule.confirmOffBody')];
        if (publicGroups > 0) parts.push(t('group.visibilityRule.confirmOffPublic', { n: publicGroups }));
        if (pending > 0) parts.push(t('group.visibilityRule.confirmOffPending', { n: pending }));
        return parts.join(' ');
    }

    // ── 운영 한도 ─

    /** 가입·정원 미리보기 */
    static policyJoin(t: Translate, reapplyDays: number, min: number, max: number): string {
        const reapply = reapplyDays > 0 ? t('group.policy.previewReapply', { n: reapplyDays }) : t('group.policy.previewReapplyNow');
        return `${reapply} ${t('group.policy.previewCapacity', { min, max })}`;
    }
}
