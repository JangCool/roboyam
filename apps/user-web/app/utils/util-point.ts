import type { PointSummary } from '~/composables/point/api/usePointApi.types';
import type { Restriction } from '~/composables/auth/api/useAuthApi.types';

/** 포인트 지급이 막히는 까닭 — 이메일 인증 전 · 정지 중 */
export type PointBlockedReason = 'EMAIL_NOT_VERIFIED' | 'SUSPENDED';

/**
 * 포인트 표시·판정 — 셸(헤더 메뉴·햄버거)과 내 정보(`/me`)·포인트 화면(`/me/points`)이 쓴다.
 * 원본(community)의 `UtilPoint` 에 있는 출석 달력·교환 규칙은 로보얌에 그 기능이 없어 옮기지 않았다.
 */

/** 천 단위 쉼표 — 소수는 버린다 */
function grouped(n: number): string {
    const rounded = Math.trunc(Math.abs(n));
    return String(rounded).replace(/\B(?=(\d{3})+(?!\d))/g, ',');
}

export default class UtilPoint {
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
     * 출석 기능이 없으면(지금은 서버가 늘 false) 「오늘 출석하기」 안내를 그리지 않는다.
     * @param {PointSummary | null | undefined} summary - 포인트 요약(없으면 권하지 않는다).
     * @param {{ emailVerified: boolean; restriction: Restriction | null }} input - 로그인 정보.
     * @returns {boolean} 권할 만하면 true.
     */
    static attendable(summary: PointSummary | null | undefined, input: { emailVerified: boolean; restriction: Restriction | null }): boolean {
        if (!summary || !summary.attendanceAvailable) return false;
        return !summary.today.attended && UtilPoint.blockedReason(input) === null;
    }
}
