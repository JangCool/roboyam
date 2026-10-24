/**
 * Terms copy — public terms screens (`/terms`, `/privacy`) and the change notice bar. Same keys and order as `ko/terms.ts`.
 */
export default {
    // ── Text screen ──
    loadError: "Couldn't load the terms",
    versionLine: 'Version {version} · effective {date}',
    changeNote: 'Summary of changes',
    emptyTitle: {
        TERMS: 'No Terms of Service in effect yet',
        PRIVACY: 'No Privacy Policy in effect yet',
    },
    upcomingLabel: 'Upcoming change',
    upcomingBox: {
        TERMS: 'Updated Terms of Service take effect on {date}',
        PRIVACY: 'An updated Privacy Policy takes effect on {date}',
    },
    viewUpcoming: 'View the updated version',
    viewingUpcoming: 'This version takes effect on {date}.',
    viewingPast: 'This is a past version.',
    viewCurrent: 'View the current version',
    versionMissing: "We couldn't find that version",
    historyTitle: 'Version history',
    status: {
        CURRENT: 'Current',
        UPCOMING: 'Upcoming',
        PAST: 'Past',
    },

    // ── Change notice bar ──
    noticeLabel: 'Terms change notice',
    noticeUpcoming: {
        TERMS: 'Terms of Service are changing · effective {date}',
        PRIVACY: 'Privacy Policy is changing · effective {date}',
    },
    noticeRecent: {
        TERMS: 'Terms of Service have changed · effective {date}',
        PRIVACY: 'Privacy Policy has changed · effective {date}',
    },
    noticeView: 'View',
    noticeClose: 'Close the {name} change notice',
};
