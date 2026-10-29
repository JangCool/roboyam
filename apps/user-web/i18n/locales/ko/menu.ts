/**
 * 메뉴(왼쪽·오른쪽·상단 메뉴, 햄버거, 모바일 상단 탭)의 상태·단추·접근성 문구 — `components/shell/AppMenu*.vue`·`AppTop*.vue`, `utils/util-menu.ts`·`util-menu-messages.ts` 가 쓴다.
 * 메뉴 **항목·그룹의 글자는 여기 없다** — 서버가 주는 동적 이름이다(`nameEn` 규칙). `en/menu.ts` 와 같은 키, 같은 차례.
 * ⚠ 문구 안의 `@`·`|` 는 글자가 아니다(`{'@'}`·`{'|'}` 로 감싼다). `{title}` 같은 자리표시자는 값으로 채워진다.
 */
export default {
    // 접근성 이름(스크린리더)
    navLabel: '전체 메뉴',
    externalLabel: '(외부 링크)',
    openSubmenu: '「{name}」 하위 메뉴 열기',
    tabsLabel: '게시판 탭',
    loadingLabel: '메뉴를 불러오는 중',
    groupButtonLabel: '{title}, 항목 {count}개',

    // 상태·단추
    errorTitle: '메뉴를 불러오지 못했어요',
    fallbackNotice: '메뉴를 불러오지 못해 기본 메뉴를 보여 드려요',
    emptyTitle: '보여 드릴 메뉴가 없어요',
    emptyHome: '홈으로',
    tabsRetry: '메뉴 다시 불러오기',
    boardsEmpty: '보여 드릴 게시판이 없어요',
    investmentNotice: '투자 고지',

    // 기본 메뉴(서버 메뉴를 못 받았을 때)의 고정 항목
    home: '홈',
    myGroups: '내 {groups}',
    allGroups: '전체 {groups}',
    descLabel: '메뉴 설명',
    descPopular: '실시간 · 어제 · 이번 주, 반응이 가장 좋은 글을 모아 봐요',
    descLatest: '모든 게시판의 새 글을 올라온 순서대로 봐요',
    descBoard: '{name}의 새 소식을 모아 봐요',
};
