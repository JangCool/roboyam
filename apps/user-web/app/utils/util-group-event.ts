import type { DisplayTime } from '#layers/display-time/utils/util-time';
import UtilTimeWords from '#layers/display-time/utils/util-time-words';
import type {
    GroupEventCounts,
    GroupEventDetail,
    GroupEventMode,
    GroupEventRequest,
    GroupEventResponse,
    GroupEventState,
    GroupLimits,
} from '~/composables/group/api/useGroupApi.types';
import { ApiError } from '~/composables/common/useApiClient';
import UtilGroup from '~/utils/util-group';

/**
 * 소모임 일정(4단계)의 순수 계산 — 화면 주소 · 날짜 배지 · 일시 한 줄 · 상태/응답 판정 · 집계 이동 · 오류 갈래 · 만들기/수정 폼 검사·요청 변환. 상태도 요청도 없다.
 * 문구(빈 상태·안내·토스트·칸 오류)는 `util-group-event-messages.ts` 와 `i18n/locales/{ko,en}/groupEvent.ts`(community `UtilGroupEvent`·`UtilGroupEventForm` 의 규칙을 옮겼다).
 *
 * **시각은 표시 시간대로 읽는다** — 서버는 UTC ISO-8601 을 주고, 화면은 `useDisplayTime()` 의 `time`(표시 값)으로 그린다. 입력은 공용 `AppDateTimePicker`(값 = 순간 ISO)다.
 * 폼 검사는 미리 알려 주는 용도이고 판정은 서버다 — 서버 `GroupEventRules`(제목 60 · 설명 1000 · 장소 100 · 취소 사유 100, 글자 수는 코드 포인트)와
 * 운영 한도(`GroupDetail.limits.eventMaxDaysAhead`·`eventMaxLengthDays`, 칸이 없으면 기본 366·7일)를 같은 규칙으로 쓴다.
 */

/** 일정을 열지 못한 갈래 — 멤버 아님(잠금) · 일정 없음 · 소모임 없음 · 비활성 소모임 · 그 밖(다시 시도) */
export type GroupEventFailure = 'locked' | 'event-notfound' | 'group-notfound' | 'group-inactive' | 'error';

/** 작은 표시의 색 — `GroupTag` 의 톤과 같은 이름 */
export type GroupEventTone = 'green' | 'amber' | 'gray' | 'blue';

/** 목록 줄의 날짜 배지 — 월(이름) · 일 · 요일(표시 언어) */
export interface GroupEventDateBadge {
    month: string;
    day: string;
    weekday: string;
}

/** 일정 한도 — 시작은 지금부터 며칠 안, 길이는 시작부터 며칠 안 */
export interface GroupEventLimits {
    maxDaysAhead: number;
    maxLengthDays: number;
}

/** 만들기·수정 폼 — 시각 칸은 순간 ISO(`AppDateTimePicker` 의 값), 비면 '' */
export interface GroupEventFormState {
    title: string;
    startsAt: string;
    endsAt: string;
    mode: GroupEventMode;
    place: string;
    description: string;
    offlineAck: boolean;
}

export type GroupEventField = 'title' | 'startsAt' | 'endsAt' | 'mode' | 'place' | 'description' | 'offlineAck';

/** 칸 문제의 종류 — 문구는 `UtilGroupEventMessages.problem` */
export type GroupEventProblem = 'REQUIRED' | 'LENGTH' | 'PAST' | 'TOO_FAR' | 'BEFORE_START' | 'TOO_LONG' | 'PAST_ONGOING' | 'ACK';

export type GroupEventProblems = Partial<Record<GroupEventField, GroupEventProblem>>;

/** 폼 검사의 재료 */
export interface GroupEventCheckContext {
    /** 지금(ms) */
    now: number;
    limits: GroupEventLimits;
    /** 이미 시작한 일정(수정)의 시작 시각 — 있으면 시작은 검사하지 않고 그대로 보낸다 */
    lockedStartsAt: string | null;
    /** 개인 책임 확인 체크가 필요한가(오프라인으로 새로 만들 때 · 온라인 → 오프라인 수정) */
    ackRequired: boolean;
}

const DAY_MS = 86_400_000;
const RESPONSES: readonly GroupEventResponse[] = ['GOING', 'NOT_GOING', 'MAYBE'];
const COUNT_KEY: Readonly<Record<GroupEventResponse, keyof GroupEventCounts>> = { GOING: 'going', NOT_GOING: 'notGoing', MAYBE: 'maybe' };

const FAILURE_BY_CODE: Readonly<Record<string, GroupEventFailure>> = {
    GROUP_NOT_MEMBER: 'locked',
    GROUP_EVENT_NOT_FOUND: 'event-notfound',
    GROUP_NOT_FOUND: 'group-notfound',
    GROUP_INACTIVE: 'group-inactive',
};

