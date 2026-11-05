/**
 * Operator site account — links one site member account to each operator. Same keys and order as `ko/operatorMember.ts`.
 */
export default {
    tab: 'Site account',
    sectionTitle: 'Site account',
    intro: 'Link the one member account this operator uses on the site.',
    introPowers: 'The linked account can use every Roboticon pack, shows an “Operator” badge next to its nickname, and moderates every board within this operator’s admin permissions.',
    introStop: 'It stops right away when the operator is disabled, locked, or out of its period.',
    loadFailed: 'Couldn’t load the site account',
    readOnly: 'You can only view this — you don’t have permission to change the site account link.',
    superOnlySelf: 'Only the super admin can change their own site account.',

    // Not linked — member who signed up with the same email
    candidateTitle: 'Member signed up with this operator’s email ({email})',
    candidateNone: 'No member account uses the same email',
    candidateNoneHint: 'Have the operator sign up on the site with the same email, then link it.',
    fieldNickname: 'Nickname',
    fieldGrade: 'Grade',
    fieldJoinedAt: 'Joined',
    fieldBoardRoles: 'Board roles',
    boardRoles: 'Manager {manager} · Staff {staff}',
    fieldUserId: 'Member ID',
    fieldLinkedAt: 'Linked at',
    fieldLinkedBy: 'Linked by',
    ineligibleTitle: 'Can’t link right now',
    ineligible: {
        EMAIL_NOT_VERIFIED: 'This member hasn’t verified their email.',
        MEMBER_RESTRICTED: 'This member is suspended or withdrawing.',
        BUSINESS_ACCOUNT: 'Business accounts can’t be linked.',
        LINKED_TO_OTHER: 'Already linked to another operator, “{name}”.',
    },
    link: 'Link',
    linkLabel: 'Link {nickname}',
    linkTitle: 'Link this member account?',
    linkMessage: '“{nickname}” becomes this operator’s site account.',
    linkMessageBoards: '“{nickname}” becomes this operator’s site account. Its {count} board manager/staff appointments will all end.',
    linked: 'Site account linked',

    // Linked
    stateActive: 'Active on the site',
    statePaused: 'Paused',
    pausedTitle: 'Why it’s paused',
    paused: {
        OPERATOR_UNAVAILABLE: 'The operator account can’t be used (not approved, locked, expired, disabled, or out of period).',
        MEMBER_SUSPENDED: 'The member account is suspended.',
    },
    openMember: 'View in Members',
    unlink: 'Unlink',
    unlinkTitle: 'Unlink this account?',
    unlinkMessage: '“{nickname}” becomes a regular member right away and loses all Roboticon packs, the operator badge, and board moderation.',
    unlinked: 'Site account unlinked',

    // Powers
    powersTitle: 'What the linked account can do',
    powersPreview: 'Once linked, it can do this',
    powersPausedNote: 'It’s paused, so none of this works now. It comes back as soon as the cause is resolved.',
    power: {
        ROBOTICON_ALL: 'All Roboticon packs',
        OPERATOR_BADGE: '“Operator” badge next to nickname',
        POST_MODERATE: 'Hide and delete posts',
        COMMENT_DELETE: 'Delete comments',
        AUTHOR_SANCTION: 'Sanction authors',
        ACTION_READ: 'View moderation history',
        ACTION_RESTORE: 'Undo moderation',
    },
    powerGranted: 'Yes',
    powerDenied: 'No',
    powerNeeds: 'Requires: {permission}',
    powerAlways: 'Always, regardless of permissions',
    dailyLimitNone: 'No daily limit on board moderation.',
    powerScopeNote: 'Notices, pinned posts, group posts, and posts or comments by other operators’ site accounts can’t be moderated.',

    // Operator list badge
    listTag: 'Site account',
    listTagTitle: 'Site account: {nickname}',
    listTagPausedTitle: 'Site account (paused): {nickname}',

    // Member detail
    memberTag: 'Operator account · {name}',
    memberTagPaused: 'Paused',
    memberNote: 'This member account is linked to an operator. It can’t withdraw while linked.',
};
