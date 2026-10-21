import type { Translate } from '#layers/i18n/utils/util-i18n';
import type { PointRules } from '~/composables/point/api/usePointApi.types';
import UtilPoint from '~/utils/util-point';

/**
 * 포인트 화면 문구 중 셸(헤더 메뉴·햄버거)이 쓰는 것만 남긴 단순 버전 — 원본(community)은 포인트 화면 전체의 문구를 담은 큰 파일이다.
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
}
