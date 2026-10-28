/**
 * 대시보드 커머스 칸 — `GET /api/admin/commerce/dashboard`(commerce.md 19절, 계약 `commerce-groupbuy-contract.md` 화면 목록 「대시보드 커머스 칸」).
 * 숫자는 서버가 그 운영자의 권한 안에서 센다 — 볼 권한이 없는 칸은 `null`(화면이 그리지 않는다). 「답변 안 한 Q&A」는 2b-6 에서 더한다.
 */
export interface CommerceDashboardView {
    /** 준비 대기 — 발송 전(CONFIRMED) 주문 */
    preparingWaiting: number | null;
    /** 열린 취소·반품 요청 — 2영업일 경고 · 3영업일 심각 */
    openRequests: { total: number; warn: number; critical: number } | null;
    /** 결제 이상(열린 것) */
    paymentAnomalies: number | null;
    /** 곧 만료될 선점(2분 안) */
    expiringHolds: number | null;
    /** 남은 수량 적은 모집 중 공구(남은 10% 이하 또는 5개 이하) */
    lowStockGroupBuys: number | null;
    /** 발송 예정일·기한 지난 발송 전 주문 */
    shipOverdue: number | null;
    /** 발송 뒤 N일 지난 SHIPPED(배송완료 처리 누락) */
    deliveryStale: { count: number; days: number } | null;
    generatedAt: string;
}
