import type { AnalyticsSwitch } from '~/composables/analytics/api/useAnalyticsSettingsApi.types';

/**
 * 통계 수집 설정(`/settings/analytics`)의 주소·항목 앵커 — 설정 화면과 대시보드 가젯의 「수집 꺼짐」 링크가 같이 쓴다. 상태도 요청도 없다.
 * 항목 하나 = 화면의 한 줄(`id` = 앵커). 앵커는 칸 이름을 하이픈으로 바꾼 것(`rawEvents` → `analytics-raw-events`).
 */

/** 설정 화면 주소 */
const PATH = '/settings/analytics';

/** 칸마다 부담 정도(설정 화면 꼬리표) — 방문 수집 전체(`enabled`)는 없음 */
const LOAD: Partial<Record<AnalyticsSwitch, 'LOW' | 'MEDIUM'>> = {
    visits: 'LOW',
    rawEvents: 'MEDIUM',
    referrers: 'LOW',
    flows: 'MEDIUM',
    notificationSends: 'LOW',
    boardActivity: 'LOW',
};

export default class UtilAnalytics {
    static readonly PATH = PATH;

    /**
     * 항목 줄의 앵커(`id`)
     * @param {AnalyticsSwitch} key - 칸.
     * @returns {string} 앵커(`analytics-raw-events`).
     */
    static anchor(key: AnalyticsSwitch): string {
        return `analytics-${key.replace(/[A-Z]/g, (c) => `-${c.toLowerCase()}`)}`;
    }

    /**
     * 그 항목으로 바로 가는 주소
     * @param {AnalyticsSwitch} key - 칸.
     * @returns {string} 주소(`/settings/analytics#analytics-raw-events`).
     */
    static link(key: AnalyticsSwitch): string {
        return `${PATH}#${UtilAnalytics.anchor(key)}`;
    }

    /**
     * 부담 정도
     * @param {AnalyticsSwitch} key - 칸.
     * @returns {'LOW' | 'MEDIUM' | null} 부담(없으면 null).
     */
    static load(key: AnalyticsSwitch): 'LOW' | 'MEDIUM' | null {
        return LOAD[key] ?? null;
    }
}
