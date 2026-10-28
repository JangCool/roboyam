import type { GroupBuySkuKind } from '~/composables/groupbuy/api/useGroupBuyApi.types';

/** 「선택한 상품」 한 줄(결정 88) — 공구 상세(수량 조절·빼기)와 주문서(확인만)가 같이 쓴다. 값은 부르는 쪽이 서버 값으로 채운다 */
export interface GroupBuyPickedLine {
    skuId: number;
    /** 표시 이름(서버 단품 이름 — 「블랙 / M」, 축이 없으면 상품 이름) */
    name: string;
    kind: GroupBuySkuKind;
    /** 단품 값(서버) */
    unitPrice: number;
    qty: number;
    /** 담을 수 있는 수량 상한(편의 — `UtilGroupBuy.lineCap`, 최종은 서버). 고칠 수 없는 목록이면 쓰지 않는다 */
    max: number;
    /** 서버 견적의 줄 금액 — 아직 없으면 null(「—」) */
    amount: number | null;
    /** 「빼기」 단추를 둘지 — 축 없는 본 상품 줄은 빼지 않는다 */
    removable: boolean;
}
