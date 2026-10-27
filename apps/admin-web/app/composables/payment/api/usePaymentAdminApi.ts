import type {
    PaymentAnomalyPage,
    PaymentAnomalyQuery,
    PaymentCancelView,
    PaymentOrderDetail,
    PaymentOrderQuery,
    PaymentOrderRow,
    PaymentPage,
    PaymentRecheckResult,
    PaymentWebhookQuery,
    PaymentWebhookRow,
    ReconcileDayRow,
    ReconcileIssueQuery,
    ReconcileIssueRow,
    ReconcileOverview,
} from '~/composables/payment/api/usePaymentAdminApi.types';

/**
 * 결제 관리자 API — 서버 주소 문자열(`/api/admin/payment…`)이 있는 **유일한 곳**이다. 상태는 없다.
 * 계약: `docs/features/commerce-payment-contract.md`(2a-1 화면 목업이 정한 모양 그대로 2a-3 서버가 준다). 응답 `Cache-Control: no-store`.
 *
 * 권한: 조회 `commerce.payment.read`, 처리(「다시 시도」·「수동 처리 완료」·「포트원에서 다시 확인」·대사 처리·다시 대사) `commerce.reconcile.manage`. 없으면 `403 PERMISSION_DENIED`.
 * 처리는 모두 사유 5~200자(`400 VALIDATION fields.reason`, 다시 대사는 사유 없음)이고 서버가 감사(돈 관련)를 남긴다. 상세를 열면 `PAYMENT_ORDER_VIEW`(개인정보 열람) 감사.
 *   - `GET  /api/admin/payments?status=&anomaly=&orderType=&from=&to=&q=&page=&size=` → `PaymentPage<PaymentOrderRow>`(만든 시각 내림차순, q = 결제 번호 일부 또는 회원 번호)
 *   - `GET  /api/admin/payments/{id}` → `PaymentOrderDetail`(이력·취소 대기열·그 결제의 웹훅) · `404 PAYMENT_ORDER_NOT_FOUND`
 *   - `POST /api/admin/payments/{id}/recheck` `{ reason }` → `PaymentRecheckResult` · `503 PAYMENT_GATEWAY_UNAVAILABLE`
 *   - `GET  /api/admin/payment-webhooks?result=&from=&to=&q=&page=&size=` → `PaymentPage<PaymentWebhookRow>`(받은 시각 내림차순, 90일 보관)
 *   - `GET  /api/admin/payment-anomalies?kind=&includeResolved=&from=&to=&page=&size=` → `PaymentAnomalyPage`
 *   - `POST /api/admin/payment-cancels/{id}/retry` `{ reason }` → `PaymentCancelView`(GAVE_UP → PENDING) · `409 PAYMENT_CANCEL_NOT_GAVE_UP`
 *   - `POST /api/admin/payment-cancels/{id}/manual` `{ reason }` → `PaymentCancelView`(GAVE_UP → MANUAL) · `409 PAYMENT_CANCEL_NOT_GAVE_UP`
 *   - `GET  /api/admin/payment-reconciliation` → `ReconcileOverview`(최근 14일)
 *   - `GET  /api/admin/payment-reconciliation/issues?type=&status=&day=&page=&size=` → `PaymentPage<ReconcileIssueRow>`(열린 것 먼저)
 *   - `POST /api/admin/payment-reconciliation/issues/{id}/resolve` `{ reason }` → `ReconcileIssueRow` · `409 RECONCILE_ISSUE_NOT_OPEN`
 *   - `POST /api/admin/payment-reconciliation/days/{day}/rerun` → `ReconcileDayRow`(RUNNING) · `409 RECONCILE_ALREADY_RUNNING` · `400 fields.day`(오늘·미래)
 *
 * 질의: `page` 는 0부터. `from`·`to` 는 ISO 순간이고 `[from, to)`(화면이 고른 날의 시작 ~ 마지막 날 다음 날 시작). 거르기 칸이 비면 보내지 않는다
 * (화면이 빈 칸을 넣지 않고, `useApiClient` 가 undefined·null 을 뺀다). `includeResolved` 는 켰을 때만 `true` 로 간다.
 */
