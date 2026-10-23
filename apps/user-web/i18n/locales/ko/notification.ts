/**
 * 알림 종(헤더)·알림 패널·안 읽은 수 배지·알림 화면(`/notifications`)·알림 설정(`/settings/notifications`)의 문구 — `components/notification/*`·`utils/util-notification.ts`·`composables/notification/*`·그 화면들이 쓴다.
 * 알림 설정의 이벤트 이름·설명은 서버 회원 이벤트 카탈로그가 준다(여기 없다).
 * 알림 한 줄의 문장은 서버가 요청 언어로 풀어 준다(여기 없다). 키는 `notification.무엇` 두 마디. `en/notification.ts` 와 같은 키, 같은 차례.
 */
export default {
    // 이름
    title: '알림',
    panelClose: '알림 패널 닫기',
    recentList: '최근 알림',
    unread: '안 읽음',
    readAll: '모두 읽음',

    // 상태
    loadFailed: '알림을 불러오지 못했어요',
    loadingSummary: '알림을 불러오는 중이에요',
    noneSummary: '알림이 없어요',
    summary: '최근 알림 {total}개 · 안 읽은 알림 {unread}개',
    emptyTitle: '새 알림이 없어요',
    emptyDescription: '댓글이나 답글이 달리면 여기에 알려 드려요',
    readAllFailed: '모두 읽음으로 바꾸지 못했어요. 잠시 후 다시 시도해 주세요.',

    // 배지 이름(스크린리더용)
    badgeUnread: '알림, 안 읽은 알림 {count}개',
    badgeUnreadOver: '알림, 안 읽은 알림 {max}개 이상',

    // 알림 화면(`/notifications`)·패널의 링크
    seoDescription: '{site}에서 받은 알림이에요.',
    viewAll: '알림 모두 보기',
    settingsLink: '알림 설정',
    listLabel: '알림 목록',
    filterLabel: '알림 거르기',
    filterComment: '댓글·답글',
    filterSystem: '계정',
    unreadOnly: '안 읽은 것만',
    readAllFiltered: '이 목록 모두 읽음',
    readAllDone: '모두 읽음으로 바꿨어요',
    emptyFiltered: '이 묶음에는 알림이 없어요',
    unreadNone: '불러온 알림 중 안 읽은 알림이 없어요',
    unreadNoneMore: '더 보기로 지난 알림을 불러와 찾을 수 있어요',
    pageSummary: '알림 {total}개 · 안 읽은 알림 {unread}개',
    retention: '알림은 {days}일 동안 보관돼요.',

    // 알림 설정(`/settings/notifications`)
    settingsTitle: '알림 설정',
    settingsSeo: '{site}에서 어떤 알림을 받을지 정해요.',
    settingsLoading: '알림 설정을 불러오는 중',
    settingsLoadFailed: '알림 설정을 불러오지 못했어요',
    settingsIntro: '운영자가 보내는 알림 중 받고 싶지 않은 것을 끌 수 있어요. 이메일 스위치는 이메일로도 보내는 알림에만 있어요.',
    settingsEmpty: '지금 받을 수 있는 알림이 없어요',
    areaContent: '내 글·댓글',
    areaMember: '내 계정',
    areaSafety: '신고·운영',
    channelInApp: '앱 알림',
    channelEmail: '이메일',
    lockedAccount: '계정 알림이라 끌 수 없어요',
    lockedAction: '운영 처리 알림이라 끌 수 없어요',
    emailNotVerified: '이메일 인증을 마치면 이메일로도 받을 수 있어요.',
    emailNoAddress: '계정에 이메일이 없어 이메일로는 받을 수 없어요.',
};
