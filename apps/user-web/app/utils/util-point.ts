import UtilTime from '#layers/display-time/utils/util-time';
import type { AttendanceMonth, AttendanceStreakRule, PointSummary } from '~/composables/point/api/usePointApi.types';
import type { Restriction } from '~/composables/auth/api/useAuthApi.types';
import { ApiError } from '~/composables/common/useApiClient';

/** 포인트 지급이 막히는 까닭 — 이메일 인증 전 · 정지 중 */
export type PointBlockedReason = 'EMAIL_NOT_VERIFIED' | 'SUSPENDED';

/** 출석 요청이 끝난 갈래 — 이미 출석함 · 막힘 · 요청 제한 · 로그인 풀림 · 그 밖 */
export type AttendOutcome = 'ALREADY_DONE' | 'BLOCKED' | 'RATE_LIMITED' | 'UNAUTHENTICATED' | 'FAILED';

/** 출석 달력 한 칸 — 앞뒤 빈 칸은 `day: null` */
export interface AttendanceCalendarCell {
    day: number | null;
    attended: boolean;
    today: boolean;
}

/**
 * 포인트 표시·판정 — 셸(헤더 메뉴·햄버거)과 내 정보(`/me`)·포인트 화면(`/me/points`)이 쓴다.
 * 출석 달력(커머스 1-3)은 원본(community)의 `UtilPoint` 를 옮겼다 — 날짜는 모두 서비스 하루(한국 시간)다. 교환 규칙은 로보얌에 그 기능이 없어 옮기지 않았다.
 */

const MONTH_PATTERN = /^(\d{4})-(0[1-9]|1[0-2])$/;

/** 두 자리로 — `07` */
function pad(n: number): string {
    return String(n).padStart(2, '0');
}

/** 천 단위 쉼표 — 소수는 버린다 */
function grouped(n: number): string {
    const rounded = Math.trunc(Math.abs(n));
    return String(rounded).replace(/\B(?=(\d{3})+(?!\d))/g, ',');
}

export default class UtilPoint {
    /** 달력을 거슬러 갈 수 있는 달 수(community 와 같다 — 그보다 오래된 달은 기록이 있어도 보지 않는다) */
    static readonly MAX_MONTHS_BACK = 24;

    /**
     * 포인트 — `1,280P`
     * @param {number} value - 포인트.
     * @returns {string} `1,280P` 꼴의 글자. 숫자가 아니면 `0P`.
     */
    static points(value: number): string {
        return `${grouped(Number.isFinite(value) ? value : 0)}P`;
    }

    /**
     * 천 단위 쉼표만 붙인 숫자 — `1,280`
     * @param {number} value - 숫자.
     * @returns {string} 쉼표가 붙은 글자(소수는 버린다).
     */
    static number(value: number): string {
        return grouped(Number.isFinite(value) ? value : 0);
    }

    /**
     * 부호를 붙인 증감 — `+20P` · `−10P` · `0P`(내역 한 줄의 오른쪽). 빼기는 읽기 쉬운 마이너스 기호(U+2212)다.
     * @param {number} delta - 증감.
     * @returns {string} 부호가 붙은 포인트 글자.
     */
    static signed(delta: number): string {
        const n = Number.isFinite(delta) ? Math.trunc(delta) : 0;
        if (n > 0) return `+${UtilPoint.points(n)}`;
        if (n < 0) return `−${UtilPoint.points(n)}`;
        return UtilPoint.points(0);
    }

    /**
     * 오늘 그 일로 더 받을 수 있는 횟수 — 상한이 0(꺼짐)이면 null.
     * @param {{ count: number; cap: number }} value - 오늘 받은 횟수와 하루 상한.
     * @returns {number | null} 남은 횟수(0 이상). 꺼져 있으면 null.
     */
    static remaining(value: { count: number; cap: number }): number | null {
        if (!(value.cap > 0)) return null;
        return Math.max(0, value.cap - Math.max(0, value.count));
    }

    /**
     * 오늘 진행 막대의 비율(0~100) — 상한이 0(꺼짐)이면 0.
     * @param {{ count: number; cap: number }} value - 오늘 받은 횟수와 하루 상한.
     * @returns {number} 0~100.
     */
    static progress(value: { count: number; cap: number }): number {
        if (!(value.cap > 0)) return 0;
        return Math.min(100, Math.max(0, Math.round((value.count / value.cap) * 100)));
    }

    /**
     * 화면이 미리 아는 막는 까닭 — 정지·영구 정지 중이면 `SUSPENDED`, 이메일 인증 전이면 `EMAIL_NOT_VERIFIED`, 아니면 null. 정지가 먼저다.
     * 화면 검사는 편의이고 판정은 서버가 한다.
     * @param {{ emailVerified: boolean; restriction: Restriction | null }} input - 로그인 정보.
     * @returns {PointBlockedReason | null} 막는 까닭. 막히지 않으면 null.
     */
    static blockedReason(input: { emailVerified: boolean; restriction: Restriction | null }): PointBlockedReason | null {
        const status = input.restriction?.status;
        if (status === 'SUSPENDED' || status === 'BANNED') return 'SUSPENDED';
        if (!input.emailVerified) return 'EMAIL_NOT_VERIFIED';
        return null;
    }

