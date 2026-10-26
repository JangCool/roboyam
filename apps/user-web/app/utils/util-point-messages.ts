import type { Translate } from '#layers/i18n/utils/util-i18n';
import type { AttendanceStreakRule, AttendResult, PointRules } from '~/composables/point/api/usePointApi.types';
import UtilPoint, { type AttendanceCalendarCell, type PointBlockedReason } from '~/utils/util-point';

/** 제목과 설명 한 쌍 — 배너·안내 상자에 쓴다 */
export interface PointCopy {
    title: string;
    text: string;
}

/** 영어 달 이름(`October`) — 영어 달 머리·칸 이름의 자리표시자. 한국어 문구는 쓰지 않는다 */
function monthName(month: number): string {
    return new Intl.DateTimeFormat('en-US', { month: 'long', timeZone: 'UTC' }).format(Date.UTC(2000, month - 1, 1));
}

/**
 * 포인트의 계산이 낀 문구 — 셸(헤더 메뉴·햄버거)·내 정보·포인트 화면(적립 조건·출석 달력, 커머스 1-3). 원본(community)은 포인트 화면 전체의 문구를 담은 큰 파일이다.
 * 문구 본문은 `i18n/locales/{ko,en}/point.ts` 에 있고, 여기는 값이 끼는 조합만 한다. 번역 함수 `t` 를 **첫 인자로 받는다**(`.claude/guides/frontend/i18n.md`).
 * 상수 문구(포인트 화면 입구 이름 `point.entry`)는 컴포넌트가 `t` 로 바로 읽는다.
 */
export default class UtilPointMessages {
    /**
     * 출석 단추의 글자 — 받을 포인트는 서버 설정값이다. 값을 아직 모르면 금액 없이 "오늘 출석하기".
     * @param {Translate} t - 번역 함수.
     * @param {number | null | undefined} attendance - 출석으로 받는 포인트.
     * @returns {string} "오늘 출석하기 +10P".
     */
    static attendLabel(t: Translate, attendance: number | null | undefined): string {
        return typeof attendance === 'number' && Number.isFinite(attendance) && attendance > 0
            ? t('point.attendWithReward', { amount: UtilPoint.number(attendance) })
            : t('point.attend');
    }

    /**
     * 햄버거 메뉴의 보유 포인트 한 줄 — "포인트 1,280P" · 출석을 권할 만하면 "포인트 1,280P · 오늘 출석하기 +10P".
     * @param {Translate} t - 번역 함수.
     * @param {number} balance - 보유 포인트.
     * @param {boolean} attendable - 오늘 출석을 권할 만한가.
     * @param {Pick<PointRules, 'attendance'>} rules - 지급 규칙.
     * @returns {string} 한 줄.
     */
    static summaryLine(t: Translate, balance: number, attendable: boolean, rules: Pick<PointRules, 'attendance'>): string {
        const base = t('point.balanceLine', { points: UtilPoint.points(balance) });
        return attendable ? t('point.summaryWithAttend', { balance: base, attend: UtilPointMessages.attendLabel(t, rules.attendance) }) : base;
    }

    /**
     * 적립 조건 목록(포인트 화면 「적립 규칙」, 커머스 1-3) — 0·false 인 조건은 빼고 말한다. 남는 조건이 없으면 빈 목록.
     * @param {Translate} t - 번역 함수.
     * @param {PointRules} rules - 지급 규칙.
     * @returns {string[]} 「글은 본문 50자 이상(…)」 · 「댓글은 10자 이상 · 내 글에 단 댓글 제외 · 한 글에 한 번」 · 「… 30초 안에 …」 · 「활동 적립은 하루 200P까지 …」.
     */
    static earnConditions(t: Translate, rules: PointRules): string[] {
        const out: string[] = [];
        if (rules.postMinLength > 0) out.push(t('point.condPost', { n: UtilPoint.number(rules.postMinLength) }));
        const comment: string[] = [];
        if (rules.commentMinLength > 0) comment.push(t('point.condCommentMin', { n: UtilPoint.number(rules.commentMinLength) }));
        if (rules.commentOwnPostExcluded) comment.push(t('point.condCommentOwnPost'));
        if (rules.commentOncePerPost) comment.push(t('point.condCommentOnce'));
        if (comment.length) out.push(t('point.condComment', { rules: comment.join(' · ') }));
        if (rules.writeIntervalSeconds > 0) out.push(t('point.condInterval', { seconds: UtilPoint.number(rules.writeIntervalSeconds) }));
        if (rules.dailyTotalCap > 0) out.push(t('point.condDailyTotal', { cap: UtilPoint.points(rules.dailyTotalCap) }));
        return out;
    }

    /**
     * 내 정보 바로가기 「포인트·출석체크」 줄 오른쪽의 작은 안내 — "1,280P", 오늘 출석을 권할 만하면 "1,280P · 출석 +10P".
     * @param {Translate} t - 번역 함수.
     * @param {number} balance - 보유 포인트.
     * @param {boolean} attendable - 오늘 출석을 권할 만한가.
     * @param {number | null | undefined} attendance - 출석으로 받는 포인트(0·모르면 출석 안내를 뺀다).
     * @returns {string} 안내 글자.
     */
    static entryHint(t: Translate, balance: number, attendable: boolean, attendance: number | null | undefined): string {
        const base = UtilPoint.points(balance);
        return attendable && typeof attendance === 'number' && attendance > 0 ? t('point.entryHint', { balance: base, amount: UtilPoint.number(attendance) }) : base;
    }

