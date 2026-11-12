/**
 * 공지 띠(`components/shell/SiteNoticeBar.vue`) 문구 — 모든 화면의 메뉴 줄 아래 한 줄.
 * 키는 `siteNotice.무엇` 두 마디. `en/siteNotice.ts` 와 같은 키, 같은 차례. 공지 글 제목·직접 입력 문구는 서버 값이라 여기 두지 않는다.
 */
export default {
    label: '사이트 공지',
    badge: '공지',
    boardLink: '공지 게시판으로 가기',
    newWindow: '(새 창)',
    prev: '이전 공지',
    next: '다음 공지',
    close: '공지 띠 닫기',
    hideMenu: '언제까지 닫을까요?',
    hide: {
        DAY: '하루 동안 보지 않기',
        WEEK: '1주일 동안 보지 않기',
        MONTH: '1달 동안 보지 않기',
    },
};
