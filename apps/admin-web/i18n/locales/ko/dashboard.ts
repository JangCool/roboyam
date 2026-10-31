/**
 * 대시보드 화면의 문구 — 처리할 일 칸(사업자·광고·핫딜) · 커머스 칸(commerce.md 19절). 키는 `dashboard.무엇` 두 마디. `en/dashboard.ts` 와 같은 키, 같은 차례.
 */
export default {
    title: '대시보드',
    emptyTitle: '아직 보여 드릴 내용이 없어요',
    emptyBody: '지표와 목록은 관리 기능이 만들어지는 대로 이곳에 추가돼요.',

    // ── 커머스 칸 ─
    commerceTitle: '커머스',
    generatedAt: '{at} 기준',
    refresh: '새로 고침',
    loadFailed: '커머스 현황을 불러오지 못했어요.',
    preparing: '준비 대기',
    preparingNote: '결제 확정 · 아직 준비중 전',
    openRequests: '열린 취소·반품 요청',
    openRequestsNote: '2영업일 경고 {warn} · 3영업일 심각 {critical}',
    paymentAnomalies: '결제 이상',
    expiringHolds: '곧 만료될 선점',
    expiringHoldsNote: '2분 안에 결제 시간이 끝나요',
    lowStock: '남은 수량 적은 공구',
    lowStockNote: '남은 10% 이하 또는 5개 이하',
    shipOverdue: '발송 예정일 지난 발송 전 주문',
    shipOverdueNote: '지연 공지가 필요한지 확인하세요',
    deliveryStale: '발송 뒤 {days}일 지난 배송 중',
    deliveryStaleNote: '배송완료 처리 누락 — 청약철회 기한이 시작되지 않아요',

    // ── 처리할 일 칸(04-ia-review Q5 — 통합 신청 심사 큐 대신) ─
    todoTitle: '처리할 일',
    todoLoadFailed: '처리할 일을 불러오지 못했어요.',
    todoAdReview: '광고 심사',
    todoHotdealReview: '핫딜 심사',
    todoReviewNote: '사업자가 신청한 건 — 승인해야 결제가 열려요',
    todoDeposit: '입금 확인 대기',
    todoDepositNote: '수동 결제 — 계약서 사본 + 입금 확인 뒤 예약 확정',
    todoDepositOverdue: '입금 기한 지남',
    todoDepositOverdueNote: '기한 연장 또는 취소가 필요해요',
    todoRefund: '환불 심사',
    todoRefundNote: '진행 중 건의 환불 요청',
    todoBusinessReview: '사업자 인증 대기',
    todoBusinessReviewNote: '처음 신청·재신청 + 재심사',
    todoDomainLink: '{domain} {count}',
};
