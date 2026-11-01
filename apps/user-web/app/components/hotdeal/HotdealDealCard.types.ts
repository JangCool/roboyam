import type { DealEndReason, PostDeal } from '~/composables/hotdeal/api/useHotdealApi.types';

/**
 * 글 보기 딜 카드(`HotdealDealCard`)가 부르는 동작 — 페이지가 `useHotdealApi` 로 만들어 내려 준다(컴포넌트는 서버를 부르지 않는다).
 * 오류는 그대로 던진다(`ApiError`) — 카드가 코드로 갈라 알린다. 성공하면 새 딜을 돌려준다(카드가 `update:deal` 로 올린다).
 */
export interface DealCardActions {
    /** 품절·가격 변경 알리기 */
    endRequest: (reason: DealEndReason) => Promise<PostDeal>;
    /** 작성자 [딜 종료] */
    end: () => Promise<PostDeal>;
    /** 작성자 되돌리기 [다시 진행 중으로](D2 — 작성자 종료 뒤 24시간 안 1번) */
    reopen: () => Promise<PostDeal>;
    /** 작성자 「아직 진행 중이에요」 */
    keep: () => Promise<PostDeal>;
}
