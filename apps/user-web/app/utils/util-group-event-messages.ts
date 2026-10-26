import type { Translate } from '#layers/i18n/utils/util-i18n';
import type { DisplayTime } from '#layers/display-time/utils/util-time';
import type {
    GroupEventCounts,
    GroupEventMode,
    GroupEventResponse,
    GroupEventRow,
    GroupEventState,
    GroupManageBlockedReason,
} from '~/composables/group/api/useGroupApi.types';
import { ApiError } from '~/composables/common/useApiClient';
import UtilBoardMessages from '~/utils/util-board-messages';
import UtilGroupEvent, { type GroupEventField, type GroupEventLimits, type GroupEventProblem, type GroupEventTone } from '~/utils/util-group-event';

/**
 * 소모임 일정(4단계) 화면의 계산이 낀 문구 — 분기·자리표시자·조합만 둔다(상수 문구는 컴포넌트가 `t('groupEvent.키')` 로 부른다).
 * 문구 본문은 `i18n/locales/{ko,en}/groupEvent.ts`. 서버 `message` 가 있는 오류는 서버 문구를 그대로 쓴다(`UtilBoardMessages.of`).
 */

export interface GroupEventTag {
    label: string;
    tone: GroupEventTone;
}

export interface GroupEventCopy {
    title: string;
    text: string;
}

/** 만들기·수정 화면 — 만들기 · 수정 */
export type GroupEventFormKind = 'create' | 'edit';

/** 만들기·수정에서 폼 대신 보이는 갈래(`useGroupEventForm` 의 `view` 중 안내가 있는 것) */
export type GroupEventGateView = 'login' | 'not-member' | 'not-manager' | 'blocked';

const RESPONSE_KEY: Record<GroupEventResponse, string> = { GOING: 'groupEvent.responseGoing', NOT_GOING: 'groupEvent.responseNotGoing', MAYBE: 'groupEvent.responseMaybe' };
const RESPONDED_KEY: Record<GroupEventResponse, string> = { GOING: 'groupEvent.respondedGoing', NOT_GOING: 'groupEvent.respondedNotGoing', MAYBE: 'groupEvent.respondedMaybe' };

export default class UtilGroupEventMessages {
    static modeLabel(t: Translate, mode: GroupEventMode): string {
        return t(mode === 'ONLINE' ? 'groupEvent.modeOnline' : 'groupEvent.modeOffline');
    }

    static modeTag(t: Translate, mode: GroupEventMode): GroupEventTag {
        return { label: UtilGroupEventMessages.modeLabel(t, mode), tone: UtilGroupEvent.modeTone(mode) };
    }

    /** 상태의 작은 표시 — 진행 중 · 종료 · 취소됨. 시작 전은 표시하지 않는다(null) */
    static stateTag(t: Translate, state: GroupEventState): GroupEventTag | null {
        const tone = UtilGroupEvent.stateTone(state);
        if (!tone) return null;
        const key = state === 'ONGOING' ? 'groupEvent.stateOngoing' : state === 'ENDED' ? 'groupEvent.stateEnded' : 'groupEvent.stateCanceled';
        return { label: t(key), tone };
    }

    static responseLabel(t: Translate, response: GroupEventResponse | null): string {
        return t(response ? RESPONSE_KEY[response] : 'groupEvent.responseNone');
    }

    /** 목록 줄의 「내 응답 참석」 */
    static responseTag(t: Translate, response: GroupEventResponse | null): GroupEventTag {
        return { label: t('groupEvent.myResponse', { label: UtilGroupEventMessages.responseLabel(t, response) }), tone: UtilGroupEvent.responseTone(response) };
    }

    /** 참석 단추 — 참석 · 불참 · 미정 */
    static responseOptions(t: Translate): { value: GroupEventResponse; label: string }[] {
        return UtilGroupEvent.RESPONSES.map((value) => ({ value, label: UtilGroupEventMessages.responseLabel(t, value) }));
    }

    /** 응답을 바꾼 뒤의 토스트 — 「불참으로 표시했어요」 */
    static respondedToast(t: Translate, response: GroupEventResponse): string {
        return t(RESPONDED_KEY[response]);
    }

    /** 응답 단추가 꺼진 까닭 */
    static responseClosed(t: Translate, state: GroupEventState): string {
        if (state === 'ONGOING') return t('groupEvent.responseClosedOngoing');
        if (state === 'ENDED') return t('groupEvent.responseClosedEnded');
        if (state === 'CANCELED') return t('groupEvent.responseClosedCanceled');
        return t('groupEvent.responseClosed');
    }

    /** 목록 줄 둘째 줄 — 「20:00 · 온라인 · 참석 14명」, 오프라인은 방식 자리에 장소 */
    static rowSummary(t: Translate, row: Pick<GroupEventRow, 'startsAt' | 'mode' | 'place' | 'goingCount'>, time: DisplayTime): string {
        const where = row.mode === 'ONLINE' ? UtilGroupEventMessages.modeLabel(t, 'ONLINE') : row.place?.trim() || UtilGroupEventMessages.modeLabel(t, 'OFFLINE');
        return [time.clock(row.startsAt), where, t('groupEvent.goingCount', { n: row.goingCount })].filter((part) => part !== '').join(' · ');
    }

    static countsLine(t: Translate, counts: GroupEventCounts): string {
        return t('groupEvent.counts', { going: counts.going, notGoing: counts.notGoing, maybe: counts.maybe });
    }

    /** 스크린리더가 읽는 목록 요약 */
    static summary(t: Translate, status: string, count: number, hasNext: boolean): string {
        if (status === 'loading') return t('groupEvent.summaryLoading');
        if (status === 'error') return t('groupEvent.summaryError');
        if (status === 'locked') return t('groupEvent.summaryLocked');
        if (status !== 'ready') return '';
        return t(hasNext ? 'groupEvent.summaryMore' : 'groupEvent.summaryEnd', { n: count });
    }