/** 바꾸려다(응답·취소·저장) 받은 오류 중 "화면이 보던 상태와 서버 상태가 달라진" 것 — 다시 받아 맞춘다 */
const CHANGED_CODES: ReadonlySet<string> = new Set([
    'GROUP_EVENT_CLOSED',
    'GROUP_EVENT_CANCELED',
    'GROUP_EVENT_ENDED',
    'GROUP_LEADER_ONLY',
    'GROUP_MANAGER_PERMISSION_DENIED',
    'GROUP_NOT_MEMBER',
    'GROUP_EVENT_NOT_FOUND',
    'GROUP_NOT_FOUND',
    'GROUP_INACTIVE',
]);

/** 일정 목록이 받은 오류 중 "이 소모임의 일정을 더는 못 본다" — 소모임 상세를 다시 받는다 */
const GONE_CODES: ReadonlySet<string> = new Set(['GROUP_NOT_FOUND', 'GROUP_INACTIVE', 'GROUP_NOT_MEMBER']);

/** 월 이름 포매터 — 같은 입력에 같은 결과를 주는 메모일 뿐 화면 상태가 아니다 */
const MONTH_FORMATS = new Map<string, Intl.DateTimeFormat>();

function monthName(language: 'ko' | 'en', month: number): string {
    const locale = language === 'en' ? 'en-US' : 'ko-KR';
    let format = MONTH_FORMATS.get(locale);
    if (!format) {
        format = new Intl.DateTimeFormat(locale, { month: 'short', timeZone: 'UTC' });
        MONTH_FORMATS.set(locale, format);
    }
    return format.format(Date.UTC(2026, month - 1, 1));
}

function ms(iso: string | null | undefined): number | null {
    if (!iso) return null;
    const value = Date.parse(iso);
    return Number.isNaN(value) ? null : value;
}

function wholeOr(raw: unknown, fallback: number): number {
    return typeof raw === 'number' && Number.isInteger(raw) && raw >= 1 ? raw : fallback;
}

/** 표시 시간대 달력으로 며칠째인지 — 오늘·내일을 가른다 */
function dayNumber(parts: { y: number; mo: number; d: number }): number {
    return Math.floor(Date.UTC(parts.y, parts.mo - 1, parts.d) / DAY_MS);
}

const eventsBase = (groupId: string): string => `${UtilGroup.detailPath(groupId)}/events`;

export default class UtilGroupEvent {
    /** 일정 목록 한 쪽(서버 기본 20, 최대 50) */
    static readonly PAGE_SIZE = 20;
    /** 참석자 한 쪽 */
    static readonly ATTENDEE_PAGE_SIZE = 20;
    /** 서버 `GroupEventRules` 의 글자 수 한도 */
    static readonly TITLE_MAX = 60;
    static readonly PLACE_MAX = 100;
    static readonly DESCRIPTION_MAX = 1000;
    static readonly CANCEL_REASON_MAX = 100;
    /** 운영 한도의 기본값 — 상세에 `limits` 칸이 없을 때(옛 서버)만 쓴다 */
    static readonly DEFAULT_MAX_DAYS_AHEAD = 366;
    static readonly DEFAULT_MAX_LENGTH_DAYS = 7;
    /** "실행 취소"가 달린 토스트가 떠 있는 시간 — 기본 3초는 누르기엔 짧다 */
    static readonly UNDO_LIFE_MS = 6000;
    static readonly ERROR_LIFE_MS = 8000;
    /** 참석 응답 단추 — 참석 · 불참 · 미정(이 순서) */
    static readonly RESPONSES = RESPONSES;
    /** 첫 오류 칸으로 초점을 옮길 차례(화면 위에서 아래) */
    static readonly FIELD_ORDER: readonly GroupEventField[] = ['title', 'startsAt', 'endsAt', 'mode', 'place', 'description', 'offlineAck'];

    /* ── 주소 ── */

    /** 소모임 상세의 일정 탭 */
    static tabPath(groupId: string): string {
        return UtilGroup.tabPath(groupId, 'events');
    }

    static chatTabPath(groupId: string): string {
        return UtilGroup.tabPath(groupId, 'chat');
    }

    static eventPath(groupId: string, eventId: string): string {
        return `${eventsBase(groupId)}/${encodeURIComponent(eventId)}`;
    }

    static editPath(groupId: string, eventId: string): string {
        return `${UtilGroupEvent.eventPath(groupId, eventId)}/edit`;
    }

    static newPath(groupId: string): string {
        return `${eventsBase(groupId)}/new`;
    }

