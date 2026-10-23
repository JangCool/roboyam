/**
 * 프로필 설정 화면(`/settings/profile`)의 문구 — 닉네임·한 줄 소개. 닉네임 칸 이름·글자 수 안내는 가입 화면과 같은 `signup.nickname*`, 닉네임 규칙 오류는 `auth.nickname*`.
 * 키는 `profile.무엇` 두 마디. `en/profile.ts` 와 같은 키, 같은 차례.
 */
export default {
    title: '프로필',
    seoDescription: '{site} 프로필의 닉네임과 한 줄 소개를 고쳐요.',
    loading: '프로필을 불러오는 중',
    loadFailed: '프로필을 불러오지 못했어요',

    // 닉네임 변경 주기
    nicknameInterval: '닉네임을 바꾸면 {days}일 동안 다시 바꿀 수 없어요.',
    nicknameLocked: '닉네임은 {date}부터 다시 바꿀 수 있어요.',

    // 한 줄 소개
    bioLabel: '한 줄 소개',
    bioHint: '{max}자까지',
    bioHelp: '내 정보와 프로필에 보여요.',
    bioTooLong: '한 줄 소개는 {max}자까지 쓸 수 있어요.',

    // 공개 프로필(`/users/{id}`)
    publicTitle: '프로필',
    publicSeoDescription: '{nickname}님의 프로필이에요.',
    publicLoading: '프로필을 불러오는 중',
    publicLoadFailed: '프로필을 불러오지 못했어요',
    notFoundTitle: '탈퇴했거나 없는 회원이에요',
    notFoundText: '이 회원의 프로필은 볼 수 없어요.',
    goBack: '이전 화면으로',
    goHome: '홈으로',
    joined: '가입 {date}',
    statPosts: '글',
    statComments: '댓글',
    statLikes: '받은 추천',
    tabsLabel: '프로필 탭',
    tabPosts: '작성글',
    tabComments: '댓글',
    postsErrorTitle: '작성글을 불러오지 못했어요',
    commentsErrorTitle: '댓글을 불러오지 못했어요',
    emptyPostsTitle: '아직 쓴 글이 없어요',
    emptyPostsMine: '첫 글을 남겨 보세요.',
    emptyPostsOther: '{nickname}님이 글을 쓰면 여기에 모여요.',
    emptyCommentsTitle: '아직 쓴 댓글이 없어요',
    emptyCommentsText: '글에 남긴 댓글이 여기에 모여요.',
    write: '글쓰기',
    hiddenPostsNote: '삭제·숨김 글은 나오지 않아요',
    replyTag: '답글',
    commentMeta: '{board} · {title}',
    feedLoading: '{label} 목록을 불러오는 중이에요.',
    feedFailed: '{label} 목록을 불러오지 못했어요.',
    feedCount: '{label} {count}개를 보여 주고 있어요.',
    feedCountMore: '{label} {count}개를 보여 주고 있어요. 더 있어요.',
    feedMore: '더보기',
    progressLabel: '포인트와 등급',
    pointsLink: '보유 포인트 {points}, 포인트 화면으로 이동',
    pointsLinkPlain: '포인트 화면으로 이동',
    pointsUnknown: '확인할 수 없어요',
    gradeLoading: '등급 진행도를 불러오는 중',
};
