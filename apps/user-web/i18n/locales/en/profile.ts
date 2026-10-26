/**
 * Profile settings (`/settings/profile`) — nickname and bio. Same keys and order as `ko/profile.ts`.
 */
export default {
    title: 'Profile',
    seoDescription: 'Edit the nickname and bio on your {site} profile.',
    loading: 'Loading your profile',
    loadFailed: 'Couldn’t load your profile',

    // Nickname changes — one free change after signing up (no waiting period), then one nickname change ticket from the point shop per change
    nicknameOnce: 'You can change your nickname once for free after signing up. After that, you need a nickname change ticket.',
    nicknameUsedUp: 'You’ve used your free change. You need a nickname change ticket to change it.',
    nicknameConfirmTitle: 'Change your nickname?',
    nicknameConfirmText: '“{from}” becomes “{to}”. This is your only free change — after this, you’ll need a nickname change ticket.',
    nicknameConfirmButton: 'Change',
    // Free change used, has tickets — the field opens and a change uses one ticket
    nicknameTicketHelp: '{count} ticket(s) · a change uses 1.',
    nicknameTicketConfirmTitle: 'Use a ticket to change?',
    nicknameTicketConfirmText: '“{from}” becomes “{to}”. This uses 1 nickname change ticket ({count} → {after} left). Used tickets can’t be refunded.',
    nicknameTicketConfirmButton: 'Use ticket and change',
    // Free change used, no tickets — field locked + point shop link
    nicknameBuyTicket: 'Buy a ticket in the point shop ({price})',

    // Suspended — the profile is read-only
    restrictedTitle: 'You can’t edit your profile while suspended',
    restrictedText: 'While your account is suspended, you can’t change your nickname or bio, but you can still browse.',
    restrictedLink: 'See suspension details',

    // Bio
    bioLabel: 'Bio',
    bioHint: 'Up to {max} characters',
    bioHelp: 'Shown on your page and profile.',
    bioTooLong: 'Your bio can be up to {max} characters.',

    // Public profile (`/users/{id}`)
    publicTitle: 'Profile',
    publicSeoDescription: "{nickname}'s profile.",
    publicLoading: 'Loading the profile',
    publicLoadFailed: 'Couldn’t load the profile',
    notFoundTitle: 'This member left or doesn’t exist',
    notFoundText: 'This profile isn’t available.',
    goBack: 'Go back',
    goHome: 'Go home',
    joined: 'Joined {date}',
    statPosts: 'Posts',
    statComments: 'Comments',
    statLikes: 'Likes received',
    tabsLabel: 'Profile tabs',
    tabPosts: 'Posts',
    tabComments: 'Comments',
    postsErrorTitle: 'Couldn’t load posts',
    commentsErrorTitle: 'Couldn’t load comments',
    emptyPostsTitle: 'No posts yet',
    emptyPostsMine: 'Write your first post.',
    emptyPostsOther: "{nickname}'s posts will show up here.",
    emptyCommentsTitle: 'No comments yet',
    emptyCommentsText: 'Comments left on posts will show up here.',
    write: 'Write',
    hiddenPostsNote: 'Deleted and hidden posts aren’t shown',
    replyTag: 'Reply',
    commentMeta: '{board} · {title}',
    feedLoading: 'Loading {label}.',
    feedFailed: 'Couldn’t load {label}.',
    feedCount: 'Showing {count} {label}.',
    feedCountMore: 'Showing {count} {label}. More available.',
    feedMore: 'Load more',
    progressLabel: 'Points and grade',
    pointsLink: 'Points {points}, go to the points page',
    pointsLinkPlain: 'Go to the points page',
    pointsUnknown: 'Unavailable',
    gradeLoading: 'Loading grade progress',
};
