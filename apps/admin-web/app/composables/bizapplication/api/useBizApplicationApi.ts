import type {
    BizApplicationDetail,
    BizApplicationList,
    BizApplicationQuery,
    BizApproveRequest,
    BizAvailability,
    BizContractView,
    BizDepositRequest,
    BizPauseActionRequest,
    BizPeriod,
    BizRefundClaimRequest,
    BizRejectCode,
    BizTransferRequest,
} from '~/composables/bizapplication/api/useBizApplicationApi.types';
import type { DocFileChannel } from '~/composables/docintake/api/useDocIntakeApi.types';

/**
 * 사업자 신청 건(광고·핫딜) 심사 API(관리자) — 서버 주소(`/api/admin/biz-applications/…`)가 있는 **유일한 곳**이다. 상태는 없다.
 * 계약 `docs/features/biz-application-contract.md`(04-ia-review). 권한은 건의 도메인으로 가른다: 조회 `ad.read`·`hotdeal.read`, 심사·결제 처리 `ad.manage`·`hotdeal.manage`,
 * 환불 승인·거절·송금 `deal-order.refund`. 건의 도메인 권한이 없으면 403. 처리 응답은 모두 처리 뒤 상세.
 *  - `GET  /api/admin/biz-applications?domain=AD|HOTDEAL&queue=&kind=&q=` → `BizApplicationList`(큐별 건수도 그 도메인 안에서). `domain` 필수(400 `fields.domain`), 다른 도메인 kind 400 `fields.kind`
 *  - `GET  /api/admin/biz-applications/{id}` → `BizApplicationDetail`
 *  - `GET  /api/admin/biz-applications/{id}/availability?startAt=&endAt=` → `BizAvailability`(요금표 계산 + 남은 자리, 자기 제외)
 *  - `POST …/{id}/approve` `{ method, supply, adjustReason?, deadlineAt, period, memo? }` — 시작이 지난 기간 400 `fields.period`(F6), 조정 사유 없음 400 `fields.adjustReason`,
 *    온라인 기한이 시작 뒤 400 `fields.deadlineAt`, 자리 없음 409 `BIZ_SLOT_FULL`. 승인 순간부터 자리를 잡는다.
 *    운영자 등록 건(source=OPERATOR)은 수동 결제만(400 `fields.method`). 자기 승인은 `memo` 1~200자 필수(400 `fields.memo`), 막는 설정이면 409 `BIZ_SELF_APPROVAL_DENIED`
 *  - `POST …/{id}/reject` `{ code, text }`(결제 전이라 환불 없음)
 *  - `POST …/{id}/manual` `{ deadlineAt, reason }`(결제 대기 → 수동 결제로 전환)
 *  - `POST …/{id}/contracts` 멀티파트 `files`(+ `replaceId`, + `channel` 들어온 길 `UPLOAD` 기본 · `FAX` 전자 팩스 — biz-doc-intake-contract) — 원본 보존, 옛 파일은 「대체됨」.
 *    서버 요청 한도(11MB) 때문에 **한 장씩** 올린다(화면이 차례로 부른다)
 *  - `GET  …/{id}/contracts/{fileId}` → `BizContractView`(`data:` 주소 — 열람 기록·감사)
 *  - `PUT  …/{id}/deposit` `{ amount, depositedOn, depositorName, mismatchReason? }`(감사 `BIZ_DEPOSIT_CONFIRM`)
 *  - `POST …/{id}/confirm` — 계약서 1장 이상 + 입금 확인 둘 다(서버 CHECK, 아니면 409 `BIZ_DEPOSIT_INCOMPLETE`)
 *  - `POST …/{id}/deposit-deadline` `{ deadlineAt }` · `POST …/{id}/cancel` `{ reason }`(운영자 등록 건은 심사 대기에서도 취소 — 등록 실수. 계약서 올리기도 운영자 등록 건은 심사 대기부터)
 *  - `POST …/{id}/refund/approve` `{ amount, reason }` · `POST …/{id}/refund/deny` `{ reason }` · `POST …/{id}/refund/transfer` `{ note? }`(수동 환불 송금 완료 표시)
 *  상태가 이미 바뀌었으면 409 `BIZ_APPLICATION_STATE_CHANGED`.
 *  사업자 핫딜 게시(2차 — `biz-hotdeal-contract.md`): 상세 `post`(`BizDealPost` — `HOTDEAL_POST` 만, 결제 전이면 null)·소재 `board`·`prefix`·`imageUrl`.
 *    게시 실패(`POST_FAILED`) 건은 시작 뒤여도 `cancel` = 전액 환불(`FULL_AUTO`).
 *  광고주 요청 일시 정지·중단(서버 연결 2026-11-02 — `biz-pause-contract.md`): 운영자가 광고주 대신 처리한다(요청 경로 + 메모 1~200자 필수, 400 `fields.channel`·`fields.memo`).
 *  - `POST …/{id}/pause` `{ channel, memo }` — 진행 중만(내림 까닭 `ADVERTISER_PAUSE`), 정지 횟수 초과 409 `BIZ_PAUSE_LIMIT`
 *  - `POST …/{id}/resume` `{ channel, memo }` — 정지 중이고 끝 전만(→ SCHEDULED, 끝 시각 그대로 — 연장 없음)
 *  - `POST …/{id}/stop` `{ channel, memo, reason? }` — 진행 중·정지 중(→ 내림 까닭 `ADVERTISER_STOP` + 일할 − 중도 해지 수수료 환불 `STOP_PRORATED` 심사 대기, 예상액 0원이면 환불 줄 없음).
 *    되돌리기 없음. `reason` 은 선택(사업자 화면에 보이는 사유 — 300자 초과 400 `fields.reason`), 메모는 사업자에게 보이지 않는다(사유로 옮기지 않음)
 *  권한 광고 `ad.manage` · 핫딜 `hotdeal.manage`, 감사 `BIZ_APP_PAUSE`·`BIZ_APP_RESUME`·`BIZ_APP_STOP`. 상세 `pause`(`BizPause`)·`takedown.channel`·`takedown.memo`, 목록 줄 `takedownCause`.
 *  추가 환불(클레임 — V128, `biz-pause-contract.md` 「추가 환불」): `POST …/{id}/refund/claim` `{ amount, reason }` — 권한 `deal-order.refund`. 취소된 건 · 끝난 건 · 광고주 중단(환불 거절 뒤) 건의
 *    남은 금액을 바로 승인(`CLAIM` — 온라인 PG 부분 취소 · 수동 송금 대기). 금액 1 ~ 남은 환불 가능액 400 `fields.amount`, 사유 5~200자 400 `fields.reason`.
 *    시작 전·진행 중·정지 중·긴급 내림 중 · 열린 환불 · 송금 대기 줄 · 남은 가능액 0 은 409 `BIZ_APPLICATION_STATE_CHANGED`. 상세 `refundSummary`(결제 전 null), 감사 `BIZ_APP_REFUND_CLAIM`.
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

        /** 계약서 사본 올리기 — 서버는 여러 장을 받지만 요청 한도(11MB)가 있어 화면은 한 장씩 부른다. `replaceId` 면 정확히 1장. 전자 팩스면 `channel = 'FAX'` */
        uploadContracts: (id: string, files: File[], replaceId?: string, channel?: DocFileChannel): Promise<BizApplicationDetail> => {
            const form = new FormData();
            for (const f of files) form.append('files', f);
            if (replaceId) form.append('replaceId', replaceId);
            if (channel) form.append('channel', channel);
            return client.post<BizApplicationDetail>(PATH.action(id, 'contracts'), form);
        },

        /** 계약서 사본 열람(`data:` 주소) — 서버가 열람 기록을 남긴다 */
        contract: (id: string, fileId: string): Promise<BizContractView> => client.get<BizContractView>(PATH.contract(id, fileId)),

        saveDeposit: (id: string, body: BizDepositRequest): Promise<BizApplicationDetail> => client.put<BizApplicationDetail>(PATH.action(id, 'deposit'), body),

        confirmBooking: (id: string): Promise<BizApplicationDetail> => client.post<BizApplicationDetail>(PATH.action(id, 'confirm')),

        extendDeposit: (id: string, deadlineAt: string): Promise<BizApplicationDetail> => client.post<BizApplicationDetail>(PATH.action(id, 'deposit-deadline'), { deadlineAt }),

        cancel: (id: string, reason: string): Promise<BizApplicationDetail> => client.post<BizApplicationDetail>(PATH.action(id, 'cancel'), { reason }),

        approveRefund: (id: string, body: { amount: number; reason: string }): Promise<BizApplicationDetail> => client.post<BizApplicationDetail>(PATH.action(id, 'refund/approve'), body),

        denyRefund: (id: string, reason: string): Promise<BizApplicationDetail> => client.post<BizApplicationDetail>(PATH.action(id, 'refund/deny'), { reason }),

        /** 추가 환불(클레임) — 남은 환불 가능액 안에서 바로 승인(되돌릴 수 없음). 대상이 아닌 상태 · 열린 환불 · 송금 대기 줄이 있으면 409 */
        refundClaim: (id: string, body: BizRefundClaimRequest): Promise<BizApplicationDetail> => client.post<BizApplicationDetail>(PATH.action(id, 'refund/claim'), body),

        /** 운영자 대신 일시 정지(광고주 요청 — 진행 중만, 정지 횟수 안에서) */
        pause: (id: string, body: BizPauseActionRequest): Promise<BizApplicationDetail> => client.post<BizApplicationDetail>(PATH.action(id, 'pause'), body),

        /** 운영자 대신 다시 켜기(정지 중 · 끝 전 — 승인 없이 바로, 연장 없음) */
        resume: (id: string, body: BizPauseActionRequest): Promise<BizApplicationDetail> => client.post<BizApplicationDetail>(PATH.action(id, 'resume'), body),

        /** 운영자 대신 중단(진행 중·정지 중 — 바로 내리고 남은 기간 일할 환불 심사, 되돌리기 없음) */
        stop: (id: string, body: BizPauseActionRequest): Promise<BizApplicationDetail> => client.post<BizApplicationDetail>(PATH.action(id, 'stop'), body),

        /** 수동 환불 송금 완료 표시 — 가장 최근 환불이 송금 대기일 때만(아니면 409) */
        markTransferred: (id: string, body: BizTransferRequest = {}): Promise<BizApplicationDetail> => client.post<BizApplicationDetail>(PATH.action(id, 'refund/transfer'), body),
    };
}
