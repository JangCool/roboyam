/**
 * 관리자 알림의 문구 — 종 알림 서랍과 알림 이벤트 관리 화면(`/settings/notifications`). 알림 제목·본문은 서버가 요청 언어로 만들어 주는 값이라 여기에 없다.
 * 영역 이름은 권한 영역(`permission.area.*`)을 같이 쓰고, 권한에 없는 `security`(보안)만 여기 있다(`areaSecurity`). 이벤트 이름·설명은 `notificationEvent.ts`(목업 전용).
 * 키는 `notification.무엇` 두 마디(심각도 `severity.*` 만 세 마디). `en/notification.ts` 와 같은 키, 같은 차례.
 */
export default {
    // ── 종 알림 서랍 ──
    title: '알림',
    unreadSummary: '안 읽은 알림 {count}건',
    allReadSummary: '모두 읽었어요',
    markAllRead: '모두 읽음',
    unreadMark: '안 읽음',
    emptyTitle: '새 알림이 없어요',
    emptyBody: '알림이 오면 이곳에 모여요.',
    loadFailed: '알림을 불러오지 못했어요',
    loadMore: '더 보기',
    loadMoreFailed: '이전 알림을 불러오지 못했어요. 잠시 후 다시 시도해 주세요.',
    toastTitle: '새 알림 {n}건',
    toastMore: '외 {n}건',
    toastOpen: '알림 이력 보기',
    osTitle: '{product} 새 알림 {n}건',
    readFailed: '읽음 처리하지 못했어요. 잠시 후 다시 시도해 주세요.',

    // ── 심각도(알림 서랍 · 이벤트 관리 · 내 정보) ──
    severity: {
        info: '안내',
        success: '완료',
        warning: '주의',
        critical: '긴급',
    },
    areaSecurity: '보안',

    // ── 알림 이벤트 관리(/settings/notifications) ──
    adminTitle: '알림 이벤트',
    adminSubtitle: '관리자 앱에서 일어나는 사건마다 알림을 쓸지, 이메일로도 보낼지 정해요.',
    adminReload: '다시 조회',
    adminLoadFailed: '알림 이벤트를 불러오지 못했어요',
    adminEmptyTitle: '등록된 알림 이벤트가 없어요',
    adminEmptyBody: '이벤트는 시스템이 정하며, 기능이 만들어지면 이곳에 나타나요.',
    adminReadOnly: '알림 이벤트를 바꿀 권한이 없어 보기만 할 수 있어요.',
    adminNote: '스위치를 누르면 바로 저장돼요. 앱 안 알림(종)은 사용 중인 이벤트에 늘 전달되고, 알림을 받는 사람은 이벤트마다 정해져 있어 여기서 바꿀 수 없어요.',
    colEvent: '이벤트',
    colRecipient: '수신 대상',
    colSeverity: '심각도',
    colEnabled: '사용',
    colEmail: '이메일',
    colSent: '최근 30일 발송',
    mandatory: '필수',
    mandatoryHint: '필수 이벤트는 끌 수 없어요.',
    notReady: '준비 중',
    notReadyHint: '아직 이 알림을 보내는 기능이 없어요. 기능이 만들어지면 켤 수 있어요.',
    emailNotAvailable: '이 이벤트는 이메일로 보내지 않아요.',
    noManageReason: '알림 이벤트를 바꿀 권한이 없어요.',
    enabledLabel: '{name} 사용',
    emailLabel: '{name} 이메일 전달',
    sentCount: '{n}건',
    adminEnabledOn: '{name} — 알림을 켰어요',
    adminEnabledOff: '{name} — 알림을 껐어요',
    adminEmailOn: '{name} — 이메일로도 보내요',
    adminEmailOff: '{name} — 이메일로는 보내지 않아요',
};
