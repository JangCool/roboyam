/**
 * 회원 탈퇴의 문구 — 계정 화면의 「회원 탈퇴」 줄, 탈퇴 화면(`/settings/account/withdraw`: 사실 고지 · 선택 · 본인 확인 · 탈퇴 진행 중), 탈퇴 완료 화면(`/withdrawn`).
 * 다크패턴 규제 기준(`docs/plans/member-withdrawal/00-analysis.md` 6절): 사실만, 한 화면, 만류 팝업 없음, 사유는 선택, 두 선택지는 같은 모양.
 * 금액은 `payment.won`, 포인트는 `UtilPoint.points`. 키는 `withdrawal.무엇` 두 마디. `en/withdrawal.ts` 와 같은 키, 같은 차례.
 */
export default {
    title: '회원 탈퇴',
    rowHint: '계정을 삭제하고 서비스를 그만둬요',
    seoDescription: '{site} 계정을 삭제해요.',
    loading: '탈퇴 정보를 불러오는 중',
    loadFailed: '탈퇴 정보를 불러오지 못했어요',
    intro: '탈퇴하면 어떻게 되는지 먼저 확인해 주세요. 아래 내용을 확인하고 단추를 누르면 바로 처리돼요.',

    // 사라지는 것
    lossesTitle: '사라지는 것',
    lossFreePoints: '적립 포인트 {points}',
    lossFreePointsHelp: '탈퇴하면 소멸되고 되돌릴 수 없어요.',
    lossItems: '포인트샵 아이템 {count}개',
    lossItemsHelp: '산 아이템과 장착한 꾸미기가 모두 없어져요.',
    lossItemsRefundable: '그중 {count}개는 산 지 7일이 안 됐고 쓰지 않아서 환불 신청할 수 있어요.',
    lossItemsRefundLink: '내 아이템에서 환불 신청하기',
    lossGrade: '등급·출석 기록',
    lossGradeHelp: '{grade} 등급과 연속 출석 {days}일이 없어져요. 다시 가입하면 처음부터 시작해요.',
    lossGradeHelpNoGrade: '등급과 출석 기록이 없어져요. 다시 가입하면 처음부터 시작해요.',

    // 남는 것
    remainsTitle: '남는 것',
    remainPosts: '글 {posts}개 · 댓글 {comments}개',
    remainPostsHelp: '작성자가 「탈퇴한 회원」으로 바뀌고 글은 그대로 남아요. 지우고 싶은 글은 탈퇴 전에 직접 지워 주세요.',
    remainPostsLink: '내 글 보기',
    remainTrades: '거래 기록',
    remainTradesHelp: '결제·주문·환불·포인트 기록은 법이 정한 기간({years}년) 동안 보관돼요. 금액·날짜만 남고 누구인지는 알 수 없어요.',
    remainContact: '연락용 이메일',
    remainContactHelp: '결제 이력이 있어서 분쟁·세무 문의 때 연락할 수 있게 이메일 하나만 암호화해 따로 보관해요({years}년). 다른 개인정보와 분리되고 일반 화면에서는 볼 수 없어요.',
    /** 로보티콘 작가(`emoticonCreator` 가 있음) — 판매 수익이 있던 작가는 직접 결제한 적이 없어도 거래 이력으로 본다(V148) */
    remainContactHelpCreator: '결제나 로보티콘 판매 수익 이력이 있어서 분쟁·세무·정산 문의 때 연락할 수 있게 이메일 하나만 암호화해 따로 보관해요({years}년). 다른 개인정보와 분리되고 일반 화면에서는 볼 수 없어요.',

    // 로보티콘 작가(`emoticonCreator` — V148)
    creatorTitle: '내 로보티콘(작가)',
    creatorPacks: '탈퇴하면 내 팩 {count}개의 새 판매가 멈춰요. 이미 산·받은 회원은 계속 쓸 수 있어요.',
    creatorPacksHelp: '멈춘 팩은 다시 판매되지 않아요.',
    creatorReviews: '심사 중인 {count}건은 반려로 닫혀요.',
    creatorEarnings: '아직 받지 못한 수익 {amount}',
    creatorEarningsGross: '원천징수 전 금액이에요.',
    creatorEarningsPayout: '남은 수익은 최소 지급액과 상관없이 다음 지급일에 한 번에 보내드려요.',
    creatorEarningsNoAccount: '정산 정보가 없어서 아래 「정할 것」에서 어떻게 할지 골라야 해요.',

    // 남은 일
    pendingTitle: '끝나야 하는 일',
    pendingIntro: '아래 일이 끝나면 탈퇴가 자동으로 완료돼요. 지금 신청하면 그때까지 「탈퇴 진행 중」이 돼요.',
    pendingKind: {
        GROUP_BUY_ORDER: '진행 중인 공동구매 주문',
        RETURN_REQUEST: '처리 중인 취소·반품 요청',
        PAYMENT_CANCEL: '진행 중인 환불',
        POINT_CHARGE_REFUND: '충전 포인트 환불',
        POINT_SHOP_REFUND: '심사 중인 포인트샵 환불 신청',
        BIZ_APPLICATION: '진행 중인 광고·핫딜',
        DELIVERED_RETURN_WINDOW: '반품 기한을 기다리는 주문',
    },
    pendingWaitUntil: '{date}쯤 끝나요',
    pendingWaitUnknown: '끝나는 시점은 처리 결과에 따라 달라요',
    pendingOpen: '보기',

    // 선택 — 배송완료 7일 안 주문
    choicesTitle: '정할 것',
    deliveredTitle: '배송 완료된 지 7일이 안 된 주문 {count}건',
    deliveredHelp: '단순 변심 반품은 배송 완료 뒤 7일까지 할 수 있어요. 어떻게 할지 골라 주세요.',
    deliveredOrder: '주문 {orderNo} · 반품 기한 {date}',
    deliveredWait: '반품 기한까지 기다렸다 자동 탈퇴',
    deliveredWaitHelp: '기한이 지나면 자동으로 탈퇴가 완료돼요. 그 사이 반품은 평소처럼 신청할 수 있어요.',
    deliveredNow: '지금 탈퇴',
    deliveredNowHelp: '탈퇴 뒤 반품은 고객센터 메일로 신청해요(연락용 이메일로 처리). 하자·오배송은 기한과 상관없이 같은 방법으로 할 수 있어요.',

    // 선택 — 충전 포인트
    paidTitle: '충전 포인트 {points}',
    paidHelp: '충전한 포인트는 환불받을 수 있어요. 환불 예상액을 보고 골라 주세요.',
    paidEstimate: '환불 예상액 {amount}',
    paidFeeNone: '충전한 지 7일이 안 돼 수수료 없이 전액 환불돼요.',
    paidFee: '충전한 지 7일이 지난 {points}에는 청약철회 기간 경과 수수료 {rate}%({fee})가 붙어요. 탈퇴와 상관없이 같은 규칙이에요.',
    paidRefund: '환불받고 탈퇴',
    paidRefundHelp: '환불이 끝나면 탈퇴가 완료돼요. 그때까지 「탈퇴 진행 중」이 돼요.',
    paidForfeit: '포기하고 탈퇴',
    paidForfeitHelp: '환불 없이 바로 탈퇴해요. 아래 동의가 필요해요.',
    paidForfeitConsent: '충전 포인트 {points}(환불 예상액 {amount})를 포기합니다.',

    // 선택 — 광고·핫딜(사업자)
    bizTitle: '진행 중인 광고·핫딜 {count}건',
    bizHelp: '사업자 회원의 진행 중 건이에요. 끝날 때까지 기다리거나 포기할 수 있어요.',
    bizRunning: '{label} · {date}까지',
    bizForfeitTotal: '포기하면 잃는 금액',
    bizRefundWaiting: '환불 대기 중인 금액',
    bizRefundWaitingHelp: '이미 취소된 건의 환불이에요. 어느 쪽을 고르든 그대로 환불돼요.',
    bizWait: '끝날 때까지 기다렸다 자동 탈퇴',
    bizWaitHelp: '마지막 건이 끝나면 자동으로 탈퇴가 완료돼요.',
    bizForfeit: '포기하고 지금 탈퇴',
    bizForfeitHelp: '진행 중인 건을 지금 끝내고 남은 기간분은 돌려받지 않아요. 아래 동의가 필요해요.',
    bizForfeitConsent: '진행 중인 광고·핫딜의 남은 금액 {amount}을 포기합니다. 환불 대기 중인 {waiting}은 그대로 환불받아요.',

    // 선택 — 로보티콘 남은 수익(정산 정보 없음, V148). 두 선택지는 같은 모양
    emoticonTitle: '받지 못한 로보티콘 수익 {amount}',
    emoticonHelp: '정산 정보(계좌)가 없어서 보낼 곳이 없어요. 정산 정보를 등록하거나 받지 않기로 해야 탈퇴할 수 있어요. 금액은 원천징수 전이에요.',
    emoticonRegister: '정산 정보 등록하고 받기',
    emoticonRegisterHelp: '등록하면 최소 지급액과 상관없이 다음 지급일에 한 번에 보내드려요. 등록하고 이 화면으로 돌아오면 다시 확인해요.',
    emoticonRegisterLink: '정산 정보 등록',
    emoticonRecheck: '다시 확인',
    emoticonWaive: '받지 않고 탈퇴',
    emoticonWaiveHelp: '탈퇴가 끝나면 사라지고 되돌릴 수 없어요.',
    emoticonWaiveConsent: '남은 수익 {amount}을 받지 않을게요.',
    emoticonDecisionRequired: '로보티콘 수익을 어떻게 할지 먼저 골라 주세요.',

    // 선택 — 모임장
    groupsTitle: '내가 모임장인 소모임 {count}개',
    groupsHelp: '모임마다 누구에게 넘길지 고르거나 해산할 수 있어요. 고르지 않으면 자동으로 처리돼요.',
    groupMembers: '멤버 {count}명',
    groupAuto: '자동 처리',
    groupAutoDelegate: '{nickname}님({role})에게 자동으로 넘겨요.',
    groupAutoDissolve: '멤버가 없어서 자동으로 해산돼요. 해산 패널티는 없어요.',
    groupTransfer: '넘기기',
    groupTransferHelp: '고른 멤버가 모임장이 돼요.',
    groupTransferTo: '넘겨받을 멤버',
    groupTransferPlaceholder: '멤버를 골라 주세요',
    groupDissolve: '해산',
    groupDissolveHelp: '모임이 닫히고 멤버가 모두 나가요. 탈퇴로 인한 해산에는 패널티가 없어요.',
    role: {
        MANAGER: '매니저',
        MEMBER: '멤버',
    },

    // 재가입
    rejoinTitle: '다시 가입하려면',
    rejoinText: '같은 이메일·소셜 계정·본인인증으로 {date}부터 다시 가입할 수 있어요. 새 계정이라 포인트·아이템·등급·글은 이어지지 않아요.',
    rejoinExtended: '기능 제한이 {date}에 끝나서 그날까지 늦춰졌어요. 사유: {reason}',

    // 사유(선택)
    reasonTitle: '탈퇴 사유 (선택)',
    reasonHelp: '고르지 않아도 탈퇴할 수 있어요. 서비스를 고치는 데 참고해요.',
    reasonPlaceholder: '사유를 골라 주세요',
    reason: {
        NOT_USING: '잘 쓰지 않아요',
        PRIVACY: '개인정보가 걱정돼요',
        INCONVENIENT: '쓰기 불편해요',
        OTHER_SERVICE: '다른 서비스를 써요',
        REJOIN: '새 계정으로 다시 가입하려고요',
        OTHER: '기타',
    },
    reasonNote: '더 적고 싶은 말',
    reasonNotePlaceholder: '선택 사항이에요 (200자까지)',

    // 본인 확인
    verifyTitle: '본인 확인',
    verifyPasswordHelp: '본인 확인을 위해 현재 비밀번호를 한 번 입력해요.',
    verifyRecentOk: '최근에 로그인해서 본인 확인이 끝났어요.',
    verifyRecentNeeded: '소셜 계정으로 로그인하는 계정이에요. 본인 확인을 위해 최근 10분 안에 로그인해야 해요.',
    verifyRelogin: '다시 로그인',

    // 제출
    submit: '탈퇴하기',
    submitScheduled: '탈퇴 신청하기',
    submitting: '처리하는 중…',
    submitHelpNow: '누르면 바로 탈퇴돼요. 되돌릴 수 없어요.',
    submitHelpScheduled: '누르면 「탈퇴 진행 중」이 되고, 위의 일이 끝나면 자동으로 완료돼요. 그 사이에는 취소할 수 있어요.',
    submitFailed: '탈퇴를 처리하지 못했어요.',
    consentRequired: '포기에 동의해야 진행할 수 있어요.',
    transferTargetRequired: '넘겨받을 멤버를 골라 주세요.',
    scheduledToast: '탈퇴를 신청했어요',

    // 탈퇴 진행 중
    scheduledTitle: '탈퇴 진행 중',
    scheduledText: '{date}에 신청했어요. 아래 일이 끝나면 자동으로 탈퇴가 완료되고 메일로 알려 드려요.',
    scheduledExpected: '예상 완료 {date}',
    scheduledExpectedUnknown: '완료 시점은 처리 결과에 따라 달라요',
    scheduledBlocked: '진행 중에는 글쓰기·참여·결제·충전 같은 새 활동이 막히고, 프로필·검색·쪽지에서도 보이지 않아요. 남은 일 처리와 탈퇴 취소만 할 수 있어요.',
    scheduledNotify: '10일 안에 끝나지 않으면 진행 상황을 메일로 알려 드려요.',
    cancel: '탈퇴 취소',
    cancelHelp: '취소하면 계정이 바로 보통 상태로 돌아와요.',
    cancelTitle: '탈퇴를 취소할까요?',
    cancelMessage: '계정이 바로 보통 상태로 돌아오고, 고른 선택도 모두 지워져요.',
    cancelConfirm: '탈퇴 취소',
    cancelDone: '탈퇴를 취소했어요',
    cancelFailed: '탈퇴를 취소하지 못했어요.',

    // 전역 안내 띠(`components/account/WithdrawalBanner.vue`) — 탈퇴 진행 중인 동안 모든 화면 머리에
    bannerLabel: '탈퇴 진행 중 안내',
    bannerTitle: '탈퇴가 진행 중이에요.',
    bannerText: '남은 일이 끝나면 자동으로 완료돼요.',
    bannerLink: '탈퇴 화면 보기 · 취소',

    // 탈퇴 완료(`/withdrawn`)
    doneSeoTitle: '탈퇴 완료',
    doneSeoDescription: '회원 탈퇴가 끝났어요.',
    doneTitle: '탈퇴가 완료됐어요',
    doneText: '그동안 함께해 주셔서 고마워요. 개인정보는 지웠고 아래 항목만 남아요.',
    doneRemainsTitle: '남는 것',
    doneRemainPosts: '글·댓글 — 작성자가 「탈퇴한 회원」으로 보여요.',
    doneRemainTrades: '거래 기록 — {years}년 동안 보관한 뒤 지워요(금액·날짜만, 누구인지는 알 수 없어요).',
    doneRemainContact: '연락용 이메일 — 결제·판매 수익 같은 거래 이력이 있어 {years}년 동안 암호화해 따로 보관해요.',
    doneRemainHashes: '재가입 제한용 해시 — {date}까지만 보관하고 지워요.',
    doneContactTitle: '도움이 필요하면',
    doneContactText: '탈퇴 뒤 반품·환불·하자 문의, 남긴 글의 블라인드 요청은 고객센터 메일로 보내 주세요. 주문번호나 글 주소를 함께 적으면 빨라요.',
    doneContactMail: '고객센터 메일',
    doneRejoinTitle: '다시 가입하려면',
    doneRejoin: '{date}부터 같은 이메일·소셜 계정·본인인증으로 다시 가입할 수 있어요. 새 계정으로 시작해요.',
    doneRejoinUnknown: '탈퇴 후 30일이 지나면 같은 이메일·소셜 계정·본인인증으로 다시 가입할 수 있어요. 새 계정으로 시작해요.',
    doneHome: '첫 화면으로',
};
