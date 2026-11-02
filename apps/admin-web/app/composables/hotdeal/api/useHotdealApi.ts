import type { AdminDealDetail, AdminDealPage, AdminDealQuery, DealActionRequest, DealRevisionListView, HotdealBoardOptionsView } from '~/composables/hotdeal/api/useHotdealApi.types';

/**
 * 핫딜 글 API(관리자) — 서버 주소(`/api/admin/hotdeals…`)가 있는 **유일한 곳**이다. 상태는 없다.
 * 계약 `docs/features/hotdeal-contract.md` 「API 후보」. 모양은 `useHotdealApi.types.ts`(서버 DTO 가 이 칸 이름 그대로).
 * 권한: 조회 `hotdeal.read`, 종료·재개 `hotdeal.manage`(모든 핫딜 게시판) — 게시판 매니저·스텝은 그 게시판 글만(서버 판정). 없으면 `403 PERMISSION_DENIED`.
 *   - `GET  /api/admin/hotdeals/boards` → `HotdealBoardOptionsView`(DEAL 게시판과 말머리 — 관리자 덧붙임)
 *   - `GET  /api/admin/hotdeals?view&board&q&createdFrom&createdTo&blockedActive&source&page&size` → `AdminDealPage`(최신 등록순, `counts` 는 상태 조건만 뺀 건수).
 *     `source=MEMBER|BUSINESS`(없으면 전체) · 줄·상세의 `source`·`business: { name, applicationId } | null`(2차 — 사업자 핫딜 게시)
 *   - `GET  /api/admin/hotdeals/{postId}` → `AdminDealDetail` · `404 POST_NOT_FOUND`
 *   - `GET  /api/admin/hotdeals/{postId}/revisions` → `DealRevisionListView`(딜 칸 수정 이력, 최근 것부터, 6개월 보존 — D3)
 *   - `POST /api/admin/hotdeals/{postId}/end` `{ reason? }`(0~200자) → 204 · `409 HOTDEAL_STATE_CHANGED`(이미 종료) · `400 fields.reason`
 *   - `POST /api/admin/hotdeals/{postId}/reopen` `{ reason? }` → 204(의심·종료 요청 리셋, 마감이 지났으면 작성자에게 「마감을 고쳐 주세요」 알림) · `409 HOTDEAL_STATE_CHANGED`
 * 감사 `HOTDEAL_END`·`HOTDEAL_REOPEN`. 응답 `Cache-Control: no-store`.
 * 판매자 일시 정지·중단(서버 연결 2026-11-02 — `biz-pause-contract.md`): 줄·상세 `row.sellerHidden`(`{ cause: ADVERTISER_PAUSE|ADVERTISER_STOP, at, by, byOperator }`) — 숨긴 글도 관리자 목록·상세에는 그대로 나온다.
 */
const PATH = {
    root: '/api/admin/hotdeals',
    boards: () => `${PATH.root}/boards`,
    one: (postId: string) => `${PATH.root}/${encodeURIComponent(postId)}`,
    revisions: (postId: string) => `${PATH.one(postId)}/revisions`,
    end: (postId: string) => `${PATH.one(postId)}/end`,
    reopen: (postId: string) => `${PATH.one(postId)}/reopen`,
};

export function useHotdealApi() {
    const auth = useAdminAuthApi();
    const client = useApiClient({ refreshCsrf: () => auth.me() });

    return {
        /** 핫딜 게시판(DEAL)과 말머리 */
        boards: (): Promise<HotdealBoardOptionsView> => client.get<HotdealBoardOptionsView>(PATH.boards()),

        /** 목록 — 빈 조건은 보내지 않는다(`view` 는 `ALL` 이어도 늘 보낸다) */
        list: (query: AdminDealQuery, signal?: AbortSignal): Promise<AdminDealPage> => client.get<AdminDealPage>(PATH.root, { query: { ...query, blockedActive: query.blockedActive || undefined }, signal }),

        detail: (postId: string): Promise<AdminDealDetail> => client.get<AdminDealDetail>(PATH.one(postId)),

        /** 딜 칸 수정 이력(서랍 「수정 이력」 탭을 열 때) */
        revisions: (postId: string): Promise<DealRevisionListView> => client.get<DealRevisionListView>(PATH.revisions(postId)),

        /** 운영진 종료(204) — 사유는 선택 */
        end: (postId: string, body: DealActionRequest): Promise<void> => client.post<void>(PATH.end(postId), body),

        /** 재개(204) — 의심·종료 요청을 리셋한다 */
        reopen: (postId: string, body: DealActionRequest): Promise<void> => client.post<void>(PATH.reopen(postId), body),
    };
}
