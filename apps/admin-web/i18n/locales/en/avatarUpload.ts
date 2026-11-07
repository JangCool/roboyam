/**
 * Avatar upload review (`/members/avatar-uploads`) copy — shared table/tab/reason text comes from `nicknameImage.*`. Same keys and order as `ko/avatarUpload.ts`.
 */
export default {
    title: 'Avatar upload review',
    subtitle: 'Review the images members applied for with an avatar upload ticket and approve or reject them. Approved images become the member’s profile avatar.',
    readOnlyHint: 'View only. Approving, rejecting and revoking need the nickname image manage permission.',
    loadFailed: 'Couldn’t load avatar applications',

    colImage: 'Image',
    colTicket: 'Ticket',
    motion: 'Animated',
    imageAlt: 'Avatar uploaded by {name}',
    ticket: {
        RESERVED: 'Reserved',
        USED: 'Used',
        RETURNED: 'Returned',
    },
    inUse: 'Current profile avatar',
    notInUse: 'Using another avatar',
    replaced: 'Replaced by a newer image',
    pausesNickname: 'Approving pauses their animated nickname image',

    zoomLabel: 'View avatar uploaded by {name}',
    zoomTitle: 'Avatar uploaded by {name}',
    whereProfile: 'Profile',
    whereView: 'Post page',
    whereList: 'Post list (still)',
    zoomNote: 'Top: original (checkerboard = transparent). Bottom: actual sizes on the public site. Animated GIFs show their first frame in post lists.',

    emptyPending: 'Nothing to review',
    emptyPendingHint: 'New applications made with an avatar upload ticket show up here.',
    emptyApproved: 'No approved avatars',
    emptyRejected: 'No rejected applications',
    emptyRevoked: 'No revoked avatars',

    approveLabel: 'Approve avatar of {name}',
    approved: 'Approved {name}’s avatar. It’s now their profile avatar.',
    rejectLabel: 'Reject avatar of {name}',
    rejectTitle: 'Reject avatar',
    rejectMessage: 'Reject {name}’s application? The reason is shown to the member and the reserved avatar upload ticket goes back to them.',
    rejected: 'Rejected {name}’s application',
    revokeLabel: 'Revoke avatar of {name}',
    revokeTitle: 'Revoke avatar',
    revokeMessage: 'Take down {name}’s uploaded avatar? If it’s in use they go back to their last still avatar, and the used ticket isn’t returned. The reason is shown to the member.',
    revoked: 'Revoked {name}’s avatar',
};
