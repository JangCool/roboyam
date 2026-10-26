/**
 * 프로필 설정 화면(`/settings/profile`)의 문구 — 닉네임·한 줄 소개. 닉네임 칸 이름·글자 수 안내는 가입 화면과 같은 `signup.nickname*`, 닉네임 규칙 오류는 `auth.nickname*`.
 * 키는 `profile.무엇` 두 마디. `en/profile.ts` 와 같은 키, 같은 차례.
 */
export default {
    title: '프로필',
    seoDescription: '{site} 프로필의 닉네임과 한 줄 소개를 고쳐요.',
    loading: '프로필을 불러오는 중',
    loadFailed: '프로필을 불러오지 못했어요',

    // 닉네임 변경 — 가입 뒤 1번 무료(날짜 대기 없음), 그다음부터는 포인트 상점의 닉네임 변경권 1장씩
    nicknameOnce: '닉네임은 가입 뒤 1번 무료로 바꿀 수 있어요. 그다음부터는 닉네임 변경권이 필요해요.',
    nicknameUsedUp: '무료 변경을 이미 썼어요. 바꾸려면 닉네임 변경권이 필요해요.',
    nicknameConfirmTitle: '닉네임을 바꿀까요?',
    nicknameConfirmText: '「{from}」에서 「{to}」(으)로 바뀌어요. 무료 변경은 이번 한 번이에요 — 다음부터는 닉네임 변경권이 필요해요.',
    nicknameConfirmButton: '바꾸기',
    // 무료를 썼고 변경권이 있다 — 칸이 열리고 바꾸면 1장을 쓴다
    nicknameTicketHelp: '변경권 {count}장 · 바꾸면 1장 써요.',
    nicknameTicketConfirmTitle: '변경권을 써서 바꿀까요?',
    nicknameTicketConfirmText: '「{from}」에서 「{to}」(으)로 바뀌어요. 닉네임 변경권 1장을 써요(남은 변경권 {count}장 → {after}장). 쓴 변경권은 환불할 수 없어요.',
    nicknameTicketConfirmButton: '변경권 쓰고 바꾸기',
    // 무료를 썼고 변경권이 없다 — 칸 잠금 + 포인트 상점 링크
    nicknameBuyTicket: '포인트 상점에서 변경권 사기({price})',

    // 정지 중 — 프로필은 읽기 전용
    restrictedTitle: '정지 중이라 프로필을 바꿀 수 없어요',
    restrictedText: '정지 중에는 닉네임과 한 줄 소개를 바꿀 수 없고, 보기는 계속 할 수 있어요.',
    restrictedLink: '제재 안내 보기',

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
    tabGroups: '소모임',
    postsErrorTitle: '작성글을 불러오지 못했어요',
    commentsErrorTitle: '댓글을 불러오지 못했어요',
    emptyPostsTitle: '아직 쓴 글이 없어요',
    emptyPostsMine: '첫 글을 남겨 보세요.',
    emptyPostsOther: '{nickname}님이 글을 쓰면 여기에 모여요.',
    emptyCommentsTitle: '아직 쓴 댓글이 없어요',
    emptyCommentsText: '글에 남긴 댓글이 여기에 모여요.',
    groupsErrorTitle: '소모임을 불러오지 못했어요',
    emptyGroupsTitle: '아직 가입한 소모임이 없어요',
    emptyGroupsMine: '관심 있는 모임을 찾아보세요.',
    groupsNote: '소모임의 비공개 글은 프로필에서 보이지 않아요',
    write: '글쓰기',
    hiddenPostsNote: '삭제·숨김 글과 비공개 소모임 글은 나오지 않아요',
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