    /** 목록 불러오기 실패 설명 — 서버 문구 + 「참석 응답도 저장되지 않았을 수 있어요」 */
    static loadErrorText(t: Translate, message: string): string {
        return [message, t('groupEvent.loadErrorTail')].filter(Boolean).join(' ');
    }

    /** 내 소모임 줄의 다음 일정 — 「일정 내일 20:00」 · 「일정 10.15 20:00」(다른 해는 연 포함). 시각이 잘못됐으면 빈 문자열 */
    static nextLabel(t: Translate, startsAt: string | null | undefined, time: DisplayTime, now: number = Date.now()): string {
        const offset = UtilGroupEvent.dayOffset(startsAt, time, now);
        const clock = time.clock(startsAt);
        if (offset === null || !clock) return '';
        let day: string;
        if (offset <= 0) day = t('groupEvent.today');
        else if (offset === 1) day = t('groupEvent.tomorrow');
        else {
            const sameYear = time.parts(startsAt)?.y === time.parts(new Date(now).toISOString())?.y;
            day = sameYear ? time.shortDate(startsAt) : time.date(startsAt);
        }
        return t('groupEvent.nextLabel', { day, time: clock });
    }

    /* ── 폼 ── */

    /** 칸 문제 → 문구(숫자는 지금 한도·글자 수) */
    static problem(t: Translate, field: GroupEventField, problem: GroupEventProblem, form: { title: string; place: string; description: string }, limits: GroupEventLimits): string {
        const n = (text: string) => UtilGroupEvent.length(text.trim());
        switch (field) {
            case 'title':
                return problem === 'REQUIRED' ? t('groupEvent.titleRequired') : t('groupEvent.titleTooLong', { max: UtilGroupEvent.TITLE_MAX, n: n(form.title) });
            case 'startsAt':
                if (problem === 'PAST') return t('groupEvent.startPast');
                if (problem === 'TOO_FAR') return t('groupEvent.startTooFar', { days: limits.maxDaysAhead });
                return t('groupEvent.startRequired');
            case 'endsAt':
                if (problem === 'TOO_LONG') return t('groupEvent.endTooLong', { days: limits.maxLengthDays });
                if (problem === 'PAST_ONGOING') return t('groupEvent.endPastOngoing');
                return t('groupEvent.endBeforeStart');
            case 'place':
                return problem === 'REQUIRED' ? t('groupEvent.placeRequired') : t('groupEvent.placeTooLong', { max: UtilGroupEvent.PLACE_MAX, n: n(form.place) });
            case 'description':
                return t('groupEvent.descriptionTooLong', { max: UtilGroupEvent.DESCRIPTION_MAX, n: n(form.description) });
            case 'offlineAck':
                return t('groupEvent.ackRequired');
            default:
                return '';
        }
    }

    /** 칸에 붙일 수 없는 제출 실패 — 제목은 코드로 고르고 설명은 서버 문구 그대로(+ 입력이 남아 있다는 말) */
    static failure(t: Translate, kind: GroupEventFormKind, e: unknown): GroupEventCopy {
        const reason = UtilBoardMessages.of(t, e);
        const code = e instanceof ApiError ? e.code : '';
        let title: string;
        if (code === 'GROUP_EVENT_LIMIT_REACHED') title = t('groupEvent.failLimitTitle');
        else if (code === 'GROUP_EVENT_ENDED') title = t('groupEvent.failEndedTitle');
        else if (code === 'GROUP_EVENT_CANCELED') title = t('groupEvent.failCanceledTitle');
        else if (code === 'GROUP_LEADER_ONLY' || code === 'GROUP_MANAGER_PERMISSION_DENIED' || code === 'GROUP_NOT_MEMBER') title = t('groupEvent.failPermissionTitle');
        else if (e instanceof ApiError && e.status === 429) title = t('groupEvent.failRateTitle');
        else title = t(kind === 'create' ? 'groupEvent.failCreateTitle' : 'groupEvent.failEditTitle');
        return { title, text: t('groupEvent.failKeep', { reason }) };
    }

    /** 폼 대신 보이는 안내 — 로그인 · 멤버 아님 · 모임장/매니저 아님(매니저면 「맡기지 않은 일」) · 쓰기가 멈춤 */
    static gate(t: Translate, view: GroupEventGateView, kind: GroupEventFormKind, role: string | null, blocked: GroupManageBlockedReason | null): GroupEventCopy {
        switch (view) {
            case 'login':
                return { title: t('groupEvent.loginTitle'), text: t(kind === 'create' ? 'groupEvent.gateLoginCreate' : 'groupEvent.gateLoginEdit') };
            case 'not-member':
                return { title: t('groupEvent.gateNotMemberTitle'), text: t('groupEvent.gateNotMemberText') };
            case 'not-manager':
                if (role === 'MANAGER') return { title: t('groupEvent.gateNotDelegatedTitle'), text: t('groupEvent.gateNotDelegatedText') };
                return { title: t(kind === 'create' ? 'groupEvent.gateNotManagerCreate' : 'groupEvent.gateNotManagerEdit'), text: t('groupEvent.gateNotManagerText') };
            default:
                if (blocked === 'EMAIL_NOT_VERIFIED') return { title: t('groupEvent.gateUnverifiedTitle'), text: t('groupEvent.gateUnverifiedText') };
                return { title: t('groupEvent.gateRestrictedTitle'), text: t(blocked === 'BUSINESS_ACCOUNT_RESTRICTED' ? 'groupEvent.gateBusinessText' : 'groupEvent.gateRestrictedText') };
        }
    }
}
