/**
 * 팝업(`components/shell/SitePopupLayer.vue`) — 사이트에 들어오면 화면 가운데 뜨는 이벤트·프로모션 창.
 * 메뉴 줄 아래 한 줄인 공지 띠(`siteNotice`)와 다르다. `en/popup.ts` 와 같은 키, 같은 차례.
 */
export default {
    label: '이벤트·안내 팝업',
    close: '닫기',
    prev: '이전 팝업',
    next: '다음 팝업',
    buttonDefault: '자세히 보기',
    newWindow: '(새 창)',
    hide: {
        TODAY: '오늘 하루 보지 않기',
        WEEK: '1주일 동안 보지 않기',
        NEVER: '다시 보지 않기',
    },
};