const PATH = {
    orders: '/api/admin/payments',
    order: (id: string) => `/api/admin/payments/${encodeURIComponent(id)}`,
    recheck: (id: string) => `/api/admin/payments/${encodeURIComponent(id)}/recheck`,
    webhooks: '/api/admin/payment-webhooks',
    anomalies: '/api/admin/payment-anomalies',
    cancelRetry: (id: string) => `/api/admin/payment-cancels/${encodeURIComponent(id)}/retry`,
    cancelManual: (id: string) => `/api/admin/payment-cancels/${encodeURIComponent(id)}/manual`,
    reconcile: '/api/admin/payment-reconciliation',
    issues: '/api/admin/payment-reconciliation/issues',
    resolve: (id: string) => `/api/admin/payment-reconciliation/issues/${encodeURIComponent(id)}/resolve`,
    rerun: (day: string) => `/api/admin/payment-reconciliation/days/${encodeURIComponent(day)}/rerun`,
};

export function usePaymentAdminApi() {
    const auth = useAdminAuthApi();
    const client = useApiClient({ refreshCsrf: () => auth.me() });

    return {
        /** 결제 주문 목록 */
        orders: (query: PaymentOrderQuery): Promise<PaymentPage<PaymentOrderRow>> => client.get<PaymentPage<PaymentOrderRow>>(PATH.orders, { query: { ...query } }),

        /** 결제 상세 — 서버가 열람 감사를 남긴다 */
        order: (id: string): Promise<PaymentOrderDetail> => client.get<PaymentOrderDetail>(PATH.order(id)),

        /** 포트원에서 다시 확인 — 확정 코드를 한 번 더 태운다 */
        recheck: (id: string, reason: string): Promise<PaymentRecheckResult> => client.post<PaymentRecheckResult>(PATH.recheck(id), { reason }),

        /** 웹훅 수신 내역 */
        webhooks: (query: PaymentWebhookQuery): Promise<PaymentPage<PaymentWebhookRow>> => client.get<PaymentPage<PaymentWebhookRow>>(PATH.webhooks, { query: { ...query } }),

        /** 결제 이상(이상 결제 + 최종 실패한 취소) */
        anomalies: (query: PaymentAnomalyQuery): Promise<PaymentAnomalyPage> => client.get<PaymentAnomalyPage>(PATH.anomalies, { query: { ...query } }),

        /** 최종 실패한 취소를 다시 시도(대기열로 되돌림) */
        retryCancel: (cancelId: string, reason: string): Promise<PaymentCancelView> => client.post<PaymentCancelView>(PATH.cancelRetry(cancelId), { reason }),

        /** 최종 실패한 취소를 운영자가 직접 끝냈다고 표시 */
        manualCancel: (cancelId: string, reason: string): Promise<PaymentCancelView> => client.post<PaymentCancelView>(PATH.cancelManual(cancelId), { reason }),

        /** 날짜별 대사 상태 */
        reconcile: (): Promise<ReconcileOverview> => client.get<ReconcileOverview>(PATH.reconcile),

        /** 대사 불일치 이슈 */
        issues: (query: ReconcileIssueQuery): Promise<PaymentPage<ReconcileIssueRow>> => client.get<PaymentPage<ReconcileIssueRow>>(PATH.issues, { query: { ...query } }),

        /** 대사 이슈 처리(자동 보정 없음 — 운영자가 맞춘 뒤 닫는다) */
        resolveIssue: (issueId: string, reason: string): Promise<ReconcileIssueRow> => client.post<ReconcileIssueRow>(PATH.resolve(issueId), { reason }),

        /** 그 날 다시 대사 — 배치가 비동기로 돈다 */
        rerunDay: (day: string): Promise<ReconcileDayRow> => client.post<ReconcileDayRow>(PATH.rerun(day)),
    };
}
