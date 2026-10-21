/**
 * 관리자 셸(상단 바·사이드바·검색·알림/화면 설정 서랍·프로필·모바일 메뉴·로그아웃 확인)의 문구.
 * 키는 `shell.무엇` 두 마디 — 화면 이름을 넣지 않는다. `en/shell.ts` 와 같은 키, 같은 차례.
 */
export default {
    // 상단 바
    adminBadge: '관리자',
    homeLink: '{site} 관리자 홈',
    openMenu: '메뉴 열기',
    accountInfo: '로그인한 관리자',
    collapseSidebar: '사이드바 접기',
    expandSidebar: '사이드바 펼치기',
    toDark: '다크 모드로 전환',
    toLight: '라이트 모드로 전환',
    notifications: '알림',
    notificationsUnread: '알림, 안 읽은 알림 {count}건',
    displaySettings: '화면 설정',
    profileMenu: '내 계정 메뉴',
    myProfile: '내 정보',
    settings: '설정',
    backToMenu: '메뉴로 돌아가기',

    // 메뉴 검색
    searchPlaceholder: '메뉴 검색',
    searchOpen: '메뉴 검색 열기',
    searchClose: '메뉴 검색 닫기',
    searchEmpty: '찾는 메뉴가 없어요',

    // 메뉴
    mainNav: '관리 메뉴',
    settingsNav: '설정 메뉴',
    menu: '메뉴',

    // 접근성 건너뛰기
    skipToContent: '본문으로 건너뛰기',

    // 권한 없는 화면
    forbiddenTitle: '이 화면을 볼 권한이 없어요',
    forbiddenBody: '필요하면 최고 관리자에게 역할을 요청해 주세요.',
    goHome: '홈으로',

    // 역할 — 최고 관리자(ADMIN) · 운영진(STAFF)
    roleAdmin: '최고 관리자',
    roleStaff: '운영진',

    // 화면 설정 서랍
    settingsTitle: '화면 설정',
    settingsPrimary: '강조색',
    settingsBarTint: '상단 네비에 강조색 적용',
    barTintHint: '끄면 상단 네비는 색을 칠하지 않고 기본 면 색으로 보여요.',
    settingsTextSize: '글자 크기',
    textSmaller: '글자 작게',
    textLarger: '글자 크게',
    settingsDark: '다크 모드',
    settingsWidth: '본문 폭',
    widthFull: '꽉 채우기',
    widthBoxed: '가운데 제한',
    settingsHoverExpand: '사이드바 마우스 올리면 펼치기',
    hoverExpandHint: '접어 둔 사이드바에 마우스를 올리면 잠시 펼쳐요.',

    settingsSurface: '배경 색상',
    settingsDensity: '밀도',
    densityNormal: '보통',
    densityCompact: '조밀',
    settingsMenuPosition: '메뉴 위치',
    menuSidebar: '사이드바',
    menuTop: '상단',
    menuPositionMobileHint: '좁은 화면(모바일)에서는 이 설정과 상관없이 햄버거 메뉴를 써요.',
    settingsDrawerPosition: '서랍 열리는 쪽',
    settingsSidebarPosition: '사이드바 위치',
    sideLeft: '왼쪽',
    sideRight: '오른쪽',
    settingsNotify: '알림',
    notifyToast: '새 알림 토스트',
    notifyToastHint: '화면을 보고 있을 때 종 아래에 띄워요.',
    notifyOs: 'OS 알림',
    notifyOsHint: '창을 내려 두었거나 다른 창을 보고 있을 때 운영체제 알림으로 보내요.',
    notifyOsDenied: '브라우저가 이 사이트의 알림을 막고 있어요. 주소창의 사이트 설정에서 허용해 주세요.',
    notifyOsUnsupported: '이 브라우저는 OS 알림을 지원하지 않아요.',

    // 배경 색상 이름
    surfaceDefault: '기본',
    surfaceWhite: '흰색',
    surfaceSlate: '슬레이트',
    surfaceGray: '회색',
    surfaceZinc: '아연',
    surfaceNeutral: '중립',
    surfaceStone: '스톤',

    // 강조색 이름
    colorBlue: '파랑',
    colorIndigo: '남색',
    colorViolet: '보라',
    colorPink: '분홍',
    colorRed: '빨강',
    colorOrange: '주황',
    colorAmber: '황금색',
    colorGreen: '초록',
    colorTeal: '청록',
    colorCyan: '하늘색',

    // 로그아웃 확인
    logoutTitle: '로그아웃할까요?',
    logoutMessage: '이 기기에서 관리자 화면이 로그아웃돼요. 다시 들어오려면 로그인해야 해요.',
    loggedOut: '로그아웃했어요',
};
