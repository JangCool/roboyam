import {
    mockBusinessApprove,
    mockBusinessCertificate,
    mockBusinessDetail,
    mockBusinessList,
    mockBusinessRecheckNts,
    mockBusinessReject,
    mockBusinessReveal,
    mockBusinessSuspend,
} from '~/composables/business/api/mock/business-mock';
import type {
    BusinessApproveRequest,
    BusinessDetail,
    BusinessPage,
    BusinessQuery,
    BusinessRejectRequest,
    BusinessReveal,
    BusinessSuspendRequest,
} from '~/composables/business/api/useBusinessApi.types';

/**
 * 사업자 심사 API(관리자) — 서버 주소(`/api/admin/businesses/…`)가 있는 **유일한 곳**이다. 상태는 없다.
 * 계약 후보(화면 확정 뒤 `docs/features/business-contract.md`). 권한: 조회 `business.read`, 처리 `business.manage`, 평문 열람 `business.reveal`(SENSITIVE, 추가 예정 — B3).
 *  - `GET  /api/admin/businesses?status=&service=&q=&page=&size=` → `BusinessPage`(목록은 가린 값만)
 *  - `GET  /api/admin/businesses/{id}` → `BusinessDetail`
 *  - `POST /api/admin/businesses/{id}/nts-check` → `BusinessDetail`(국세청 상태조회 다시 — 결과 스냅샷 저장, B1)
 *  - `POST /api/admin/businesses/{id}/approve` `{ ntsOverrideReason? }` → 계속사업자만. 휴·폐업 409 `BUSINESS_NTS_NOT_ACTIVE`, 조회 불가 + 사유 없음 400 `fields.ntsOverrideReason`
 *  - `POST /api/admin/businesses/{id}/reject` `{ code, text }`
 *  - `POST /api/admin/businesses/{id}/suspend` `{ code, text, pendingAction, scheduledAction, runningAction }`(H2)
 *  - `POST /api/admin/businesses/{id}/reveal` `{ reason }` → `BusinessReveal`(열람 기록 — 운영자·IP·사유, 10분 뒤 화면이 가린다)
 *  - `GET  /api/admin/businesses/{id}/certificate` → 사업자등록증 사본(열람 기록, B2)
 *  상태가 이미 바뀌었으면 409 `BUSINESS_STATE_CHANGED` — 화면은 "이미 바뀌었어요" + 다시 읽기.
 *
 * ⚠ MOCK: 서버에 API 가 없어 `./mock/business-mock.ts` 를 부른다. 서버가 붙으면 `// MOCK:` 줄과 목업 import 를 지우고 `// REAL:` 줄을 살린다.
 */
const PATH = {
    root: '/api/admin/businesses',
    one: (id: string) => `${PATH.root}/${encodeURIComponent(id)}`,
    action: (id: string, name: string) => `${PATH.one(id)}/${name}`,
};

export function useBusinessApi() {
    // REAL: const auth = useAdminAuthApi();
    // REAL: const client = useApiClient({ refreshCsrf: () => auth.me() });
    void PATH;

    return {
        list: (query: BusinessQuery): Promise<BusinessPage> =>
            // MOCK:
            mockBusinessList(query),
        // REAL: client.get<BusinessPage>(PATH.root, { query: { ...query } }),

        detail: (id: string): Promise<BusinessDetail> =>
            // MOCK:
            mockBusinessDetail(id),
        // REAL: client.get<BusinessDetail>(PATH.one(id)),

        recheckNts: (id: string): Promise<BusinessDetail> =>
            // MOCK:
            mockBusinessRecheckNts(id),
        // REAL: client.post<BusinessDetail>(PATH.action(id, 'nts-check')),

        approve: (id: string, body: BusinessApproveRequest): Promise<BusinessDetail> =>
            // MOCK:
            mockBusinessApprove(id, body),
        // REAL: client.post<BusinessDetail>(PATH.action(id, 'approve'), body),

        reject: (id: string, body: BusinessRejectRequest): Promise<BusinessDetail> =>
            // MOCK:
            mockBusinessReject(id, body),
        // REAL: client.post<BusinessDetail>(PATH.action(id, 'reject'), body),

        suspend: (id: string, body: BusinessSuspendRequest): Promise<BusinessDetail> =>
            // MOCK:
            mockBusinessSuspend(id, body),
        // REAL: client.post<BusinessDetail>(PATH.action(id, 'suspend'), body),

        reveal: (id: string, reason: string): Promise<BusinessReveal> =>
            // MOCK:
            mockBusinessReveal(id, reason),
        // REAL: client.post<BusinessReveal>(PATH.action(id, 'reveal'), { reason }),

        certificate: (id: string): Promise<{ url: string; mimeType: string }> =>
            // MOCK:
            mockBusinessCertificate(id),
        // REAL: client.get<{ url: string; mimeType: string }>(PATH.action(id, 'certificate')),
    };
}
