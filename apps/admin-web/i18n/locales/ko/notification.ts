/**
 * 관리자 알림 서랍의 문구. 알림 제목·본문은 서버가 요청 언어로 만들어 주는 값이라 여기에 없다.
 * 키는 `notification.무엇` 두 마디. `en/notification.ts` 와 같은 키, 같은 차례.
 */
export default {
    title: '알림',
    unreadSummary: '안 읽은 알림 {count}건',
    allReadSummary: '모두 읽었어요',
    markAllRead: '모두 읽음',
    unreadMark: '안 읽음',
    emptyTitle: '새 알림이 없어요',
    emptyBody: '알림이 오면 이곳에 모여요.',
    loadFailed: '알림을 불러오지 못했어요',
    toastTitle: '새 알림 {n}건',
    toastMore: '외 {n}건',
    toastOpen: '알림 이력 보기',
    osTitle: '{product} 새 알림 {n}건',
    readFailed: '읽음 처리하지 못했어요. 잠시 후 다시 시도해 주세요.',
};
