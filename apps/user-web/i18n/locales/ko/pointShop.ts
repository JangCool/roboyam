/**
 * 포인트 상점 문구 — 목록(`/point-shop`)·품목 상세(`/point-shop/{id}`)·구매 확인·내 아이템(`/me/items`). 화면 이름 자체는 서비스 용어(`siteTerms.pointShop`, `term('POINT_SHOP')`)다.
 * 키는 `pointShop.무엇` 두 마디. `en/pointShop.ts` 와 같은 키, 같은 차례. 서버 오류 문구는 서버가 준다(여기 없다).
 * ⚠ 문구 안의 `@`·`|` 는 글자가 아니다(`{'@'}`·`{'|'}` 로 감싼다).
 */
export default {
    seoDescription: '{site} 포인트로 닉네임 색·프로필 테두리·닉네임 변경권을 사요.',

    // 목록
    myPoints: '내 포인트',
    restrictedTitle: '정지 중이라 장착을 바꿀 수 없어요',
    restrictedText: '정지 중에는 아이템을 장착·해제하거나 아바타를 바꿀 수 없어요. 지금 장착한 것은 그대로 보이고, 목록은 계속 볼 수 있어요.',
    myItems: '내 아이템',
    kindFilter: '종류 거르기',
    kindNicknameColor: '닉네임 색',
    kindProfileBorder: '프로필 테두리',
    kindAvatar: '아바타',
    kindTicket: '변경권',
    loading: '포인트 상점을 불러오는 중',
    loadFailed: '포인트 상점을 불러오지 못했어요',
    empty: '아직 파는 아이템이 없어요',
    emptyFiltered: '이 종류는 아직 파는 아이템이 없어요',
    emptyText: '새 아이템이 들어오면 여기에 보여요.',
    listNote: '포인트 상점의 아이템은 포인트로만 사요. 사고 나서 {days}일 안에 쓰지(장착하지) 않았으면 내 아이템에서 환불할 수 있어요.',

    // 날짜 — 표시 값 시간대의 월·일
    monthDay: '{month}월 {day}일',

    // 사용 방식
    usagePermanent: '영구',
    usageConsumable: '소모형',
    usagePeriod: '기간제',
    usagePeriodDays: '기간제 {days}일',

    // 판매 상태 · 보유
    saleFrom: '{date}부터',
    saleEnded: '판매 끝',
    soldOut: '품절',
    saleUntil: '~{date}까지',
    stockLeft: '남은 {count}개',
    owned: '보유 중',
    ownedCount: '{count}장 보유',
    ownedUntil: '~{date}까지 사용 중',

    // 상세 — 살 수 없는 까닭
    blockedUpcoming: '{date}부터 살 수 있어요.',
    blockedEnded: '판매가 끝난 아이템이에요.',
    blockedSoldOut: '품절됐어요.',
    blockedOwned: '이미 가지고 있는 아이템이에요. 내 아이템에서 장착할 수 있어요.',
    blockedInsufficient: '포인트가 {points} 모자라요.',

    // 기간제 남은 기간
    daysLeft: '{days}일 남음',
    periodOver: '기간 끝',

    // 429 — 서버 문구 + 기다릴 시간
    retryAfter: '{message} ({wait} 뒤에 다시 시도해 주세요)',

    // 상세
    notFoundTitle: '찾을 수 없는 아이템이에요',
    previewTitle: '미리보기 — 내 닉네임에 적용한 모습',
    previewLight: '밝은 화면',
    previewDark: '어두운 화면',
    previewCorrectedLight: '밝은 화면에서는 읽기 쉽게 조금 진하게 보여요.',
    previewCorrectedDark: '어두운 화면에서는 읽기 쉽게 조금 밝게 보여요.',
    previewCorrectedBoth: '밝은·어두운 화면에서 읽기 쉽게 조금씩 바꿔 보여요.',
    previewInPost: '글·댓글에서는 이렇게 보여요',
    previewJustNow: '방금',
    previewComment: '댓글·목록',
    previewProfile: '내 프로필',
    previewPostHead: '글 보기의 작성자',
    borderName: '고리 · {name}',
    borderWhere: '테두리는 내 프로필과 글 보기의 작성자 자리에 보여요. 목록·댓글·쪽지에서는 닉네임 색만 보여요.',

    // 변경권 — 미리보기 대신 다섯 줄
    ticketAboutTitle: '닉네임 변경권은 이런 아이템이에요',
    ticketWhatLabel: '할 수 있는 일',
    ticketWhat: '닉네임을 한 번 더 바꿀 수 있어요. 1장에 1번이에요.',
    ticketWhenLabel: '쓰는 때',
    ticketWhen: '가입 뒤 1번의 무료 변경을 쓴 다음부터 필요해요. 무료 변경이 남아 있으면 변경권은 쓰이지 않아요.',
    ticketWhereLabel: '쓰는 곳',
    ticketWhere: '설정 › 프로필에서 닉네임을 바꿀 때 써요.',
    ticketPeriodLabel: '기간',
    ticketPeriod: '기간 없이 쓸 때까지 가지고 있어요.',
    ticketRefundLabel: '환불',
    ticketRefund: '쓰지 않았고 산 지 {days}일 안이면 내 아이템에서 환불할 수 있어요. 쓴 변경권은 환불할 수 없어요.',
    ticketFreeLeft: '지금은 무료 변경이 1번 남아 있어 변경권 없이 바꿀 수 있어요.',
    ticketFreeUsed: '무료 변경을 이미 썼어요 · 지금 가진 변경권 {count}장',

    // 구매
    buy: '{points}에 사기',
    useInProfile: '프로필에서 쓰기',
    goEquip: '장착하러 가기',
    confirmTitle: '구매할까요?',
    confirmPrice: '가격',
    confirmBalance: '내 포인트',
    confirmBalanceChange: '지금 {before} → 산 뒤 {after}',
    confirmFreeFirst: '적립 포인트부터 빠져요.',
    confirmPeriodFromNow: '산 순간부터 {days}일 동안 쓸 수 있어요.',
    confirmPeriodContinue: '지금 기간 끝({date})에 {days}일이 이어 붙어요.',
    confirmNotice: '사용(장착)하면 환불할 수 없어요 · {days}일 안에 쓰지 않으면 환불할 수 있어요. 이 안내를 확인했어요.',
    confirmBuy: '구매',
    bought: '{name} 구매를 마쳤어요',

    // 내 아이템
    myItemsLoading: '내 아이템을 불러오는 중',
    myItemsLoadFailed: '내 아이템을 불러오지 못했어요',
    myItemsEmpty: '아직 가진 아이템이 없어요',
    myItemsEmptyText: '포인트 상점에서 닉네임 색·프로필 테두리·변경권을 살 수 있어요.',
    equippedTitle: '지금 장착',
    slotEmpty: '장착한 것 없음',
    equippedNote: '자리마다 하나만 장착해요. 한 번이라도 장착한 아이템은 해제해도 환불할 수 없어요(안 쓰고 {days}일 안이면 환불할 수 있어요).',
    equippedTag: '장착 중',
    equip: '장착',
    unequip: '해제',
    refund: '환불',
    // 정지 중 환불 신청(운영자 수락 — 계약 「1-2 정지 중 환불 신청」)
    refundRequest: '환불 신청',
    refundRequestConfirmTitle: '환불을 신청할까요?',
    refundRequestConfirmText: '{name} — 운영자가 확인한 뒤 환불돼요({points}, 환불 기한 {date}). 기다리는 동안 이 아이템은 쓸 수 없어요.',
    refundRequested: '환불을 신청했어요. 운영자가 확인하면 알려 드릴게요.',
    refundRequestPending: '환불 신청함',
    refundRequestPendingText: '운영자 확인 기다리는 중',
    refundRequestRejected: '환불 거절',
    refundRequestRejectedReason: '환불 신청이 거절됐어요: {reason}',
    refundRequestRejectedPlain: '환불 신청이 거절됐어요.',
    ticketCount: '{count}장 보유',
    ticketOwned: '보유',
    ticketUsed: '사용함',
    boughtOn: '{date} 구매',
    refundUntil: '{date}까지 환불 가능',
    usedNoRefund: '사용함 · 환불 불가',
    refundExpired: '환불 기한 지남',
    revokedTitle: '환불됨 {count}개',
    equipConfirmTitle: '장착할까요?',
    equipConfirmText: '장착하면 이 아이템은 환불할 수 없어요. 해제해도 마찬가지예요.',
    equipped: '{name}을(를) 장착했어요',
    unequipped: '장착을 해제했어요',
    refundConfirmTitle: '환불할까요?',
    refundConfirmText: '{name} — {points}를 돌려받아요(환불 기한 {date}). 환불하면 이 아이템은 사라져요. 장착하거나 쓴 아이템은 환불할 수 없어요.',
    refunded: '{points}를 돌려받았어요 · 지금 {balance}',

    // 프로필 테두리 팔레트 이름(`UtilPointShop.BORDERS` 의 `nameKey`)
    border_GOLD: '금빛',
    border_SILVER: '은빛',
    border_ROSE: '장미',
    border_OCEAN: '바다',
    border_FOREST: '숲',
    border_NIGHT: '밤하늘',
    border_RAINBOW: '무지개',
};
