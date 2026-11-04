/** 통계 수집 설정(`/settings/analytics`) — 방문·알림·게시판 통계를 항목별로 켜고 끈다(화면 확정 2026-11-04) */
export default {
    title: '통계 수집',
    subtitle: '방문·알림·게시판 통계를 항목별로 켜고 꺼요. 모든 항목은 처음에 꺼져 있어요.',
    readOnly: '설정을 바꿀 권한이 없어서 조회만 할 수 있어요.',
    forbiddenBody: '통계 수집 설정을 볼 권한이 없어요.',
    loadFailed: '통계 수집 설정을 불러오지 못했어요.',
    saved: '통계 수집 설정을 저장했어요.',
    saveFieldsRejected: '맞지 않는 값이 있어요. 표시된 칸을 확인해 주세요.',

    // ── 수집 방식 ─
    howTitle: '수집 방식',
    howVisitorId: '방문자는 브라우저 저장소에 둔 무작위 방문자 ID로 구분해요. IP 주소와 원본 브라우저 정보는 저장하지 않아요.',
    howDelayVisits: '방문 숫자는 서버가 1분마다 몰아 쓰므로 최대 1분 늦게 보여요.',
    howDelayBoard: '게시판 활동은 10분마다 집계해서 최대 10분 늦게 보여요.',
    howDefaultOff: '켜면 그때부터 모아요. 켜기 전 기간의 숫자는 없어요.',
    updatedBy: '마지막 변경: {at} · {by}',
    neverUpdated: '아직 바꾼 적이 없어요.',

    // ── 구획 ─
    visitSection: '방문 수집',
    otherSection: '알림·게시판 집계',
    visitOffNote: '방문 수집이 꺼져 있어 아래 항목은 동작하지 않아요. 고른 값은 그대로 남아요.',

    // ── 항목 ─
    label: {
        enabled: '방문 수집',
        visits: '방문자·페이지뷰·지금 접속',
        rawEvents: '방문 원본 기록',
        referrers: '유입 경로',
        flows: '이동 흐름',
        notificationSends: '알림 발송 기록',
        boardActivity: '게시판 활동 집계',
    },
    help: {
        enabled: '끄면 사용자 브라우저가 방문 신호를 아예 보내지 않아요. 아래 방문 항목은 이것이 켜져야 동작해요.',
        visits: '오늘 방문자·페이지뷰와 지금 접속(최근 5분)을 세요.',
        rawEvents: '어떤 페이지를 언제 봤는지 원본을 보관해요. 「인기 페이지」의 원천이고, 나중에 자세히 분석할 때 써요.',
        referrers: '방문이 어디서 왔는지(검색·SNS·직접·다른 사이트·캠페인)와 많이 들어온 사이트를 세요.',
        flows: '방문자가 어느 페이지에서 어느 페이지로 옮겨 갔는지 세요.',
        notificationSends: '알림을 보낼 때마다 채널별 성공·실패를 기록해요.',
        boardActivity: '글·댓글·가입 수와 게시판별 활동을 모아 세요.',
    },
    loadLow: '부담 낮음',
    loadMedium: '부담 보통',
    loadNote: {
        visits: '서버가 1분마다 몰아 써요',
        rawEvents: '1분마다 여러 줄을 몰아 써요',
        referrers: '방문 숫자와 함께 1분마다 몰아 써요',
        flows: '1분마다 이동 기록을 몰아 써요',
        notificationSends: '보낼 때 한 줄씩 써요',
        boardActivity: 'DB 작업이 10분마다 집계해요',
    },
    usedBy: '쓰는 곳',
    usedByFlows: '다음 단계 「방문 분석」 화면(준비 중)',
    retentionLabel: '원본 보관 기간',
    retentionHelp: '보관 기간이 지난 원본 기록은 지워요.',
    retentionDays: '{n}일',
    privacyNotice: '방문 원본 기록(본 페이지·유입 경로)을 보관하려면 개인정보 처리방침에 그 내용과 보관 기간을 적어야 해요.',
    privacyLink: '약관·처리방침 관리로 가기',

    // ── 이력·나가기 ─
    historyHint: '통계 수집 설정을 바꾼 기록이에요. 최신이 위에 있어요.',
    leaveTitle: '저장하지 않은 변경이 있어요',
    leaveMessage: '이 화면을 떠나면 바꾼 설정이 사라져요.',
    leaveConfirm: '저장하지 않고 나가기',
};
