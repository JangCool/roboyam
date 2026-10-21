/**
 * 알림 종(헤더)·알림 패널·안 읽은 수 배지(알림·쪽지함)의 문구 — `components/notification/*`·`utils/util-notification.ts`·`composables/notification/*` 가 쓴다.
 * 키는 `notification.무엇` 두 마디. `en/notification.ts` 와 같은 키, 같은 차례.
 */
export default {
    // 이름
    title: '알림',
    settings: '알림 설정',
    panelClose: '알림 패널 닫기',
    recentList: '최근 알림',
    unread: '안 읽음',
    viewAll: '알림 모두 보기 ›',

    // 상태
    loadFailed: '알림을 불러오지 못했어요',
    loadingSummary: '알림을 불러오는 중이에요',
    noneSummary: '알림이 없어요',
    summary: '최근 알림 {total}개 · 안 읽은 알림 {unread}개',
    emptyTitle: '새 알림이 없어요',
    emptyDescription: '댓글이나 답글이 달리면 여기에 알려 드려요',

    // 배지 이름(스크린리더용)
    badgeUnread: '알림, 안 읽은 알림 {count}개',
    badgeUnreadOver: '알림, 안 읽은 알림 {max}개 이상',
    dmTitle: '쪽지함',
    dmBadgeUnread: '쪽지함, 안 읽은 대화 {count}개',
    dmBadgeUnreadOver: '쪽지함, 안 읽은 대화 {max}개 이상',
};
