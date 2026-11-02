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
    /** 게시판 매니저·스텝 [핫딜 종료] — 사유 필수(1~200자) */
    moderateEnd: (reason: string) => Promise<PostDeal>;
    /** 게시판 매니저·스텝 [다시 진행 중으로] — 사유 선택(없으면 null) */
    moderateReopen: (reason: string | null) => Promise<PostDeal>;
    /**
     * 사업자 글 작성자 [일시 정지] 확인 창에 쓸 신청 건 정보(끝 날짜·쓴 횟수) — 지금 정지할 수 없는 건이면 null. 페이지가 신청 건 상세로 만든다.
     * 없으면 카드에 [일시 정지]가 없다.
     */
    bizPauseInfo?: () => Promise<DealPauseInfo | null>;
    /** 사업자 글 작성자 [일시 정지] — 그 글의 신청 건을 정지한다(오류는 그대로 던진다) */
    bizPause?: () => Promise<void>;
}

/** 딜 카드 일시 정지 확인 창에 쓰는 신청 건 값 */
export interface DealPauseInfo {
    /** 신청 끝 날짜(포함) — 이날까지 다시 켤 수 있다 */
    endDate: string;
    used: number;
    limit: number;
}
