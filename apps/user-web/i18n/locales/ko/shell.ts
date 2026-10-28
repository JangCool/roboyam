/**
 * 앱 셸(상단 바·햄버거 메뉴·하단 탭·푸터·글쓰기 버튼·로그아웃 확인)과 공통 부품(등급 뱃지·오류 상태)의 문구.
 * 키는 `shell.무엇` 두 마디 — 화면 이름을 넣지 않는다. `en/shell.ts` 와 같은 키, 같은 차례.
 * ⚠ 문구 안의 `@`·`|` 는 글자가 아니다(`{'@'}`·`{'|'}` 로 감싼다). `{site}` 같은 자리표시자는 값으로 채워진다.
 */
export default {
    // 상단 바
    openMenu: '메뉴 열기',
    homeLink: '{site} 홈',
    accountNav: '계정',
    accountMenu: '내 계정 메뉴',
    // 테마 셸의 상단 메뉴(다음식 LNB · 네이버식 윗줄 링크)
    subMenuLabel: '{title} 메뉴',
    quickLinks: '바로가기',
    // 가로 메뉴 줄이 넘칠 때 양끝 화살표 단추(`AppScrollRow`)
    scrollMenuLeft: '메뉴 왼쪽으로 넘기기',
    scrollMenuRight: '메뉴 오른쪽으로 넘기기',

    // 메뉴 줄
    menu: '메뉴',
    myInfo: '내 정보',
    messageBox: '쪽지함',
    messageBoxUnread: '쪽지함 · 안 읽음 {count}',
    adminApp: '관리자 앱',
    adminAppNewTab: '관리자 앱 (새 탭에서 열려요)',

    // 로그인 전 안내(햄버거 메뉴)
    loginInvite: '로그인하고 참여해 보세요',
    loginInviteHint: '글·댓글은 로그인 후 쓸 수 있어요',

    // 접근성 건너뛰기
    skipToContent: '본문으로 건너뛰기',

    // 하단 탭
    bottomTabs: '하단 탭',
    tabHome: '홈',
    tabBoards: '게시판',
    tabGroups: '소모임', // 서비스 이름(GROUPS)을 못 읽을 때의 자리 — 보통은 운영자가 정한 이름이 보인다
    tabNotifications: '알림',

    // 글쓰기 버튼
    write: '글쓰기',

    // 로그아웃 확인
    logoutTitle: '로그아웃할까요?',
    logoutMessage: '이 기기에서 로그아웃돼요. 다시 로그인하면 이어서 쓸 수 있어요.',
    loggedOut: '로그아웃했어요',

    // 등급 뱃지(서버가 이름을 주지 않았을 때)
    gradeSeedling: '새싹',
    gradeRegular: '일반',
    gradeLoyal: '단골',
    gradeVeteran: '베테랑',
};
