/**
 * 결제 문구 — 가짜 결제창(`PaymentFakeCheckoutDialog`, 가짜 게이트웨이에서 포트원 결제창 대신)과 개발용 시험 결제 화면(`/dev/payment`).
 * 충전·공동구매 화면이 생기면 결제 결과 문구도 여기에 더한다. 키는 `payment.무엇` 두 마디. `en/payment.ts` 와 같은 키, 같은 차례.
 */
export default {
    // 공통
    orderName: '주문',
    amount: '결제 금액',
    paidAmount: '결제된 금액',
    paidAt: '결제 시각',
    expiresAt: '유효 시각',
    paymentId: '결제 번호',
    gateway: '결제창',
    gatewayName: {
        FAKE: '가짜(개발용)',
        PORTONE: '포트원',
    },
    won: '{amount}원',
    wonSuffix: ' 원',
    minuteSuffix: ' 분',
    autoCancel: '자동 취소',
    status: {
        READY: '결제 대기',
        PAID: '결제 완료',
        PARTIAL_CANCELED: '부분 취소',
        CANCELED: '전액 취소',
        EXPIRED: '만료',
    },
    cancelStatus: {
        PENDING: '취소 대기',
        SENDING: '취소 보내는 중',
        SUCCEEDED: '취소 완료',
        GAVE_UP: '취소 실패(운영자 확인 중)',
        MANUAL: '운영자가 처리함',
    },

    // 가짜 결제창
    fakeTitle: '가짜 결제창(개발용)',
    fakeNotice: '개발·시험용 결제창이에요. 실제 돈은 오가지 않아요. 결과를 골라 결제 흐름을 시험해요.',
    fakeOutcome: '결제 결과',
    fakeOption: {
        PAID: '정상 결제',
        FAILED: '결제 실패',
        AMOUNT: '금액 바꿔 결제',
        LATE: '늦게 결제',
    },
    fakeOptionHelp: {
        PAID: '주문 금액 그대로 결제해요.',
        FAILED: '카드 거절처럼 결제가 되지 않아요.',
        AMOUNT: '주문과 다른 금액으로 결제해요 — 서버가 받지 않고 자동 취소해야 해요.',
        LATE: '유효 시간이 지난 뒤 결제된 것으로 해요 — 서버가 받지 않고 자동 취소해야 해요.',
    },
    fakeAmount: '결제할 금액',
    fakeAmountHelp: '주문 금액은 {amount}원이에요.',
    fakeAmountRequired: '결제할 금액을 넣어 주세요.',
    fakeAmountSame: '주문 금액과 다른 금액을 넣어 주세요.',
    fakeClose: '창 닫기',
    fakePay: '결제하기',

    // 시험 결제 화면(/dev/payment)
    devBadge: '개발 전용 화면',
    devTitle: '시험 결제',
    devIntro: '충전·공동구매 화면이 생기기 전에 결제 모듈만 끝까지 시험해요. 시험 주문을 만들고 결제창을 열어 결과를 골라 보세요. 관리자 화면의 결제 주문에도 같은 결제가 보여요.',
    devCreateTitle: '1. 시험 주문 만들기',
    devCreateIntro: '금액과 결제 유효 시간을 정해요. 유효 시간이 지나면 결제해도 받지 않아요(늦은 결제).',
    devAmount: '금액',
    devAmountHint: '100 ~ 1,000,000원',
    devValidMinutes: '유효 시간',
    devValidMinutesHint: '1 ~ 60분',
    devCreate: '시험 주문 만들기',
    devCreateAgain: '새 시험 주문 만들기',
    devCheckoutTitle: '2. 결제창',
    devPortone: '실제 포트원 테스트 결제창이 열려요(테스트 채널 — 실제 돈은 나가지 않아요). 모바일에서는 결제사 화면으로 갔다가 이 화면으로 돌아와요.',
    devPortoneMissing: '서버가 포트원 상점 ID·결제 채널 키를 주지 않아 결제창을 열 수 없어요. 관리자 화면의 포트원 설정을 확인해 주세요.',
    devOpenCheckout: '결제창 열기',
    devNote: {
        FAILED: '결제가 되지 않아 완료 요청을 보내지 않았어요. 결제창을 다시 열 수 없으니 새 시험 주문을 만들어 주세요.',
        CLOSED: '결제창을 닫았어요. 다시 열 수 있어요.',
        PG_FAILED: '결제가 되지 않아 완료 요청을 보내지 않았어요. 같은 결제 번호로 결제창을 다시 열 수 있어요.',
    },
    devResultTitle: '3. 결과',
    devReload: '결과 다시 받기',
    devAccepted: '결제를 받아들였어요. 시험 주문이 완료됐어요.',
    devAnomaly: {
        AMOUNT_MISMATCH: '결제 금액이 주문 금액과 달라 받지 않았어요. 결제된 금액은 전액 자동 취소돼요.',
        DOMAIN_REJECTED: '주문이 결제를 받을 수 없는 상태라 받지 않았어요. 전액 자동 취소돼요.',
        LATE_PAYMENT: '유효 시간이 지난 뒤 결제돼 받지 않았어요. 전액 자동 취소돼요.',
    },
    devNotAccepted: '아직 결제가 확정되지 않았어요.',
    devAdminHint: '자동 취소가 끝났는지는 「결과 다시 받기」로 확인해요. 관리자 › 결제 › 결제 주문 · 결제 이상에서 같은 결제의 이력을 볼 수 있어요.',
};