    /* ── 출석 달력(커머스 1-3) ── */

    /**
     * 달 머리 — "2026년 10월" · "October 2026"
     * @param {Translate} t - 번역 함수.
     * @param {string} month - `YYYY-MM`.
     * @returns {string} 머리 글자. 모양이 틀리면 빈 글자.
     */
    static monthLabel(t: Translate, month: string): string {
        const parts = UtilPoint.monthParts(month);
        return parts ? t('point.calMonth', { year: parts.year, month: parts.month, monthName: monthName(parts.month) }) : '';
    }

    /**
     * 달력 머리의 요일(일요일부터 7개)
     * @param {Translate} t - 번역 함수.
     * @returns {string[]} 요일 글자.
     */
    static weekdays(t: Translate): string[] {
        return t('point.calWeekdays').split(',');
    }

    /**
     * 칸 하나의 스크린리더 이름 — "10월 6일 출석함 · 오늘"
     * @param {Translate} t - 번역 함수.
     * @param {string} month - `YYYY-MM`.
     * @param {AttendanceCalendarCell} cell - 달력 칸.
     * @returns {string} 이름. 빈 칸이면 빈 글자.
     */
    static cellLabel(t: Translate, month: string, cell: AttendanceCalendarCell): string {
        const parts = UtilPoint.monthParts(month);
        if (!parts || cell.day === null) return '';
        const date = t('point.calDay', { month: parts.month, day: cell.day, monthName: monthName(parts.month) });
        const label = cell.attended ? t('point.calDayAttended', { date }) : date;
        return cell.today ? t('point.calDayToday', { label }) : label;
    }

    /**
     * 다음 연속 출석 보너스까지 — 보너스가 꺼졌으면 빈 글자. 오늘 출석 전이고 오늘이 보너스 날이면 "오늘 출석하면 연속 보너스 +20P", 아니면 "3일 더 출석하면 +20P".
     * @param {Translate} t - 번역 함수.
     * @param {number} streak - 지금 연속 출석 일수.
     * @param {AttendanceStreakRule | null | undefined} rule - 연속 출석 보너스 규칙.
     * @param {boolean} attendedToday - 오늘 출석했나.
     * @returns {string} 안내 한 줄.
     */
    static streakNext(t: Translate, streak: number, rule: AttendanceStreakRule | null | undefined, attendedToday: boolean): string {
        if (!UtilPoint.streakBonusOn(rule)) return '';
        const days = UtilPoint.daysToStreakBonus(streak, rule.days);
        const bonus = UtilPoint.number(rule.bonus);
        return days === 1 && !attendedToday ? t('point.streakNextToday', { bonus }) : t('point.streakNext', { days, bonus });
    }

    /**
     * 출석 성공 토스트 — 받은 포인트(+ 연속 보너스를 받았으면 함께). 동결 중처럼 받은 것이 없으면 "출석했어요".
     * @param {Translate} t - 번역 함수.
     * @param {AttendResult} result - 출석 결과.
     * @returns {string} 토스트 문구.
     */
    static attendedToast(t: Translate, result: AttendResult): string {
        if (result.streakBonus > 0) {
            return t('point.attendedToastStreak', { amount: UtilPoint.number(result.earned), streak: UtilPoint.number(result.streak), bonus: UtilPoint.number(result.streakBonus) });
        }
        return result.earned > 0 ? t('point.attendedToast', { amount: UtilPoint.number(result.earned) }) : t('point.attendedToastNoPoints');
    }

    /**
     * 출석 요청이 실패했을 때의 안내 — 서버가 분명히 거절했으면(4xx) "포인트는 지급되지 않았어요", 결과를 모르면 다시 불러와 확인한다고 말한다.
     * @param {Translate} t - 번역 함수.
     * @param {boolean} definite - 서버가 분명히 거절했나.
     * @returns {PointCopy} 제목과 설명.
     */
    static attendFailed(t: Translate, definite: boolean): PointCopy {
        return definite ? { title: t('point.attendFailedTitle'), text: t('point.attendFailedText') } : { title: t('point.attendUnknownTitle'), text: t('point.attendUnknownText') };
    }

    /**
     * 요청 제한(429) 안내
     * @param {Translate} t - 번역 함수.
     * @returns {PointCopy} 제목과 설명.
     */
    static attendRateLimited(t: Translate): PointCopy {
        return { title: t('point.attendRateLimitedTitle'), text: t('point.attendRateLimitedText') };
    }

    /**
     * 출석을 막는 까닭별 안내 — 정지 중 · 이메일 인증 전
     * @param {Translate} t - 번역 함수.
     * @param {PointBlockedReason} reason - 막는 까닭.
     * @returns {PointCopy} 제목과 설명.
     */
    static blocked(t: Translate, reason: PointBlockedReason): PointCopy {
        return reason === 'SUSPENDED'
            ? { title: t('point.blockedSuspendedTitle'), text: t('point.blockedSuspendedText') }
            : { title: t('point.blockedEmailTitle'), text: t('point.blockedEmailText') };
    }
}
