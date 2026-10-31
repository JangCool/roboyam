import type { AdBookingStatus, AdDaySlot, AdType } from '~/composables/ad/api/useAdApi.types';
import { BIZ_AD_TYPES } from '~/composables/bizapplication/api/useBizApplicationApi.types';

type Severity = 'secondary' | 'info' | 'success' | 'warn' | 'danger' | 'contrast';

/**
 * 광고 일정표 화면 규칙 — 입력만 보고 답하는 순수 함수(서버 `AdAvailabilityJudge` 예정이 저장 때 다시 판정한다).
 */
export default class UtilAd {
    static readonly TYPES: readonly AdType[] = BIZ_AD_TYPES;

    /** 긴급 내림 뒤 다른 서버 사본이 비워지기까지(초) — 로보얌 20초 사본 */
    static readonly CACHE_SECONDS = 20;

    static isBanner(type: AdType): boolean {
        return type === 'WIDE_BANNER' || type === 'STRIP_BANNER' || type === 'SQUARE_BANNER' || type === 'SMALL_BANNER';
    }

    static statusSeverity(status: AdBookingStatus): Severity {
        switch (status) {
            case 'RUNNING':
                return 'success';
            case 'SCHEDULED':
                return 'contrast';
            case 'SUBMITTED':
            case 'AWAITING_PAYMENT':
            case 'AWAITING_DEPOSIT':
                return 'warn';
            case 'TAKEN_DOWN':
                return 'danger';
            default:
                return 'secondary';
        }
    }

    /** 날짜 점 색 — 0 = 꽉 참(빨강), 1 = 거의(주황), 그 밖 = 여유(초록) */
    static dotClass(slot: AdDaySlot | undefined): string {
        if (!slot) return 'bg-transparent';
        if (slot.remaining <= 0) return 'bg-red-500';
        if (slot.remaining === 1 || slot.remaining / slot.limit <= 0.25) return 'bg-amber-500';
        return 'bg-emerald-500';
    }

    /** `YYYY-MM` — 달력이 보는 첫 달 */
    static monthOf(year: number, monthIndex: number): string {
        return `${year}-${String(monthIndex + 1).padStart(2, '0')}`;
    }

    static ymd(year: number, monthIndex: number, day: number): string {
        return `${year}-${String(monthIndex + 1).padStart(2, '0')}-${String(day).padStart(2, '0')}`;
    }

    /** 긴급 내림 가능(진행·예약) */
    static canTakedown(status: AdBookingStatus): boolean {
        return status === 'RUNNING' || status === 'SCHEDULED';
    }
}
