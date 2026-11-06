/**
 * Site notice bar (`components/shell/SiteNoticeBar.vue`) — same keys and order as `ko/siteNotice.ts`.
 */
export default {
    label: 'Site notice',
    badge: 'Notice',
    newWindow: '(opens in a new window)',
    prev: 'Previous notice',
    next: 'Next notice',
    close: 'Close notice bar',
    hideMenu: 'Hide for how long?',
    hide: {
        DAY: 'Hide for a day',
        WEEK: 'Hide for a week',
        MONTH: 'Hide for a month',
        FOREVER: 'Hide forever',
    },
    foreverTitle: 'Hide this notice forever?',
    foreverMessage: "You're responsible for any harm from not checking notices in time.\nThe bar comes back when a new notice is posted.",
    foreverConfirm: 'Hide forever',
};
