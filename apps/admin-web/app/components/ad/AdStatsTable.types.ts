import type { AdStatCounts } from '~/composables/ad/api/useAdApi.types';

/** 성과 작은 표 한 줄 — 이름 칸은 부르는 쪽이 번역해 준다 */
export interface AdStatsTableRow extends AdStatCounts {
    key: string;
    label: string;
    /** 이름 아래 작은 글자(승인일 등) */
    sub?: string;
    /** 이름 옆 꼬리표(「지금 버전」 등) */
    tag?: string;
    /** 건 수(유형별·광고주 순위) */
    bookings?: number;
}
