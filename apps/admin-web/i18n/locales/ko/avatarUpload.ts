/**
 * 아바타 올리기 심사(`/members/avatar-uploads`) 문구 — 표·탭·사유 칸의 공통 글자는 `nicknameImage.*` 를 같이 쓰고, 아바타에만 있는 것만 여기 둔다.
 * 키는 `avatarUpload.무엇` 두 마디(변경권은 `avatarUpload.ticket.*`). `en/avatarUpload.ts` 와 같은 키, 같은 차례.
 */
export default {
    title: '아바타 올리기 심사',
    subtitle: '회원이 아바타 변경권으로 신청한 직접 만든 그림을 확인하고 승인·반려합니다. 승인하면 그 회원의 프로필 아바타가 됩니다.',
    readOnlyHint: '보기 권한만 있어요. 승인·반려·해제는 닉네임 이미지 처리 권한이 필요해요.',
    loadFailed: '아바타 신청을 불러오지 못했어요',

    colImage: '그림',
    colTicket: '변경권',
    motion: '움직임',
    imageAlt: '{name}님이 올린 아바타',
    ticket: {
        RESERVED: '예약됨',
        USED: '사용',
        RETURNED: '반환',
    },
    inUse: '지금 프로필 아바타',
    notInUse: '다른 아바타를 쓰는 중',
    replaced: '새 그림으로 바뀜',
    pausesNickname: '승인하면 움직이는 닉네임 이미지가 쉬어요',

    zoomLabel: '{name}님이 올린 아바타 크게 보기',
    zoomTitle: '{name}님이 올린 아바타',
    whereProfile: '프로필',
    whereView: '글 보기',
    whereList: '글 목록(정지)',
    zoomNote: '위는 원본(체크 무늬는 투명한 부분), 아래는 공개 사이트의 실제 크기예요. 움직이는 GIF 는 글 목록에서 첫 장면으로 보여요.',

    emptyPending: '심사할 신청이 없어요',
    emptyPendingHint: '회원이 아바타 변경권으로 새로 신청하면 여기에 보여요.',
    emptyApproved: '승인한 아바타가 없어요',
    emptyRejected: '반려한 신청이 없어요',
    emptyRevoked: '해제한 아바타가 없어요',

    approveLabel: '{name}님의 아바타 승인',
    approved: '{name}님의 아바타를 승인했어요. 회원의 프로필 아바타가 됐어요.',
    rejectLabel: '{name}님의 아바타 반려',
    rejectTitle: '아바타 반려',
    rejectMessage: '{name}님의 신청을 반려할까요? 사유는 회원에게 보이고, 예약된 아바타 변경권 1장은 회원에게 돌아가요.',
    rejected: '{name}님의 신청을 반려했어요',
    revokeLabel: '{name}님의 아바타 해제',
    revokeTitle: '아바타 해제',
    revokeMessage: '{name}님이 올린 아바타를 내릴까요? 쓰는 중이면 마지막으로 쓴 정지 아바타로 돌아가고, 쓴 변경권은 돌려주지 않아요. 사유는 회원에게 보여요.',
    revoked: '{name}님의 아바타를 해제했어요',
};
