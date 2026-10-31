import type { AdBookingRow, AdBookingStatus, AdDaySlot, AdTakedown, AdType } from '~/composables/ad/api/useAdApi.types';
import { BIZ_AD_TYPES } from '~/composables/bizapplication/api/useBizApplicationApi.types';
import UtilBizApplication from '~/utils/util-biz-application';

type Severity = 'secondary' | 'info' | 'success' | 'warn' | 'danger' | 'contrast';

/**
 * 광고 일정표 화면 규칙 — 입력만 보고 답하는 순수 함수(서버 `AdOperatorService` 가 저장 때 다시 판정한다).
 */
export default class UtilAd {
    static readonly TYPES: readonly AdType[] = BIZ_AD_TYPES;

    /** 긴급 내림 뒤 다른 서버 사본이 비워지기까지(초) — 로보얌 20초 사본 */
    static readonly CACHE_SECONDS = 20;

    /** 운영자 등록이 되는 유형 — 광고 종류(배너 넷·한 줄). 핫딜 노출 자리는 글이 있어야 해서 서버가 400 `fields.type`(서버 `BizProduct.adProduct()`) */
    static readonly MANUAL_TYPES: readonly AdType[] = ['WIDE_BANNER', 'STRIP_BANNER', 'SQUARE_BANNER', 'SMALL_BANNER', 'TEXT_LINE'];

    /** 운영자 등록 서랍이 칸 아래에 붙이는 서버 오류 키(`fields.*`) — 나머지는 서랍 위쪽에 모은다 */
    static readonly MANUAL_FIELDS: readonly string[] = ['advertiserId', 'period', 'weight', 'supply', 'creative.imageFileId', 'creative.linkUrl', 'creative.altText', 'creative.text', 'memo', 'contractFileIds'];

    static isManualType(type: AdType): boolean {
        return UtilAd.MANUAL_TYPES.includes(type);
    }

    /** 「광고 심사에서 열기」 — 건의 도메인 심사 화면(AD → `/ad-reviews`, HOTDEAL → `/hotdeal-reviews`) */
    static reviewLink(row: Pick<AdBookingRow, 'id' | 'domain'>): { path: string; query: { id: string } } {
        return { path: UtilBizApplication.DOMAIN_SCREENS[row.domain].path, query: { id: row.id } };
    }

    /** 되돌리기 — 긴급 내림만(정지 내림은 사업자 정지 해제가 되돌린다) */
    static canRestore(status: AdBookingStatus, takedown: AdTakedown | null): boolean {
        return status === 'TAKEN_DOWN' && takedown?.cause === 'EMERGENCY';
    }

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
