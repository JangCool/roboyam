import type { GroupBuyNotice } from '~/composables/groupbuy/api/useGroupBuyAdminApi.types';

/** 고시 양식 편집 한 장 — 서버 모양에 화면 안 줄 키를 더했다(새 양식도 안정된 키) */
export interface GroupBuyNoticeRow extends GroupBuyNotice {
    key: string;
}

/** 적용 옵션 고르기에 보일 공구 옵션 — 저장된 옵션(`id` 있음)만 고를 수 있다 */
export interface GroupBuyNoticeOptionRef {
    key: string;
    id: string | null;
    name: string;
}

/** 양식 한 장의 적용 옵션 고르기 항목 */
export interface GroupBuyNoticeOptionChoice {
    value: string;
    label: string;
    disabled: boolean;
    /** 고를 수 없는 까닭(다른 양식 제목 · 저장 전) */
    note: string;
}
