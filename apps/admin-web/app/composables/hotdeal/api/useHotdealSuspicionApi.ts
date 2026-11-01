import { mockSuspicionDetail, mockSuspicionDismiss, mockSuspicionList } from '~/composables/hotdeal/api/mock/hotdeal-mock';
import type { DealSuspicionDetail, DealSuspicionPage, DealSuspicionStatus } from '~/composables/hotdeal/api/useHotdealSuspicionApi.types';

/**
 * 위장 홍보 의심 후보 API(관리자) — 서버 주소(`/api/admin/hotdeal-suspicions…`)가 있는 **유일한 곳**이다. 상태는 없다. **서버에 아직 없다(목업)**.
 * 권한: 조회 `hotdeal.read`, 무시 `hotdeal.manage`. 자동 제재는 없다(제재는 신고·회원 화면).
 *   - `GET  /api/admin/hotdeal-suspicions?status&page&size` → `DealSuspicionPage`
 *   - `GET  /api/admin/hotdeal-suspicions/{id}` → `DealSuspicionDetail`(그 회원의 그 판매처 글 최근 10개) · `404 HOTDEAL_SUSPICION_NOT_FOUND`
 *   - `POST /api/admin/hotdeal-suspicions/{id}/dismiss` `{ note? }`(0~200자) → 204 · `409 HOTDEAL_STATE_CHANGED`(이미 무시됨)
 * 감사 `HOTDEAL_SUSPICION_DISMISS`.
 *
 * ⚠ MOCK: 모든 메서드가 `./mock/hotdeal-mock.ts` 를 부른다.
 */
const PATH = {
    root: '/api/admin/hotdeal-suspicions',
    one: (id: string) => `${PATH.root}/${encodeURIComponent(id)}`,
    dismiss: (id: string) => `${PATH.one(id)}/dismiss`,
};

export function useHotdealSuspicionApi() {
    // REAL: const auth = useAdminAuthApi();
    // REAL: const client = useApiClient({ refreshCsrf: () => auth.me() });
    void PATH; // MOCK: 서버가 붙으면 이 줄을 지운다

    return {
        list: (status: DealSuspicionStatus, page: number, size: number): Promise<DealSuspicionPage> =>
            // MOCK:
            mockSuspicionList(status, page, size),
        // REAL: client.get<DealSuspicionPage>(PATH.root, { query: { status, page, size } }),

        detail: (id: string): Promise<DealSuspicionDetail> =>
            // MOCK:
            mockSuspicionDetail(id),
        // REAL: client.get<DealSuspicionDetail>(PATH.one(id)),

        dismiss: (id: string, note: string): Promise<void> =>
            // MOCK:
            mockSuspicionDismiss(id, note || undefined),
        // REAL: client.post<void>(PATH.dismiss(id), note ? { note } : {}),
    };
}
