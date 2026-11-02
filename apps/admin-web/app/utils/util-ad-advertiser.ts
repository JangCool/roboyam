import type { AdAdvertiserRow } from '~/composables/ad/api/useAdAdvertiserApi.types';
import type { AdStatRange } from '~/composables/ad/api/useAdApi.types';
import UtilBizApplication from '~/utils/util-biz-application';

type Severity = 'secondary' | 'info' | 'success' | 'warn' | 'danger' | 'contrast';

/** 목록·서랍의 상태 꼬리표 — 직접 등록은 사용 중/보관, 사업자는 신원·광고 서비스 상태를 따른다 */
export type AdAdvertiserBadge = 'ACTIVE' | 'ARCHIVED' | 'BIZ_ACTIVE' | 'BIZ_AD_SUSPENDED' | 'BIZ_AD_OFF' | 'BIZ_SUSPENDED' | 'BIZ_WITHDRAWN' | 'BIZ_REVIEW';

const DAY_MS = 86_400_000;

/**
 * 광고주 관리 화면 규칙 — 입력만 보고 답하는 순수 함수(서버가 다시 판정한다).
 */
export default class UtilAdAdvertiser {
    /** 서랍 성과 기본 범위(오늘 포함 최근 30일) */
    static readonly STATS_DAYS = 30;

    static badge(row: Pick<AdAdvertiserRow, 'kind' | 'state' | 'business'>): AdAdvertiserBadge {
        const b = row.business;
        if (row.kind === 'ADVERTISER' || !b) return row.state === 'ARCHIVED' ? 'ARCHIVED' : 'ACTIVE';
        if (b.status === 'WITHDRAWN') return 'BIZ_WITHDRAWN';
        if (b.status === 'SUSPENDED') return 'BIZ_SUSPENDED';
        if (b.status !== 'APPROVED') return 'BIZ_REVIEW';
        if (b.adService === 'SUSPENDED') return 'BIZ_AD_SUSPENDED';
        if (b.adService !== 'APPROVED') return 'BIZ_AD_OFF';
        return 'BIZ_ACTIVE';
    }

    static badgeSeverity(badge: AdAdvertiserBadge): Severity {
        switch (badge) {
            case 'ACTIVE':
            case 'BIZ_ACTIVE':
                return 'success';
            case 'BIZ_AD_SUSPENDED':
                return 'warn';
            case 'BIZ_SUSPENDED':
                return 'danger';
            case 'BIZ_REVIEW':
                return 'info';
            default:
                return 'secondary';
        }
    }

    /** 오늘 포함 최근 `days` 날(KST) */
    static recentRange(days = UtilAdAdvertiser.STATS_DAYS, now = Date.now()): AdStatRange {
        const to = UtilBizApplication.kstToday(now);
        const from = UtilBizApplication.kstDate(new Date(Date.parse(`${to}T00:00:00+09:00`) - (days - 1) * DAY_MS).toISOString());
        return { from, to };
    }

    /** 광고 성과 화면에서 이 광고주로 거른 주소 */
    static performanceLink(id: string, range: AdStatRange): { path: string; query: Record<string, string> } {
        return { path: '/ads/performance', query: { advertiserId: id, from: range.from, to: range.to } };
    }
}
