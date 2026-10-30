import {
    mockBizAppApprove,
    mockBizAppApproveRefund,
    mockBizAppAvailability,
    mockBizAppCancel,
    mockBizAppConfirmBooking,
    mockBizAppDenyRefund,
    mockBizAppDetail,
    mockBizAppExtendDeposit,
    mockBizAppList,
    mockBizAppMockPay,
    mockBizAppReject,
    mockBizAppSaveDeposit,
    mockBizAppSwitchToManual,
    mockBizAppUploadContracts,
} from '~/composables/bizapplication/api/mock/bizapplication-mock';
import type {
    BizApplicationDetail,
    BizApplicationList,
    BizApplicationQuery,
    BizApproveRequest,
    BizAvailability,
    BizDepositRequest,
    BizPeriod,
    BizRejectCode,
} from '~/composables/bizapplication/api/useBizApplicationApi.types';

/**
 * 사업자 신청 건(광고·핫딜) 심사 API(관리자) — 서버 주소(`/api/admin/biz-applications/…`)가 있는 **유일한 곳**이다. 상태는 없다.
 * 계약 후보(화면 확정 뒤 `docs/features/biz-application-contract.md`). 권한(가정): 조회 `business.read`, 심사·결제 처리 `ad.manage`(광고)·`hotdeal.manage`(핫딜), 환불 `deal-order.refund`.
 *  - `GET  /api/admin/biz-applications?queue=&kind=&q=` → `BizApplicationList`(큐별 건수 포함)
 *  - `GET  /api/admin/biz-applications/{id}` → `BizApplicationDetail`
 *  - `GET  /api/admin/biz-applications/{id}/availability?startAt=&endAt=` → `BizAvailability`(요금표 계산 + 남은 자리)
 *  - `POST …/{id}/approve` `{ method, supply, adjustReason?, deadlineAt, period }` — 시작이 지난 기간 400 `fields.period`(F6), 조정 사유 없음 400 `fields.adjustReason`, 자리 없음 409 `AD_SLOT_FULL`. 승인 순간부터 자리를 잡는다
 *  - `POST …/{id}/reject` `{ code, text }`(결제 전이라 환불 없음)
 *  - `POST …/{id}/manual` `{ deadlineAt, reason }`(결제 대기 → 수동 결제로 전환)
 *  - `POST …/{id}/contracts` 멀티파트 `files[]`(+ `replaceId`) — 원본 보존, 옛 파일은 「대체됨」
 *  - `PUT  …/{id}/deposit` `{ amount, depositedOn, depositorName, mismatchReason? }`(감사 `BIZ_DEPOSIT_CONFIRM`)
 *  - `POST …/{id}/confirm` — 계약서 1장 이상 + 입금 확인 둘 다(서버 CHECK, 아니면 409 `BIZ_DEPOSIT_INCOMPLETE`)
 *  - `POST …/{id}/deposit-deadline` `{ deadlineAt }` · `POST …/{id}/cancel` `{ reason }`
 *  - `POST …/{id}/refund/approve` `{ amount, reason }` · `POST …/{id}/refund/deny` `{ reason }`
 *  상태가 이미 바뀌었으면 409 `BIZ_APPLICATION_STATE_CHANGED`.
 *
 * ⚠ MOCK: 서버에 API 가 없어 `./mock/bizapplication-mock.ts` 를 부른다. 서버가 붙으면 `// MOCK:` 줄과 목업 import 를 지우고 `// REAL:` 줄을 살린다.
 *   `mockPay` 는 목업 전용(사업자 쪽 온라인 결제 완료를 흉내) — 서버가 붙으면 메서드째 지운다.
 */
const PATH = {
    root: '/api/admin/biz-applications',
    one: (id: string) => `${PATH.root}/${encodeURIComponent(id)}`,
    action: (id: string, name: string) => `${PATH.one(id)}/${name}`,
};

