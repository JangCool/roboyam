/**
 * 기능 제한 안내 문구 — `components/sanction/FeatureRestrictionNotice.vue`·`utils/util-feature-restriction-messages.ts` 가 쓴다(commerce.md 12-3). 계정 정지 배너는 `sanction.*`.
 * 키는 `restriction.무엇` 두 마디. `en/restriction.ts` 와 같은 키, 같은 차례. 시각 `{clock}` 은 표시 값으로 그린 글자다.
 */
export default {
    withPeriod: '{title} · {period}',
    until: '{month}월 {day}일 {clock}까지',
    untilYear: '{year}년 {month}월 {day}일 {clock}까지',
    untilLifted: '해제될 때까지',
    reason: '사유: {reason}',

    title_POINT_FREEZE: '포인트가 동결돼 있어요',
    title_GROUP_BUY_BAN: '공동구매 참여가 제한돼 있어요',
    title_CHARGE_BAN: '포인트 충전이 제한돼 있어요',

    type_POINT_FREEZE: '포인트 동결',
    type_GROUP_BUY_BAN: '공동구매 참여 제한',
    type_CHARGE_BAN: '충전 제한',

    allowed_POINT_FREEZE: '충전 포인트 환불 신청은 그대로 할 수 있어요.',
    allowed_GROUP_BUY_BAN: '이미 결제한 주문의 배송 조회·취소·반품은 그대로 할 수 있어요.',
    allowed_CHARGE_BAN: '가진 포인트 사용과 환불 신청은 그대로 할 수 있어요.',
};
