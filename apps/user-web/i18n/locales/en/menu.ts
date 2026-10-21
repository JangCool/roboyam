/**
 * 메뉴(왼쪽·오른쪽·상단 메뉴, 햄버거, 모바일 상단 탭)의 상태·단추·접근성 문구 — `ko/menu.ts` 와 같은 키, 같은 차례.
 * ⚠ 문구 안의 `@`·`|` 는 글자가 아니다(`{'@'}`·`{'|'}` 로 감싼다).
 */
export default {
    // 접근성 이름(스크린리더)
    navLabel: 'Main menu',
    externalLabel: '(external link)',
    tabsLabel: 'Board tabs',
    loadingLabel: 'Loading menu',
    groupButtonLabel: '{title}, items: {count}',

    // 상태·단추
    errorTitle: "Couldn't load the menu",
    fallbackNotice: "Couldn't load the menu, so the default menu is shown",
    emptyTitle: 'No menu to show',
    emptyHome: 'Go home',
    tabsRetry: 'Reload menu',
    boardsEmpty: 'No boards to show',
    investmentNotice: 'Investment notice',

    // 기본 메뉴(서버 메뉴를 못 받았을 때)의 고정 항목
    home: 'Home',
    myGroups: 'My {groups}',
    allGroups: 'All {groups}',
};