export function useBizApplicationApi() {
    // REAL: const auth = useAdminAuthApi();
    // REAL: const client = useApiClient({ refreshCsrf: () => auth.me() });
    void PATH;

    return {
        list: (query: BizApplicationQuery): Promise<BizApplicationList> =>
            // MOCK:
            mockBizAppList(query),
        // REAL: client.get<BizApplicationList>(PATH.root, { query: { ...query } }),

        detail: (id: string): Promise<BizApplicationDetail> =>
            // MOCK:
            mockBizAppDetail(id),
        // REAL: client.get<BizApplicationDetail>(PATH.one(id)),

        availability: (id: string, period: BizPeriod): Promise<BizAvailability> =>
            // MOCK:
            mockBizAppAvailability(id, period),
        // REAL: client.get<BizAvailability>(PATH.action(id, 'availability'), { query: { ...period } }),

        approve: (id: string, body: BizApproveRequest): Promise<BizApplicationDetail> =>
            // MOCK:
            mockBizAppApprove(id, body),
        // REAL: client.post<BizApplicationDetail>(PATH.action(id, 'approve'), body),

        reject: (id: string, body: { code: BizRejectCode; text: string }): Promise<BizApplicationDetail> =>
            // MOCK:
            mockBizAppReject(id, body),
        // REAL: client.post<BizApplicationDetail>(PATH.action(id, 'reject'), body),

        switchToManual: (id: string, body: { deadlineAt: string; reason: string }): Promise<BizApplicationDetail> =>
            // MOCK:
            mockBizAppSwitchToManual(id, body),
        // REAL: client.post<BizApplicationDetail>(PATH.action(id, 'manual'), body),

        /** 목업 전용 — 사업자의 온라인 결제 완료를 흉내 */
        mockPay: (id: string): Promise<BizApplicationDetail> => mockBizAppMockPay(id),

        uploadContracts: (id: string, files: File[], replaceId?: string): Promise<BizApplicationDetail> => {
            const form = new FormData();
            for (const f of files) form.append('files', f);
            if (replaceId) form.append('replaceId', replaceId);
            // MOCK:
            return mockBizAppUploadContracts(id, files, replaceId);
            // REAL: return client.post<BizApplicationDetail>(PATH.action(id, 'contracts'), form);
        },

        saveDeposit: (id: string, body: BizDepositRequest): Promise<BizApplicationDetail> =>
            // MOCK:
            mockBizAppSaveDeposit(id, body),
        // REAL: client.put<BizApplicationDetail>(PATH.action(id, 'deposit'), body),

        confirmBooking: (id: string): Promise<BizApplicationDetail> =>
            // MOCK:
            mockBizAppConfirmBooking(id),
        // REAL: client.post<BizApplicationDetail>(PATH.action(id, 'confirm')),

        extendDeposit: (id: string, deadlineAt: string): Promise<BizApplicationDetail> =>
            // MOCK:
            mockBizAppExtendDeposit(id, deadlineAt),
        // REAL: client.post<BizApplicationDetail>(PATH.action(id, 'deposit-deadline'), { deadlineAt }),

        cancel: (id: string, reason: string): Promise<BizApplicationDetail> =>
            // MOCK:
            mockBizAppCancel(id, reason),
        // REAL: client.post<BizApplicationDetail>(PATH.action(id, 'cancel'), { reason }),

        approveRefund: (id: string, body: { amount: number; reason: string }): Promise<BizApplicationDetail> =>
            // MOCK:
            mockBizAppApproveRefund(id, body),
        // REAL: client.post<BizApplicationDetail>(PATH.action(id, 'refund/approve'), body),

        denyRefund: (id: string, reason: string): Promise<BizApplicationDetail> =>
            // MOCK:
            mockBizAppDenyRefund(id, reason),
        // REAL: client.post<BizApplicationDetail>(PATH.action(id, 'refund/deny'), { reason }),
    };
}
