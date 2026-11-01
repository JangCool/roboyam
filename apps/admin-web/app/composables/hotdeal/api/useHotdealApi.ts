import { mockHotdealBoards, mockHotdealDetail, mockHotdealEnd, mockHotdealList, mockHotdealReopen } from '~/composables/hotdeal/api/mock/hotdeal-mock';
import type { AdminDealDetail, AdminDealPage, AdminDealQuery, DealActionRequest, HotdealBoardOptionsView } from '~/composables/hotdeal/api/useHotdealApi.types';

/**
 * 핫딜 글 API(관리자) — 서버 주소(`/api/admin/hotdeals…`)가 있는 **유일한 곳**이다. 상태는 없다.
 * 계약 `docs/features/hotdeal-contract.md` 「API 후보」. **서버에 아직 없다(목업)**.
 * 권한: 조회 `hotdeal.read`, 종료·재개 `hotdeal.manage`(모든 핫딜 게시판) — 게시판 매니저·스텝은 그 게시판 글만(서버 판정). 없으면 `403 PERMISSION_DENIED`.
 *   - `GET  /api/admin/hotdeals/boards` → `HotdealBoardOptionsView`(DEAL 게시판과 말머리 — 관리자 덧붙임)
 *   - `GET  /api/admin/hotdeals?view&board&q&createdFrom&createdTo&page&size` → `AdminDealPage`(최신 등록순, `counts` 는 상태 조건만 뺀 건수)
 *   - `GET  /api/admin/hotdeals/{postId}` → `AdminDealDetail` · `404 POST_NOT_FOUND`
 *   - `POST /api/admin/hotdeals/{postId}/end` `{ reason? }`(0~200자) → 204 · `409 HOTDEAL_STATE_CHANGED`(이미 종료) · `400 fields.reason`
 *   - `POST /api/admin/hotdeals/{postId}/reopen` `{ reason? }` → 204(의심·종료 요청 리셋, 마감이 지났으면 작성자에게 「마감을 고쳐 주세요」 알림) · `409 HOTDEAL_STATE_CHANGED`
 * 감사 `HOTDEAL_END`·`HOTDEAL_REOPEN`. 응답 `Cache-Control: no-store`.
 *
 * ⚠ MOCK: 모든 메서드가 `./mock/hotdeal-mock.ts` 를 부른다. 서버가 붙으면 `// MOCK:` 줄과 목업 import 를 지우고 `// REAL:` 줄을 살린다.
 */
const PATH = {
    root: '/api/admin/hotdeals',
    boards: () => `${PATH.root}/boards`,
    one: (postId: string) => `${PATH.root}/${encodeURIComponent(postId)}`,
    end: (postId: string) => `${PATH.one(postId)}/end`,
    reopen: (postId: string) => `${PATH.one(postId)}/reopen`,
};

export function useHotdealApi() {
    // REAL: const auth = useAdminAuthApi();
    // REAL: const client = useApiClient({ refreshCsrf: () => auth.me() });
    void PATH; // MOCK: 모든 메서드가 아직 목업이라 주소를 쓰는 곳이 없다 — 서버가 붙으면 이 줄을 지운다

    return {
        /** 핫딜 게시판(DEAL)과 말머리 */
        boards: (): Promise<HotdealBoardOptionsView> =>
            // MOCK:
            mockHotdealBoards(),
        // REAL: client.get<HotdealBoardOptionsView>(PATH.boards()),

        list: (query: AdminDealQuery, signal?: AbortSignal): Promise<AdminDealPage> =>
            // MOCK:
            (void signal, mockHotdealList(query)),
        // REAL: client.get<AdminDealPage>(PATH.root, { query: { ...query }, signal }),

        detail: (postId: string): Promise<AdminDealDetail> =>
            // MOCK:
            mockHotdealDetail(postId),
        // REAL: client.get<AdminDealDetail>(PATH.one(postId)),

        /** 운영진 종료(204) — 사유는 선택 */
        end: (postId: string, body: DealActionRequest): Promise<void> =>
            // MOCK:
            mockHotdealEnd(postId, body.reason),
        // REAL: client.post<void>(PATH.end(postId), body),

        /** 재개(204) — 의심·종료 요청을 리셋한다 */
        reopen: (postId: string, body: DealActionRequest): Promise<void> =>
            // MOCK:
            mockHotdealReopen(postId, body.reason),
        // REAL: client.post<void>(PATH.reopen(postId), body),
    };
}