    static pastPath(groupId: string): string {
        return `${eventsBase(groupId)}/past`;
    }

    /* ── 날짜·시각 ── */

    /** 목록 줄의 날짜 배지 — `{ month: '10월'|'Oct', day: '5', weekday: '일'|'Sun' }`(표시 시간대·언어). 시각이 잘못됐으면 null */
    static dateBadge(iso: string | null | undefined, time: DisplayTime): GroupEventDateBadge | null {
        const p = time.parts(iso);
        if (!p) return null;
        const language = time.display.language;
        return { month: monthName(language, p.mo), day: String(p.d), weekday: UtilTimeWords.of(language).weekday(p.weekday) };
    }

    /**
     * 일시 한 줄 — "10월 5일 (일) 20:00" · 끝이 같은 날이면 "… ~ 21:30" · 다른 날이면 "… ~ 10월 6일 (월) 02:00"(표시 시간대·시간 형식·언어, 올해가 아니면 연이 붙는다).
     * @returns 시작이 잘못됐으면 빈 문자열.
     */
    static when(startsAt: string | null | undefined, endsAt: string | null | undefined, time: DisplayTime): string {
        const s = time.parts(startsAt);
        if (!s) return '';
        const start = time.slot(startsAt);
        const e = time.parts(endsAt);
        if (!e) return start;
        return dayNumber(e) === dayNumber(s) ? `${start} ~ ${time.clock(endsAt)}` : `${start} ~ ${time.slot(endsAt)}`;
    }

    /** 시작까지 표시 시간대 달력으로 며칠 남았나(오늘 0 · 내일 1 · 지난날은 음수). 시각이 잘못됐으면 null */
    static dayOffset(iso: string | null | undefined, time: DisplayTime, now: number = Date.now()): number | null {
        const k = time.parts(iso);
        const today = time.parts(new Date(now).toISOString());
        if (!k || !today) return null;
        return dayNumber(k) - dayNumber(today);
    }

    /* ── 상태·응답 ── */

    /** 끝나지 않은 일정(시작 전·진행 중)만 고칠 수 있다 */
    static isEditable(state: GroupEventState): boolean {
        return state === 'UPCOMING' || state === 'ONGOING';
    }

    /** 취소된 일정의 줄에는 내 응답을 보이지 않는다 */
    static showsResponse(state: GroupEventState): boolean {
        return state !== 'CANCELED';
    }

    static stateTone(state: GroupEventState): GroupEventTone | null {
        if (state === 'ONGOING') return 'green';
        if (state === 'ENDED') return 'gray';
        if (state === 'CANCELED') return 'amber';
        return null;
    }

    static responseTone(response: GroupEventResponse | null): GroupEventTone {
        return response === 'GOING' ? 'green' : response === 'MAYBE' ? 'amber' : 'gray';
    }

    static modeTone(mode: GroupEventMode): GroupEventTone {
        return mode === 'ONLINE' ? 'blue' : 'amber';
    }

    /** 응답을 바꿨을 때의 집계 — 이전 칸에서 하나 빼고 새 칸에 하나 더한다(서버 답 전에 먼저 바꿔 보이는 용도, 0 아래로 내려가지 않는다) */
    static shiftCounts(counts: GroupEventCounts, from: GroupEventResponse | null, to: GroupEventResponse | null): GroupEventCounts {
        const next: GroupEventCounts = { ...counts };
        if (from === to) return next;
        if (from) next[COUNT_KEY[from]] = Math.max(0, next[COUNT_KEY[from]] - 1);
        if (to) next[COUNT_KEY[to]] += 1;
        return next;
    }

    /* ── 오류 ── */

    static failureOf(e: unknown): GroupEventFailure {
        return e instanceof ApiError ? (FAILURE_BY_CODE[e.code] ?? 'error') : 'error';
    }

    static isChanged(e: unknown): boolean {
        return e instanceof ApiError && CHANGED_CODES.has(e.code);
    }

    static isGone(e: unknown): boolean {
        return e instanceof ApiError && GONE_CODES.has(e.code);
    }

    /* ── 만들기·수정 폼 ── */

    /** 지금 적용되는 일정 한도 — 상세의 `limits`. 칸이 없거나 이상하면 기본 366·7일 */
    static limitsOf(limits: GroupLimits | null | undefined): GroupEventLimits {
        return {
            maxDaysAhead: wholeOr(limits?.eventMaxDaysAhead, UtilGroupEvent.DEFAULT_MAX_DAYS_AHEAD),
            maxLengthDays: wholeOr(limits?.eventMaxLengthDays, UtilGroupEvent.DEFAULT_MAX_LENGTH_DAYS),
        };
    }

