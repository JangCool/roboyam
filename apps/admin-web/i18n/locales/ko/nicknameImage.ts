/**
 * 닉네임 이미지 심사(`/members/nickname-images`) 문구. 키는 `nicknameImage.무엇` 두 마디(상태는 `nicknameImage.status.*`). `en/nicknameImage.ts` 와 같은 키, 같은 차례.
 * 처리 실패 문구는 서버 `message` 를 그대로 쓴다(`404 NICKNAME_IMAGE_NOT_FOUND`·`409 NICKNAME_IMAGE_NOT_PENDING`·`409 NICKNAME_IMAGE_NOT_ACTIVE`·`fields.reason`).
 * ⚠ `{0}` 는 PrimeVue 가 채우는 자리다 — 번역에서는 `{count}` 로 두고 화면이 `{ count: '{0}' }` 로 넘긴다.
 */
export default {
    title: '닉네임 이미지 심사',
    subtitle: '회원이 신청한 닉네임 이미지를 확인하고 승인·반려합니다. 승인한 이미지는 회원의 닉네임 자리에 보입니다.',

    // 탭
    tabsLabel: '신청 상태',
    status: {
        PENDING: '심사 대기',
        APPROVED: '승인',
        REJECTED: '반려',
        REVOKED: '해제',
    },

    // 검색
    searchPlaceholder: '닉네임 검색',
    clearSearch: '검색 지우기',
    readOnlyHint: '보기 권한만 있어요. 승인·반려·해제는 닉네임 이미지 처리 권한이 필요해요.',

    // 표
    colImage: '이미지',
    colMember: '회원',
    colFile: '파일',
    colCreatedAt: '신청 시각',
    colReview: '처리',
    fileLine: '{format} · {size}KB',
    sizeLine: '{width}×{height}',
    frames: '{count}프레임',
    fileGone: '이미지 파일이 지워졌어요',
    still: '정지 이미지',
    imageAlt: '{name}님의 닉네임 이미지',
    zoom: '확대',
    zoomLabel: '{name}님의 닉네임 이미지 크게 보기',
    zoomTitle: '{name}님의 닉네임 이미지',
    zoomNote: '실제 크기 240×60 을 {zoom}배로 보여요. 체크 무늬는 투명한 부분이에요.',
    reviewedBy: '{time} · {name}',
    reasonLabel: '사유',
    loadFailed: '닉네임 이미지 신청을 불러오지 못했어요',

    // 빈 상태
    emptyPending: '심사할 신청이 없어요',
    emptyPendingHint: '회원이 새로 신청하면 여기에 보이고 운영자 알림이 와요.',
    emptyApproved: '승인한 이미지가 없어요',
    emptyRejected: '반려한 신청이 없어요',
    emptyRevoked: '해제한 이미지가 없어요',

    // 처리
    approve: '승인',
    approveLabel: '{name}님의 닉네임 이미지 승인',
    approved: '{name}님의 닉네임 이미지를 승인했어요',
    reject: '반려',
    rejectLabel: '{name}님의 닉네임 이미지 반려',
    rejectTitle: '닉네임 이미지 반려',
    rejectMessage: '{name}님의 신청을 반려할까요? 사유는 회원에게 보여요.',
    rejected: '{name}님의 신청을 반려했어요',
    revoke: '해제',
    revokeLabel: '{name}님의 닉네임 이미지 해제',
    revokeTitle: '닉네임 이미지 해제',
    revokeMessage: '{name}님에게 적용 중인 이미지를 내릴까요? 닉네임 자리가 글자로 돌아가고 사유는 회원에게 보여요.',
    revoked: '{name}님의 닉네임 이미지를 해제했어요',
    reasonPlaceholder: '회원에게 보일 사유를 적어 주세요.',
    reasonHelp: '1~{max}자',
    reasonRequired: '사유를 적어 주세요.',
    reasonTooLong: '사유는 {max}자까지 적을 수 있어요.',
};
