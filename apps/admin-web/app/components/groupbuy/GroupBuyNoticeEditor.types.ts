import type { NoticeCustomItem, NoticeItemValue } from '~/composables/groupbuy/api/useGroupBuyAdminApi.types';

/**
 * 고시 양식 편집 한 장 — 응답 모양(`GroupBuyNotice`)에서 적용 단품만 **단품 줄 키**(`GroupBuySkuRef.key`)로 든다(이름을 고쳐도 따라가게).
 * 저장할 때 부르는 쪽이 키를 지금 단품 이름(`skuNames`)으로, 추가 항목을 `values[]` 의 `custom` 줄로 바꾼다(`GroupBuyNoticeInput`).
 */
export interface GroupBuyNoticeRow {
    /** 화면 안 줄 키 */
    key: string;
    categoryCode: string;
    title: string;
    /** 적용 단품의 줄 키. 비면 기본 양식(MAIN 단품들) */
    skuKeys: string[];
    values: NoticeItemValue[];
    /** 판매자 추가 항목(배열 차례 = `sortOrder`) */
    customItems: NoticeCustomItem[];
}

/** 양식 한 장의 적용 단품 고르기 항목 */
export interface GroupBuyNoticeOptionChoice {
    value: string;
    label: string;
    disabled: boolean;
    /** 고를 수 없는 까닭(다른 양식 제목 · 이름 없음) */
    note: string;
}

/** 날짜 항목 입력 도우미(저장하지 않음 — 자리표시·예시 문구만 바꾼다, 원칙 4) */
export type NoticeDateHelper = 'EXACT' | 'EARLIEST' | 'RANGE' | 'ORDER_BASED';
/** KC 항목 입력 도우미(저장하지 않음 — 원칙 6) */
export type NoticeKcHelper = 'CERT' | 'EXEMPT';