    /** 시작으로 고를 수 있는 가장 늦은 순간 — 지금 + 시작 한도 일수 */
    static maxStart(now: number, limits: GroupEventLimits): string {
        return new Date(now + limits.maxDaysAhead * DAY_MS).toISOString();
    }

    /** 끝으로 고를 수 있는 가장 늦은 순간 — 시작 + 길이 한도 일수. 시작이 없으면 undefined */
    static maxEnd(startsAt: string, limits: GroupEventLimits): string | undefined {
        const start = ms(startsAt);
        return start === null ? undefined : new Date(start + limits.maxLengthDays * DAY_MS).toISOString();
    }

    /** 글자 수(코드 포인트 — 서버와 같다) */
    static length(text: string): number {
        return Array.from(text).length;
    }

    static emptyForm(): GroupEventFormState {
        return { title: '', startsAt: '', endsAt: '', mode: 'ONLINE', place: '', description: '', offlineAck: false };
    }

    /** 고칠 일정으로 폼을 채운다 — 확인 체크는 비운다(이미 오프라인인 일정은 다시 묻지 않는다) */
    static formOf(detail: Pick<GroupEventDetail, 'title' | 'startsAt' | 'endsAt' | 'mode' | 'place' | 'description'>): GroupEventFormState {
        return {
            title: detail.title,
            startsAt: detail.startsAt,
            endsAt: detail.endsAt ?? '',
            mode: detail.mode,
            place: detail.place ?? '',
            description: detail.description ?? '',
            offlineAck: false,
        };
    }

    /** 칸 검사 — 칸마다 문제 하나(위에서 처음 걸린 것). 판정은 서버(`GroupEventRules`)다 */
    static problems(form: GroupEventFormState, ctx: GroupEventCheckContext): GroupEventProblems {
        const problems: GroupEventProblems = {};
        const title = UtilGroupEvent.length(form.title.trim());
        if (title === 0) problems.title = 'REQUIRED';
        else if (title > UtilGroupEvent.TITLE_MAX) problems.title = 'LENGTH';

        const start = ms(ctx.lockedStartsAt ?? form.startsAt);
        if (start === null) problems.startsAt = 'REQUIRED';
        else if (!ctx.lockedStartsAt) {
            if (start <= ctx.now) problems.startsAt = 'PAST';
            else if (start > ctx.now + ctx.limits.maxDaysAhead * DAY_MS) problems.startsAt = 'TOO_FAR';
        }

        const end = ms(form.endsAt);
        if (end !== null && start !== null) {
            if (end <= start) problems.endsAt = 'BEFORE_START';
            else if (end > start + ctx.limits.maxLengthDays * DAY_MS) problems.endsAt = 'TOO_LONG';
            else if (ctx.lockedStartsAt && end <= ctx.now) problems.endsAt = 'PAST_ONGOING';
        }

        if (form.mode === 'OFFLINE') {
            const place = UtilGroupEvent.length(form.place.trim());
            if (place === 0) problems.place = 'REQUIRED';
            else if (place > UtilGroupEvent.PLACE_MAX) problems.place = 'LENGTH';
            if (ctx.ackRequired && !form.offlineAck) problems.offlineAck = 'ACK';
        }
        if (UtilGroupEvent.length(form.description.trim()) > UtilGroupEvent.DESCRIPTION_MAX) problems.description = 'LENGTH';
        return problems;
    }

    /** 폼 → 요청 본문. 모든 칸을 보낸다(비운 끝 시각·설명은 null). 이미 시작한 일정은 서버가 준 시작 시각을 그대로 보낸다 */
    static toRequest(form: GroupEventFormState, lockedStartsAt: string | null): GroupEventRequest {
        const place = form.place.trim();
        const description = form.description.trim();
        return {
            title: form.title.trim(),
            startsAt: lockedStartsAt ?? form.startsAt,
            endsAt: form.endsAt || null,
            mode: form.mode,
            place: form.mode === 'OFFLINE' && place ? place : null,
            description: description || null,
            offlineAck: form.mode === 'OFFLINE' && form.offlineAck,
        };
    }

    /** 처음 값과 달라졌나 — 나가기 확인에 쓴다 */
    static isDirty(form: GroupEventFormState, original: GroupEventFormState): boolean {
        return (
            form.title.trim() !== original.title.trim() ||
            ms(form.startsAt) !== ms(original.startsAt) ||
            ms(form.endsAt) !== ms(original.endsAt) ||
            form.mode !== original.mode ||
            form.place.trim() !== original.place.trim() ||
            form.description.trim() !== original.description.trim() ||
            form.offlineAck !== original.offlineAck
        );
    }
}
