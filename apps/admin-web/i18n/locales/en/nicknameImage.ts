/**
 * Nickname image review (`/members/nickname-images`). Same keys and order as `ko/nicknameImage.ts`.
 */
export default {
    title: 'Nickname image review',
    subtitle: 'Review nickname images requested by members. Approved images replace the member’s nickname text.',

    // Tabs
    tabsLabel: 'Request status',
    status: {
        PENDING: 'Pending',
        APPROVED: 'Approved',
        REJECTED: 'Rejected',
        REVOKED: 'Revoked',
    },

    // Search
    searchPlaceholder: 'Search nickname',
    clearSearch: 'Clear search',
    readOnlyHint: 'You can only view. Approving, rejecting and revoking need the nickname image manage permission.',

    // Table
    colImage: 'Image',
    colMember: 'Member',
    colFile: 'File',
    colCreatedAt: 'Requested',
    colReview: 'Review',
    fileLine: '{format} · {size}KB',
    sizeLine: '{width}×{height}',
    frames: '{count} frames',
    fileGone: 'The image file was deleted',
    still: 'Still image',
    imageAlt: 'Nickname image of {name}',
    zoom: 'Zoom',
    zoomLabel: 'Enlarge the nickname image of {name}',
    zoomTitle: 'Nickname image of {name}',
    zoomNote: 'Shown at {zoom}× the actual 240×60 size. The checkerboard marks transparent areas.',
    reviewedBy: '{time} · {name}',
    reasonLabel: 'Reason',
    loadFailed: 'Couldn’t load nickname image requests',

    // Empty states
    emptyPending: 'Nothing to review',
    emptyPendingHint: 'New requests show up here and send an operator notification.',
    emptyApproved: 'No approved images',
    emptyRejected: 'No rejected requests',
    emptyRevoked: 'No revoked images',

    // Actions
    approve: 'Approve',
    approveLabel: 'Approve the nickname image of {name}',
    approved: 'Approved the nickname image of {name}',
    reject: 'Reject',
    rejectLabel: 'Reject the nickname image of {name}',
    rejectTitle: 'Reject nickname image',
    rejectMessage: 'Reject the request from {name}? The member will see the reason.',
    rejected: 'Rejected the request from {name}',
    revoke: 'Revoke',
    revokeLabel: 'Revoke the nickname image of {name}',
    revokeTitle: 'Revoke nickname image',
    revokeMessage: 'Take down the image {name} is using? Their nickname goes back to text and they will see the reason.',
    revoked: 'Revoked the nickname image of {name}',
    reasonPlaceholder: 'Reason shown to the member',
    reasonHelp: '1–{max} characters',
    reasonRequired: 'Enter a reason.',
    reasonTooLong: 'Use {max} characters or fewer.',
};
