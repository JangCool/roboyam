/**
 * commerce 업무 문구 — 커머스 설정(`/settings/commerce`). `en/commerce.ts` 와 같은 키, 같은 차례.
 * 정책 원천: `docs/domain/commerce.md` 12-4(돈 관련 요청 속도 제한).
 */
export default {
    // ── 커머스 설정(/settings/commerce) ─
    settingsTitle: '커머스 설정',
    settingsSubtitle: '판매·결제에 공통으로 쓰는 설정이에요. 지금은 요청 제한만 있고, 판매 스위치·사업자 정보 등은 나중에 이 화면에 더해져요. 바꾼 값은 감사 로그에 남아요.',
    loadFailed: '커머스 설정을 불러오지 못했어요.',

    // ── 요청 제한 구획 ─
    limitsSection: '요청 제한',
    limitsLead: '회원 한 명이 1분 동안 보낼 수 있는 돈 관련 요청 수예요. 넘으면 회원에게 몇 초 뒤 다시 할 수 있는지 안내해요. 0으로 두면 제한하지 않아요.',
    limitsNotReady: '공구·충전·상점 구매·환불 기능은 아직 없어서 지금은 값만 저장돼요. 그 기능이 생기면 바로 적용돼요.',
    actionName: {
        GROUPBUY_JOIN: '공구 참여',
        POINT_CHARGE: '포인트 충전 요청',
        POINT_SHOP_PURCHASE: '포인트 상점 구매',
        REFUND_REQUEST: '환불 신청',
    },
    actionDesc: {
        GROUPBUY_JOIN: '공구에 참여해 수량을 잡아 두는 요청',
        POINT_CHARGE: '포인트를 충전하려고 결제를 여는 요청',
        POINT_SHOP_PURCHASE: '포인트 상점에서 아이템을 사는 요청',
        REFUND_REQUEST: '아이템·충전·반품 환불을 신청하는 요청',
    },
    perMinute: '번 / 1분',
    unlimited: '제한 없음',
    changedFrom: '저장된 값 {value}에서 바뀜',
    defaultValue: '기본값 {value}',
    useDefault: '기본값으로',
    limitRequired: '값을 입력해 주세요.',
    limitNotInteger: '정수로 입력해 주세요.',
    limitOutOfRange: '0부터 100 사이로 입력해 주세요.',
    noteApply: '저장하면 모든 서버에 15초 안에 반영돼요.',
    noteWindow: '1분 단위로 세기 때문에 1분이 바뀌는 경계에서 잠깐 최대 2배까지 몰릴 수 있어요. 남용을 막는 1차 방어이고, 진짜 한도(월 충전 한도·1인 수량 등)는 각 기능의 규칙이 지켜요.',
    lastSaved: '마지막 저장: {at} · {by}',
    neverSaved: '저장한 적 없음 — 기본값이에요.',

    // ── 저장 ─
    limitsSaved: '요청 제한을 저장했어요.',
    saveFieldsRejected: '받아들여지지 않은 칸이 있어요. 칸 아래 문구를 확인해 주세요.',
    leaveTitle: '저장하지 않은 변경이 있어요',
    leaveMessage: '이 화면을 떠나면 바꾼 값이 사라져요.',
    leaveConfirm: '버리고 나가기',
    leaveCancel: '계속 편집',
};