    /**
     * 오늘 출석을 권할 만한가 — 요약이 있고 서버에 출석 기능이 있으며(`attendanceAvailable`) 아직 출석 전이고 막는 까닭(정지 중·인증 전)이 없을 때.
     * 출석 기능이 없으면(`attendanceAvailable=false`) 「오늘 출석하기」 안내를 그리지 않는다.
     * @param {PointSummary | null | undefined} summary - 포인트 요약(없으면 권하지 않는다).
     * @param {{ emailVerified: boolean; restriction: Restriction | null }} input - 로그인 정보.
     * @returns {boolean} 권할 만하면 true.
     */
    static attendable(summary: PointSummary | null | undefined, input: { emailVerified: boolean; restriction: Restriction | null }): boolean {
        if (!summary || !summary.attendanceAvailable) return false;
        return !summary.today.attended && UtilPoint.blockedReason(input) === null;
    }

    /* ── 출석 달력(커머스 1-3 — community `UtilPoint` 의 출석 달력을 옮겼다) ── */

    /**
     * 서버 오류 코드에서 막는 까닭을 알아본다 — `ACCOUNT_RESTRICTED` 는 `SUSPENDED`, `EMAIL_NOT_VERIFIED` 는 그대로.
     * @param {string} code - 오류 코드.
     * @returns {PointBlockedReason | null} 막는 까닭. 해당하지 않으면 null.
     */
    static blockedReasonOf(code: string): PointBlockedReason | null {
        if (code === 'ACCOUNT_RESTRICTED') return 'SUSPENDED';
        if (code === 'EMAIL_NOT_VERIFIED') return 'EMAIL_NOT_VERIFIED';
        return null;
    }

    /**
     * 출석 요청이 어떻게 끝났는지의 갈래 — 이미 출석함 · 막힘(정지·인증 전) · 요청 제한 · 로그인 풀림 · 그 밖. 문구가 아니라 상태·코드로 가른다.
     * @param {unknown} e - 잡은 오류.
     * @returns {AttendOutcome} 갈래.
     */
    static outcomeOf(e: unknown): AttendOutcome {
        if (!(e instanceof ApiError)) return 'FAILED';
        if (e.code === 'ATTENDANCE_ALREADY_DONE') return 'ALREADY_DONE';
        if (UtilPoint.blockedReasonOf(e.code)) return 'BLOCKED';
        if (e.status === 429) return 'RATE_LIMITED';
        if (e.status === 401) return 'UNAUTHENTICATED';
        return 'FAILED';
    }

    /**
     * 서버가 분명히 거절한 오류인가(4xx) — 그렇다면 포인트가 움직이지 않았다고 말해도 된다. 연결 끊김·서버 오류(5xx)는 결과를 모른다.
     * @param {unknown} e - 잡은 오류.
     * @returns {boolean} 서버가 4xx 로 답했으면 true.
     */
    static isDefiniteRefusal(e: unknown): boolean {
        return e instanceof ApiError && e.status >= 400 && e.status < 500;
    }

    /**
     * 한국 시간(서비스 하루) 기준 이번 달 — `2026-10`. 서버가 준 오늘이 있으면 그쪽이 먼저다(이 값은 그 전의 처음 값).
     * @param {number} now - 지금(밀리초). 시험·고정 표시용이며 안 주면 현재 시각.
     * @returns {string} `YYYY-MM`.
     */
    static currentMonth(now: number = Date.now()): string {
        return (UtilTime.serviceDayOf(now) ?? '').slice(0, 7);
    }

    /**
     * 달을 앞뒤로 옮긴다 — 해를 넘겨도 맞다(`2026-01` 에서 −1 은 `2025-12`).
     * @param {string} month - `YYYY-MM`.
     * @param {number} delta - 옮길 달 수(음수는 과거).
     * @returns {string} 옮긴 달. 모양이 틀리면 받은 값 그대로.
     */
    static shiftMonth(month: string, delta: number): string {
        const match = MONTH_PATTERN.exec(month);
        if (!match) return month;
        const index = Number(match[1]) * 12 + (Number(match[2]) - 1) + delta;
        return `${Math.floor(index / 12)}-${pad((index % 12) + 1)}`;
    }

    /**
     * 두 달 사이의 달 수 — `to` 가 `from` 보다 뒤면 양수.
     * @param {string} from - 기준 달 `YYYY-MM`.
     * @param {string} to - 비교할 달 `YYYY-MM`.
     * @returns {number} 달 수. 모양이 틀리면 0.
     */
    static monthsBetween(from: string, to: string): number {
        const a = MONTH_PATTERN.exec(from);
        const b = MONTH_PATTERN.exec(to);
        if (!a || !b) return 0;
        return Number(b[1]) * 12 + Number(b[2]) - (Number(a[1]) * 12 + Number(a[2]));
    }

