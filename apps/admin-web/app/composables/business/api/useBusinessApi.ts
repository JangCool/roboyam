import type {
    BusinessApproveRequest,
    BusinessCertificateFile,
    BusinessDetail,
    BusinessPage,
    BusinessQuery,
    BusinessRejectRequest,
    BusinessReveal,
    BusinessService,
    BusinessSuspendRequest,
    BusinessUnsuspendRequest,
} from '~/composables/business/api/useBusinessApi.types';

/**
 * 사업자 심사 API(관리자) — 서버 주소(`/api/admin/businesses/…`)가 있는 **유일한 곳**이다. 상태는 없다.
 * 계약 `docs/features/business-contract.md`. 권한: 조회 `business.read`, 처리 `business.manage`, 평문 열람 `business.reveal`, 정지 해제 `business.unsuspend`(둘 다 SENSITIVE — B3).
 *  - `GET  /api/admin/businesses?status=&service=&q=&page=&size=` → `BusinessPage`(목록은 가린 값만)
 *  - `GET  /api/admin/businesses/{id}` → `BusinessDetail`
 *  - `POST /api/admin/businesses/{id}/nts-check` → `BusinessDetail`(국세청 다시 조회 — 개업일이 있으면 진위확인, 없으면 상태조회만 + `UNCHECKED`, 결과 스냅샷 저장)
 *  - `POST /api/admin/businesses/{id}/approve` `{ ntsOverrideReason? }` → 계속사업자 + 진위 일치만. 휴·폐업·없는 번호 409 `BUSINESS_NTS_NOT_ACTIVE`, 진위 불일치 409 `BUSINESS_NTS_MISMATCH`,
 *    조회 전 409 `BUSINESS_NTS_NOT_CHECKED`, 조회 불가·진위 미확인 + 사유 없음 400 `fields.ntsOverrideReason`,
 *    등록증을 우편·팩스로 보낸다고 했는데 아직 받지 않음 409 `BUSINESS_CERT_NOT_RECEIVED`(상세 `certificateDelivery` — biz-doc-intake-contract)
 *  - `POST /api/admin/businesses/{id}/reject` `{ code, text }`
 *  - `POST /api/admin/businesses/{id}/suspend` `{ code, text, pendingAction, scheduledAction, runningAction }`(H2)
 *  - `POST /api/admin/businesses/{id}/unsuspend` `{ restore, note? }`(`business.unsuspend`)
 *  - `POST /api/admin/businesses/{id}/reveal` `{ reason }` → `BusinessReveal`(열람 기록 — 운영자·IP·사유, 10분 뒤 화면이 가린다)
 *  - `GET  /api/admin/businesses/{id}/certificate` → 사업자등록증 사본 `data:` 주소(열람 기록, B2)
 *  상태가 이미 바뀌었으면 409 `BUSINESS_STATE_CHANGED` — 화면은 "이미 바뀌었어요" + 다시 읽기.
 *
 * 서비스만 정지·해제(2026-11-02 — 신원은 그대로, 그 서비스 신청 건에만 적용):
 *  - `POST /api/admin/businesses/{id}/services/{service}/suspend` `{ code, text, pendingAction, scheduledAction, runningAction }`(`business.manage`) → `BusinessDetail`
 *  - `POST /api/admin/businesses/{id}/services/{service}/resume` `{ restore, note? }`(`business.unsuspend`) → `BusinessDetail`
 *  - 상세 `serviceCards[]` 의 `suspendCode`(서비스만 정지면 값)·`suspendText`·`suspendedAt`·`suspendedByName`·`impact`(그 서비스 건만), `history[]` 의 `service`
 *  상태가 맞지 않으면 409 `BUSINESS_STATE_CHANGED`, 칸 오류 400 `fields.*`. `service` 는 `HOTDEAL`·`AD`.
 */
const PATH = {
    root: '/api/admin/businesses',
    one: (id: string) => `${PATH.root}/${encodeURIComponent(id)}`,
    action: (id: string, name: string) => `${PATH.one(id)}/${name}`,
    service: (id: string, service: BusinessService, name: 'suspend' | 'resume') => `${PATH.one(id)}/services/${encodeURIComponent(service)}/${name}`,
};

export function useBusinessApi() {
    const auth = useAdminAuthApi();
    const client = useApiClient({ refreshCsrf: () => auth.me() });

    return {
        list: (query: BusinessQuery): Promise<BusinessPage> => client.get<BusinessPage>(PATH.root, { query: { ...query } }),

        detail: (id: string): Promise<BusinessDetail> => client.get<BusinessDetail>(PATH.one(id)),

        /** 서비스만 정지(`business.manage`) — 신원은 그대로, 건 처리 선택은 그 서비스 신청 건에만 */
        suspendService: (id: string, service: BusinessService, body: BusinessSuspendRequest): Promise<BusinessDetail> =>
            client.post<BusinessDetail>(PATH.service(id, service, 'suspend'), body),

        /** 서비스만 정지 해제(`business.unsuspend`) — 서비스 정지로 내려간 그 서비스 건의 되돌리기 선택 */
        resumeService: (id: string, service: BusinessService, body: BusinessUnsuspendRequest): Promise<BusinessDetail> =>
            client.post<BusinessDetail>(PATH.service(id, service, 'resume'), body),

        recheckNts: (id: string): Promise<BusinessDetail> => client.post<BusinessDetail>(PATH.action(id, 'nts-check')),

        approve: (id: string, body: BusinessApproveRequest): Promise<BusinessDetail> => client.post<BusinessDetail>(PATH.action(id, 'approve'), body),

        reject: (id: string, body: BusinessRejectRequest): Promise<BusinessDetail> => client.post<BusinessDetail>(PATH.action(id, 'reject'), body),

        suspend: (id: string, body: BusinessSuspendRequest): Promise<BusinessDetail> => client.post<BusinessDetail>(PATH.action(id, 'suspend'), body),

        /** 정지 해제(`business.unsuspend`) — 화면 단추는 아직 없다(계약 「미확정」) */
        unsuspend: (id: string, body: BusinessUnsuspendRequest): Promise<BusinessDetail> => client.post<BusinessDetail>(PATH.action(id, 'unsuspend'), body),

        reveal: (id: string, reason: string): Promise<BusinessReveal> => client.post<BusinessReveal>(PATH.action(id, 'reveal'), { reason }),

        certificate: (id: string): Promise<BusinessCertificateFile> => client.get<BusinessCertificateFile>(PATH.action(id, 'certificate')),

        /**
         * 사업자 삭제(`business.delete`) — `DELETE /api/admin/businesses/{id}` `{ reason }`(5~200자) → 204. 광고·핫딜 신청 건이 한 줄이라도 있으면(상태 무관) 409 `BUSINESS_HAS_DEALINGS`,
         * 사유 400 `fields.reason`, 없으면 404 `BUSINESS_NOT_FOUND`. 신원·서비스 권한·국세청 기록·등록증 사본·처리 기록·열람 기록을 지우고 회원 계정은 남긴다(감사 `BUSINESS_DELETE`).
         */
        remove: (id: string, reason: string): Promise<void> => client.del<void>(PATH.one(id), undefined, { reason }),
    };
}
