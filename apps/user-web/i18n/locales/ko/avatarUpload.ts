/**
 * 아바타 올리기(`/settings/avatar-upload`) 문구 — 변경권 · 신청 · 승인 대기 · 반려 · 내가 올린 아바타. 키는 `avatarUpload.무엇`(파일 문제만 `avatarUpload.problem.<코드>`).
 * `en/avatarUpload.ts` 와 같은 키, 같은 차례.
 */
export default {
    seo: '{site}에서 직접 만든 그림을 아바타로 써요.',
    intro: '직접 만든 그림을 아바타로 써요. 아바타 변경권 1장으로 신청하고, 운영팀 확인 뒤 바로 프로필 아바타가 돼요. 정지 그림도, 움직이는 GIF 도 돼요.',
    loading: '아바타 올리기 상태를 불러오는 중',
    loadFailed: '아바타 올리기 상태를 불러오지 못했어요',
    emailTitle: '이메일 인증을 마쳐야 신청할 수 있어요.',
    restrictedTitle: '정지 중에는 신청·취소·지우기를 할 수 없어요.',

    ticketTitle: '아바타 변경권 {count}장',
    ticketRule: '신청할 때 1장이 예약되고, 승인되면 쓰여요. 반려되거나 신청을 취소하면 돌려받아요.',
    ticketReservedNote: '1장은 지금 승인을 기다리는 신청에 예약돼 있어요.',
    buyTicket: '변경권 사기 · {points}',
    buyTicketPlain: '변경권 사기',

    currentTitle: '내가 올린 아바타',
    currentInUse: '지금 프로필 아바타로 쓰고 있어요.',
    currentKept: '다른 아바타를 쓰는 중이에요. 다시 쓸 때는 변경권이 들지 않아요.',
    whereView: '글 보기',
    whereList: '글 목록',
    remove: '지우기',
    removeConfirmTitle: '내가 올린 아바타를 지울까요?',
    removeConfirmText: '지우면 다시 쓰려면 새로 신청해야 하고, 쓴 변경권은 돌려받지 않아요. 쓰는 중이었다면 마지막으로 쓴 정지 아바타로 돌아가요.',
    altCurrent: '내가 올린 아바타',
    altRequested: '승인을 기다리는 아바타',
    altPicked: '고른 그림',

    pendingText: '운영팀이 확인하면 바로 프로필 아바타가 돼요.',
    pendingTicket: '변경권 1장이 이 신청에 예약돼 있어요. 취소하거나 반려되면 돌려받아요.',
    cancelConfirmTitle: '신청을 취소할까요?',
    cancelConfirmText: '예약된 변경권 1장을 돌려받아요.',
    rejectedTitle: '신청이 반려됐어요',
    rejectedTicket: '예약했던 변경권 1장은 돌려받았어요. 고쳐서 다시 신청할 수 있어요.',

    formTitle: '아바타 신청',
    formTitleChange: '새 그림으로 신청',
    ruleFormat: 'PNG·JPG·WebP·GIF, 정사각형(1:1)',
    ruleSize: '{kb}KB 이하, {min}×{min}px 이상',
    ruleMotion: '움직이는 GIF 는 글 보기·댓글·프로필에서 움직이고, 글 목록에서는 첫 장면으로 보여요. 움직이는 WebP 는 안 돼요.',
    ruleReview: '다른 사람의 얼굴·캐릭터·로고·저작물, 불쾌한 그림은 반려돼요.',
    consent: '직접 만들었거나 쓸 권리가 있는 그림이에요.',
    motionNicknameNote: '움직이는 닉네임 이미지를 쓰고 있어요. 이 움직이는 아바타가 승인되면 닉네임 이미지는 쉬어요(움직이는 꾸미기는 하나만 — 나중에 고른 쪽이 남아요).',
    noTicket: '아바타 변경권이 있어야 신청할 수 있어요.',
    goBuy: '상점에서 사기',
    submit: '변경권 1장으로 신청하기',
    submitReplace: '이 그림으로 신청 바꾸기',
    replaceConfirmTitle: '대기 중인 신청을 바꿀까요?',
    replaceConfirmText: '지금 승인을 기다리는 신청은 취소되고 이 그림으로 다시 신청해요. 예약된 변경권을 그대로 써서 1장이 더 들지 않아요.',

    toastSubmitted: '신청했어요. 운영팀이 확인하면 알려 드려요.',
    toastCancelled: '신청을 취소했어요. 변경권을 돌려받았어요.',
    toastRemoved: '내가 올린 아바타를 지웠어요',
    failStale: '이미 바뀐 상태예요. 최신 상태로 다시 불러올게요.',

    problem: {
        FORMAT: 'PNG·JPG·WebP·GIF 만 올릴 수 있어요.',
        EMPTY: '빈 파일이에요.',
        TOO_LARGE: '{kb}KB 이하만 올릴 수 있어요.',
        UNREADABLE: '그림을 읽을 수 없어요. 다른 파일로 해 주세요.',
        RATIO: '정사각형(1:1) 그림만 올릴 수 있어요.',
        TOO_SMALL: '그림이 너무 작아요. {min}×{min}px 이상으로 올려 주세요.',
        ANIMATED_WEBP: '움직이는 WebP 는 올릴 수 없어요. 움직이는 그림은 GIF 로 올려 주세요.',
    },
};
