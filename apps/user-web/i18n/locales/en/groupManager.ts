/**
 * Club manager copy (stage 3) — same keys and order as `ko/groupManager.ts`.
 */
export default {
    // ── The five tasks (fixed order) ──
    perm: {
        APPLICATIONS: 'Handle join requests',
        KICK: 'Remove members',
        RECRUITING: 'Close / reopen recruiting',
        EVENTS: 'Manage events',
        UNPUBLISH: 'Publish/unpublish posts',
    },
    permHint: {
        APPLICATIONS: 'Review and approve or decline requests',
        KICK: 'Remove regular members',
        RECRUITING: 'Close and reopen recruiting',
        EVENTS: 'Create, edit and cancel club events',
        UNPUBLISH: 'Handle publishing members’ posts and make them private again',
    },
    permNote: {
        APPLICATIONS: 'They can see applicants’ notes and whether someone was removed before. Give this to members you trust.',
        KICK: 'Only regular members can be removed, and the leader is notified.',
    },
    summarySeparator: ' · ',
    toolsPaused: 'Tools are unavailable right now',

    // ── Filter & headings ──
    filterLabel: 'Show members',
    filterAll: 'All {n}',
    filterManagers: 'Managers {count}/{limit}',
    heading: 'Managers {count}/{limit}',
    appointed: 'Appointed {y}.{mo}',
    listError: 'Couldn’t load managers',
    emptyTitle: 'No managers yet',
    emptyText: 'Appoint members as managers to share tasks like handling join requests.',
    appointOff: 'New appointments are turned off right now',
    limitButton: 'Change limit',

    // ── ⋯ sheet ──
    menuAppoint: 'Appoint as manager',
    menuEdit: 'Change tasks',
    menuRemove: 'Remove as manager',
    kickNotice: 'The leader is also notified of the removal.',

    // ── Task sheet (appoint / change tasks) ──
    appointTitle: 'Appoint {name} as manager',
    editTitle: 'Tasks for {name}',
    appointDescription: 'Choose the tasks. Managers can only do what’s checked.',
    editDescription: 'Managers can only do what’s checked. They’ll be notified of changes.',
    pickOne: 'Choose at least one',
    clearedText: 'To give no tasks at all, remove them as manager',
    clearedAction: 'Remove as manager',
    appointConfirm: 'Appoint',
    fixedTitle: 'Managers can’t',
    fixedInfo: 'Edit club info or chat links',
    fixedManagers: 'Appoint managers, change tasks or set the limit',
    fixedLeader: 'Hand over leadership or dissolve the club',
    slots: 'Managers {count}/{limit} → {next}/{limit} after appointing',
    slotsFull: 'Up to {limit} managers. Remove one or raise the limit first',

    // ── Results ──
    appointedToast: 'Appointed {name} as manager',
    changedToast: 'Changed tasks for {name}',
    removedToast: 'Removed {name} as manager',
    limitSavedToast: 'Manager limit set to {n}',
    notManagerAnymore: 'They’re no longer a manager',
    resignedToast: 'You stepped down as manager',
    removeFailed: 'Couldn’t remove the manager',
    removeTitle: 'Remove {name} as manager?',
    removeMessage: 'They’ll become a regular member and lose their tasks. {name} will be notified.',
    removeConfirm: 'Remove',

    // ── Limit sheet ──
    limitTitle: 'Manager limit',
    limitDescription: 'The leader decides how many managers the club can have (1–{max}).',
    limitValue: '{n}',
    limitNow: 'Currently {n}',
    limitFloor: 'There are {n} managers now, so the limit can’t go below {n}',
    limitCeiling: 'Up to {max} managers',
    limitUp: 'Increase manager limit',
    limitDown: 'Decrease manager limit',

    // ── Manage home ──
    rowManagers: 'Managers',
    rowManagersHint: 'Set tasks and the limit',
    rowValue: '{count}/{limit}',
    headRole: 'You’re a manager',
    headGranted: 'Tasks: {list}',
    lockedHint: 'Not assigned by the leader',
    notDelegatedTitle: 'The leader hasn’t assigned this to you',
    lockedText: '“{name}” hasn’t been assigned to you. Ask the leader if you need it.',
    notDelegatedText: '“{name}” hasn’t been assigned to you.',
    lockedOk: 'OK',
    leaderOnlyBox: 'Only the leader can edit club info and chat links, appoint managers or change their tasks, and dissolve the club.',
    roleSection: 'My role',
    resignRow: 'Step down as manager',
    resignHint: 'You’ll become a regular member',
    backToManage: 'Back to manage',
    revoked: 'The leader hasn’t assigned this task to you.',
    demoted: 'You’re no longer a manager.',

    // ── Resign sheet ──
    resignTitle: 'Step down as manager?',
    resignDescription: 'You’ll become a regular member and lose your tasks. The leader will be notified. Only the leader can appoint you again.',
    resignConfirm: 'Step down',

    // ── Short reasons on paused buttons ──
    blockedEmail: 'Available after you verify your email',
    blockedRestricted: 'Unavailable while your account is restricted',
    blockedBusiness: 'Not available for business accounts',
};
