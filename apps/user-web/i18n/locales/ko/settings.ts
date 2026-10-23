/**
 * 설정 묶음(`/settings/*`)의 문구 — 메뉴 이름·안내, 떠나기 확인, 시간 표시 화면. 프로필은 `profile.*`, 알림 설정은 `notification.*`, 계정·로그인 연결은 `account.*`.
 * 표시 값 폼 안의 칸 이름은 공용 레이어의 `display.*`. 키는 `settings.무엇` 두 마디. `en/settings.ts` 와 같은 키, 같은 차례.
 */
export default {
    title: '설정',
    seoDescription: '{site} 계정 설정이에요.',

    // 메뉴
    profile: '프로필',
    profileHint: '닉네임·한 줄 소개',
    notifications: '알림 설정',
    notificationsHint: '어떤 알림을 받을지',
    display: '시간 표시',
    displayHint: '언어·시간대·시간과 날짜 모양',
    accountHint: '로그인 수단·소셜 계정 연결',
    nicknameImage: '닉네임 그림',
    nicknameImageHint: '닉네임 자리에 그림 · 단골 등급부터',
    adult: '성인 확인',
    adultHint: '19금 글을 보려면 출생연도를 확인해요',
    view: '보기',
    viewHint: '야간 모드·글자 크기·댓글 정렬',
    blocks: '차단 관리',
    blocksHint: '차단한 회원·단어',
    memos: '이용자 메모',
    memosHint: '나에게만 보이는 회원 메모',
    messages: '쪽지 수신',
    messagesHint: '쪽지를 받을지',

    // 저장하지 않고 떠날 때
    leaveTitle: '저장하지 않고 나갈까요?',
    leaveMessage: '바꾼 내용이 저장되지 않아요.',
    leaveConfirm: '나가기',

    // 시간 표시
    displaySeo: '{site}에서 시각을 어떤 언어·시간대·모양으로 볼지 정해요.',
    displayLoading: '시간 표시 설정을 불러오는 중',
    displayLoadFailed: '시간 표시 설정을 불러오지 못했어요',
    displayOwnTag: '내 설정',
    displayDefaultTag: '사이트 기본값',
    displayOwnText: '내가 고른 값으로 보여요.',
    displayDefaultText: '사이트 기본값을 따르고 있어요. 저장하면 내 설정이 돼요.',
    displayReset: '기본값으로 되돌리기',
    displayResetTitle: '사이트 기본값으로 되돌릴까요?',
    displayResetMessage: '내가 고른 값을 지우고 사이트 기본값을 따라요. 운영자가 기본값을 바꾸면 함께 바뀌어요.',
    displayResetDone: '사이트 기본값으로 되돌렸어요',
};