    /**
     * 지난달로 갈 수 있는가 — 이번 달에서 `MAX_MONTHS_BACK` 달까지.
     * @param {string} month - 지금 보는 달.
     * @param {string} current - 이번 달.
     * @returns {boolean} 더 거슬러 갈 수 있으면 true.
     */
    static canGoPrev(month: string, current: string): boolean {
        return UtilPoint.monthsBetween(month, current) < UtilPoint.MAX_MONTHS_BACK;
    }

    /**
     * 다음 달로 갈 수 있는가 — 이번 달이 끝이다(미래 달은 볼 것이 없다).
     * @param {string} month - 지금 보는 달.
     * @param {string} current - 이번 달.
     * @returns {boolean} 이번 달보다 앞이면 true.
     */
    static canGoNext(month: string, current: string): boolean {
        return UtilPoint.monthsBetween(month, current) > 0;
    }

    /**
     * 달의 연·월 숫자 — 달 머리·칸 이름 문구에 쓴다.
     * @param {string} month - `YYYY-MM`.
     * @returns {{ year: number; month: number } | null} 모양이 틀리면 null.
     */
    static monthParts(month: string): { year: number; month: number } | null {
        const match = MONTH_PATTERN.exec(month);
        return match ? { year: Number(match[1]), month: Number(match[2]) } : null;
    }

    /**
     * 달력 칸들 — 1일의 요일만큼 앞 빈 칸(일요일 시작), 그 달의 날들, 한 주를 채우는 뒤 빈 칸. 출석한 날과 오늘을 표시한다.
     * @param {string} month - `YYYY-MM`.
     * @param {readonly number[]} days - 출석한 날(일)의 번호.
     * @param {string | null} today - 한국 오늘 `YYYY-MM-DD`(이 달이 아니면 오늘 표시는 없다).
     * @returns {AttendanceCalendarCell[]} 7의 배수 개의 칸. 달 모양이 틀리면 빈 배열.
     */
    static cells(month: string, days: readonly number[], today: string | null): AttendanceCalendarCell[] {
        const parts = UtilPoint.monthParts(month);
        if (!parts) return [];
        const { year: y, month: m } = parts;
        const total = new Date(Date.UTC(y, m, 0)).getUTCDate();
        const lead = new Date(Date.UTC(y, m - 1, 1)).getUTCDay();
        const attended = new Set(days);
        const todayMatch = today ? /^(\d{4})-(\d{2})-(\d{2})$/.exec(today) : null;
        const todayDay = todayMatch && Number(todayMatch[1]) === y && Number(todayMatch[2]) === m ? Number(todayMatch[3]) : null;
        const cells: AttendanceCalendarCell[] = [];
        for (let i = 0; i < lead; i++) cells.push({ day: null, attended: false, today: false });
        for (let d = 1; d <= total; d++) cells.push({ day: d, attended: attended.has(d), today: d === todayDay });
        while (cells.length % 7 !== 0) cells.push({ day: null, attended: false, today: false });
        return cells;
    }

    /**
     * 출석 성공을 달력에 반영한다 — 보고 있는 달에 그 날짜가 속하면 출석한 날에 더하고 그 달 출석 수를 올린다(이미 있으면 올리지 않는다). 연속 일수는 응답 값으로 바꾼다.
     * @param {AttendanceMonth} data - 보고 있는 달의 출석 정보.
     * @param {string} date - 출석한 한국 날짜 `YYYY-MM-DD`.
     * @param {number} streak - 응답의 연속 출석 일수.
     * @returns {AttendanceMonth} 바뀐 새 값.
     */
    static withAttended(data: AttendanceMonth, date: string, streak: number): AttendanceMonth {
        const today = date === data.today.date ? { ...data.today, attended: true } : data.today;
        const day = Number(date.slice(8, 10));
        if (date.slice(0, 7) !== data.month || !(day > 0) || data.days.includes(day)) return { ...data, today, streak };
        return { ...data, days: [...data.days, day].sort((a, b) => a - b), monthCount: data.monthCount + 1, today, streak };
    }

    /**
     * 연속 출석 보너스가 켜져 있는가 — 며칠과 보너스가 둘 다 1 이상일 때.
     * @param {AttendanceStreakRule | null | undefined} rule - 연속 출석 보너스 규칙.
     * @returns {boolean} 켜져 있으면 true.
     */
    static streakBonusOn(rule: AttendanceStreakRule | null | undefined): rule is AttendanceStreakRule {
        return Boolean(rule) && rule!.days > 0 && rule!.bonus > 0;
    }

    /**
     * 다음 연속 출석 보너스까지 출석해야 하는 날 수 — 연속 일수가 `days` 의 배수가 될 때 보너스를 받는다.
     * 오늘 출석 전이면 `streak` 은 어제까지라 오늘 출석이 첫날로 세어지고, 출석 뒤면 내일부터 센다(어느 쪽이든 `days - streak % days`).
     * @param {number} streak - 지금 연속 출석 일수(끊겼으면 0).
     * @param {number} days - 보너스 주기(1 이상).
     * @returns {number} 1 이상의 날 수. 주기가 0 이하이면 0.
     */
    static daysToStreakBonus(streak: number, days: number): number {
        if (!(days > 0)) return 0;
        const s = Math.max(0, Math.trunc(streak));
        return days - (s % days);
    }
}
