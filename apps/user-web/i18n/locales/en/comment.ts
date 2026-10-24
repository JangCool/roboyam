/**
 * Comment texts. Same keys and order as `ko/comment.ts`.
 */
export default {
    title: 'Comments',
    loading: 'Loading comments',
    loadFailed: 'Couldn’t load comments',
    sortLabel: 'Sort comments',
    sortOldest: 'Oldest',
    sortLatest: 'Newest',
    bestTitle: 'Top comments',
    bestBadge: 'BEST',
    emptyTitle: 'No comments yet',
    emptyText: 'Be the first to comment.',

    inputLabel: 'Write a comment',
    placeholder: 'Leave a comment',
    submit: 'Post',
    replySubmit: 'Reply',
    replyingTo: 'Replying to {name}',
    required: 'Enter a comment.',
    tooLong: 'Comments can be up to {max} characters.',
    posted: 'Comment posted.',
    updated: 'Comment updated.',

    reply: 'Reply',
    edited: '(edited)',
    deleted: 'This comment was deleted',
    hidden: 'Hidden after reports',
    hiddenByStaff: 'Hidden by staff',
    likeLabel: '{n} likes',
    alreadyLiked: 'You already liked this comment.',

    editTitle: 'Edit comment',
    deleteTitle: 'Delete this comment?',
    deleteMessage: 'Deleted comments can’t be restored.',
    deletedToast: 'Comment deleted.',
    passwordDescription: 'Enter the password you set when commenting.',

    // ── Highlight debate (pro and con opinions) ──
    stance: {
        PRO: 'Pro',
        CON: 'Con',
    },
    kindLabel: 'Comment type',
    kind: {
        ALL: 'All',
        GENERAL: 'General',
        PRO: 'Pro',
        CON: 'Con',
    },
    kindEmpty: 'No comments of this type yet',
    debateClosed: 'The debate has ended. Existing opinions are read-only.',
    debateGuestHint: 'Only signed-in members can post pro or con opinions.',
    stancePickLabel: 'Which side are you on?',
    stanceGeneral: 'General',
    stancePickHint: 'Your first pro or con comment sets your side. You can switch later, and your existing opinions switch with you.',
    myStance: 'My side',
    myStanceHint: 'Your comments and replies on this post get this badge.',
    /** Button — names the side you switch to (key = new side) */
    changeStanceTo: {
        PRO: 'Switch to pro',
        CON: 'Switch to con',
    },
    /** Confirm title — key = new side */
    changeStanceTitle: {
        PRO: 'Switch from con → pro?',
        CON: 'Switch from pro → con?',
    },
    /** Confirm body when you have opinions to move (`n` = `debate.myOpinionCount`) */
    changeStanceMoves: {
        PRO: 'Your {n} con opinion(s) so far will become pro too.',
        CON: 'Your {n} pro opinion(s) so far will become con too.',
    },
    /** Confirm body when there is nothing to move */
    changeStanceFuture: {
        PRO: 'Opinions you write from now on get the pro badge.',
        CON: 'Opinions you write from now on get the con badge.',
    },
    stanceChanged: {
        PRO: 'Switched to pro.',
        CON: 'Switched to con.',
    },
    /** Success toast — `n` = response `moved` */
    stanceChangedMoved: {
        PRO: 'Switched to pro. {n} of your opinions moved too.',
        CON: 'Switched to con. {n} of your opinions moved too.',
    },
    mention: 'Mention a member',
    mentionSearch: 'Find by nickname',
    mentionHelp: 'Pick a member who commented on this post to reply to their latest comment.',
    mentionEmpty: 'No members to pick.',
    mentionTarget: "Replying to {'@'}{name}",
    mentionClear: 'Clear',
};
