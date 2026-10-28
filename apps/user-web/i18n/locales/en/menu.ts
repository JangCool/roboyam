/**
 * Menu (left, right and top menus, hamburger, mobile top tabs) states, buttons and accessibility texts. Same keys and order as `ko/menu.ts`.
 * ⚠ `@` and `|` inside a message are not plain characters (wrap them as `{'@'}` · `{'|'}`).
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
    descLabel: 'About this menu',
    descPopular: 'The most-loved posts — live, yesterday and this week',
    descLatest: 'New posts from every board, newest first',
    descBoard: 'The latest from {name}',
};
