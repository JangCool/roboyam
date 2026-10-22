/**
 * 알림 종(헤더)·알림 패널·안 읽은 수 배지의 문구 — `components/notification/*`·`utils/util-notification.ts`·`composables/notification/*` 가 쓴다.
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
};
