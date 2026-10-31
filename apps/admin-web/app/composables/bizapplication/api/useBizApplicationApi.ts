import type {
    BizApplicationDetail,
    BizApplicationList,
    BizApplicationQuery,
    BizApproveRequest,
    BizAvailability,
    BizContractView,
    BizDepositRequest,
    BizPeriod,
    BizRejectCode,
    BizTransferRequest,
} from '~/composables/bizapplication/api/useBizApplicationApi.types';

/**
 * 사업자 신청 건(광고·핫딜) 심사 API(관리자) — 서버 주소(`/api/admin/biz-applications/…`)가 있는 **유일한 곳**이다. 상태는 없다.
 * 계약 `docs/features/biz-application-contract.md`(04-ia-review). 권한은 건의 도메인으로 가른다: 조회 `ad.read`·`hotdeal.read`, 심사·결제 처리 `ad.manage`·`hotdeal.manage`,
 * 환불 승인·거절·송금 `deal-order.refund`. 건의 도메인 권한이 없으면 403. 처리 응답은 모두 처리 뒤 상세.
 *  - `GET  /api/admin/biz-applications?domain=AD|HOTDEAL&queue=&kind=&q=` → `BizApplicationList`(큐별 건수도 그 도메인 안에서). `domain` 필수(400 `fields.domain`), 다른 도메인 kind 400 `fields.kind`
 *  - `GET  /api/admin/biz-applications/{id}` → `BizApplicationDetail`
 *  - `GET  /api/admin/biz-applications/{id}/availability?startAt=&endAt=` → `BizAvailability`(요금표 계산 + 남은 자리, 자기 제외)
 *  - `POST …/{id}/approve` `{ method, supply, adjustReason?, deadlineAt, period }` — 시작이 지난 기간 400 `fields.period`(F6), 조정 사유 없음 400 `fields.adjustReason`,
 *    온라인 기한이 시작 뒤 400 `fields.deadlineAt`, 자리 없음 409 `BIZ_SLOT_FULL`. 승인 순간부터 자리를 잡는다
 *  - `POST …/{id}/reject` `{ code, text }`(결제 전이라 환불 없음)
 *  - `POST …/{id}/manual` `{ deadlineAt, reason }`(결제 대기 → 수동 결제로 전환)
 *  - `POST …/{id}/contracts` 멀티파트 `files`(+ `replaceId`) — 원본 보존, 옛 파일은 「대체됨」. 서버 요청 한도(11MB) 때문에 **한 장씩** 올린다(화면이 차례로 부른다)
 *  - `GET  …/{id}/contracts/{fileId}` → `BizContractView`(`data:` 주소 — 열람 기록·감사)
 *  - `PUT  …/{id}/deposit` `{ amount, depositedOn, depositorName, mismatchReason? }`(감사 `BIZ_DEPOSIT_CONFIRM`)
 *  - `POST …/{id}/confirm` — 계약서 1장 이상 + 입금 확인 둘 다(서버 CHECK, 아니면 409 `BIZ_DEPOSIT_INCOMPLETE`)
 *  - `POST …/{id}/deposit-deadline` `{ deadlineAt }` · `POST …/{id}/cancel` `{ reason }`
 *  - `POST …/{id}/refund/approve` `{ amount, reason }` · `POST …/{id}/refund/deny` `{ reason }` · `POST …/{id}/refund/transfer` `{ note? }`(수동 환불 송금 완료 표시)
 *  상태가 이미 바뀌었으면 409 `BIZ_APPLICATION_STATE_CHANGED`.
 */
const PATH = {
    root: '/api/admin/biz-applications',
    one: (id: string) => `${PATH.root}/${encodeURIComponent(id)}`,
    action: (id: string, name: string) => `${PATH.one(id)}/${name}`,
    contract: (id: string, fileId: string) => `${PATH.one(id)}/contracts/${encodeURIComponent(fileId)}`,
};

export function useBizApplicationApi() {
    const auth = useAdminAuthApi();
    const client = useApiClient({ refreshCsrf: () => auth.me() });

    return {
        list: (query: BizApplicationQuery): Promise<BizApplicationList> => client.get<BizApplicationList>(PATH.root, { query: { ...query } }),

        detail: (id: string): Promise<BizApplicationDetail> => client.get<BizApplicationDetail>(PATH.one(id)),

        availability: (id: string, period: BizPeriod): Promise<BizAvailability> => client.get<BizAvailability>(PATH.action(id, 'availability'), { query: { ...period } }),

        approve: (id: string, body: BizApproveRequest): Promise<BizApplicationDetail> => client.post<BizApplicationDetail>(PATH.action(id, 'approve'), body),

        reject: (id: string, body: { code: BizRejectCode; text: string }): Promise<BizApplicationDetail> => client.post<BizApplicationDetail>(PATH.action(id, 'reject'), body),

        switchToManual: (id: string, body: { deadlineAt: string; reason: string }): Promise<BizApplicationDetail> =>
            client.post<BizApplicationDetail>(PATH.action(id, 'manual'), body),

        /** 계약서 사본 올리기 — 서버는 여러 장을 받지만 요청 한도(11MB)가 있어 화면은 한 장씩 부른다. `replaceId` 면 정확히 1장 */
        uploadContracts: (id: string, files: File[], replaceId?: string): Promise<BizApplicationDetail> => {
            const form = new FormData();
            for (const f of files) form.append('files', f);
            if (replaceId) form.append('replaceId', replaceId);
            return client.post<BizApplicationDetail>(PATH.action(id, 'contracts'), form);
        },

        /** 계약서 사본 열람(`data:` 주소) — 서버가 열람 기록을 남긴다 */
        contract: (id: string, fileId: string): Promise<BizContractView> => client.get<BizContractView>(PATH.contract(id, fileId)),

        saveDeposit: (id: string, body: BizDepositRequest): Promise<BizApplicationDetail> => client.put<BizApplicationDetail>(PATH.action(id, 'deposit'), body),

        confirmBooking: (id: string): Promise<BizApplicationDetail> => client.post<BizApplicationDetail>(PATH.action(id, 'confirm')),

        extendDeposit: (id: string, deadlineAt: string): Promise<BizApplicationDetail> => client.post<BizApplicationDetail>(PATH.action(id, 'deposit-deadline'), { deadlineAt }),

        cancel: (id: string, reason: string): Promise<BizApplicationDetail> => client.post<BizApplicationDetail>(PATH.action(id, 'cancel'), { reason }),

        approveRefund: (id: string, body: { amount: number; reason: string }): Promise<BizApplicationDetail> =>
            client.post<BizApplicationDetail>(PATH.action(id, 'refund/approve'), body),

        denyRefund: (id: string, reason: string): Promise<BizApplicationDetail> => client.post<BizApplicationDetail>(PATH.action(id, 'refund/deny'), { reason }),

        /** 수동 환불 송금 완료 표시 — 가장 최근 환불이 송금 대기일 때만(아니면 409) */
        markTransferred: (id: string, body: BizTransferRequest = {}): Promise<BizApplicationDetail> => client.post<BizApplicationDetail>(PATH.action(id, 'refund/transfer'), body),
    };
}
