import type {
    BusinessApproveRequest,
    BusinessCertificateFile,
    BusinessDetail,
    BusinessPage,
    BusinessQuery,
    BusinessRejectRequest,
    BusinessReveal,
    BusinessSuspendRequest,
    BusinessUnsuspendRequest,
} from '~/composables/business/api/useBusinessApi.types';

/**
 * 사업자 심사 API(관리자) — 서버 주소(`/api/admin/businesses/…`)가 있는 **유일한 곳**이다. 상태는 없다.
 * 계약 `docs/features/business-contract.md`. 권한: 조회 `business.read`, 처리 `business.manage`, 평문 열람 `business.reveal`, 정지 해제 `business.unsuspend`(둘 다 SENSITIVE — B3).
 *  - `GET  /api/admin/businesses?status=&service=&q=&page=&size=` → `BusinessPage`(목록은 가린 값만)
 *  - `GET  /api/admin/businesses/{id}` → `BusinessDetail`
 *  - `POST /api/admin/businesses/{id}/nts-check` → `BusinessDetail`(국세청 상태조회 다시 — 결과 스냅샷 저장, B1)
 *  - `POST /api/admin/businesses/{id}/approve` `{ ntsOverrideReason? }` → 계속사업자만. 휴·폐업·없는 번호 409 `BUSINESS_NTS_NOT_ACTIVE`, 조회 전 409 `BUSINESS_NTS_NOT_CHECKED`,
 *    조회 불가 + 사유 없음 400 `fields.ntsOverrideReason`
 *  - `POST /api/admin/businesses/{id}/reject` `{ code, text }`
 *  - `POST /api/admin/businesses/{id}/suspend` `{ code, text, pendingAction, scheduledAction, runningAction }`(H2)
 *  - `POST /api/admin/businesses/{id}/unsuspend` `{ restore, note? }`(`business.unsuspend`)
 *  - `POST /api/admin/businesses/{id}/reveal` `{ reason }` → `BusinessReveal`(열람 기록 — 운영자·IP·사유, 10분 뒤 화면이 가린다)
 *  - `GET  /api/admin/businesses/{id}/certificate` → 사업자등록증 사본 `data:` 주소(열람 기록, B2)
 *  상태가 이미 바뀌었으면 409 `BUSINESS_STATE_CHANGED` — 화면은 "이미 바뀌었어요" + 다시 읽기.
 */
const PATH = {
    root: '/api/admin/businesses',
    one: (id: string) => `${PATH.root}/${encodeURIComponent(id)}`,
    action: (id: string, name: string) => `${PATH.one(id)}/${name}`,
};

export function useBusinessApi() {
    const auth = useAdminAuthApi();
    const client = useApiClient({ refreshCsrf: () => auth.me() });

    return {
        list: (query: BusinessQuery): Promise<BusinessPage> => client.get<BusinessPage>(PATH.root, { query: { ...query } }),

        detail: (id: string): Promise<BusinessDetail> => client.get<BusinessDetail>(PATH.one(id)),

        recheckNts: (id: string): Promise<BusinessDetail> => client.post<BusinessDetail>(PATH.action(id, 'nts-check')),

        approve: (id: string, body: BusinessApproveRequest): Promise<BusinessDetail> => client.post<BusinessDetail>(PATH.action(id, 'approve'), body),

        reject: (id: string, body: BusinessRejectRequest): Promise<BusinessDetail> => client.post<BusinessDetail>(PATH.action(id, 'reject'), body),

        suspend: (id: string, body: BusinessSuspendRequest): Promise<BusinessDetail> => client.post<BusinessDetail>(PATH.action(id, 'suspend'), body),

        /** 정지 해제(`business.unsuspend`) — 화면 단추는 아직 없다(계약 「미확정」) */
        unsuspend: (id: string, body: BusinessUnsuspendRequest): Promise<BusinessDetail> => client.post<BusinessDetail>(PATH.action(id, 'unsuspend'), body),

        reveal: (id: string, reason: string): Promise<BusinessReveal> => client.post<BusinessReveal>(PATH.action(id, 'reveal'), { reason }),

        certificate: (id: string): Promise<BusinessCertificateFile> => client.get<BusinessCertificateFile>(PATH.action(id, 'certificate')),
    };
}
