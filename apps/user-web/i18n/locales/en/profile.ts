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
    bioHelp: 'When your profile is public, it’s shown on your profile and below your posts.',
    bioTooLong: 'Your bio can be up to {max} characters.',

    // Profile visibility — public (default) / private. Nickname, avatar, grade and posts/comments on boards are always visible
    visibilityTitle: 'Profile visibility',
    visibilityPublic: 'Public',
    visibilityPublicHint: 'Anyone can see your bio, join date, activity counts, and your posts, comments and groups. Your bio appears below your posts.',
    visibilityPrivate: 'Private',
    visibilityPrivateHint: 'Others see only your nickname, avatar and grade. Your bio, join date, activity counts, and your posts, comments and groups lists are hidden.',
    visibilityNote: 'Even when private, your posts and comments on boards stay visible. Staff can see member information to run the site.',

    // Private profile (`/users/{id}`)
    privateTitle: 'This profile is private',
    privateText: '{nickname} hasn’t made their profile public. You can still see their posts and comments on boards.',
    privateMineTitle: 'Your profile is private',
    privateMineText: 'Others see only your nickname, avatar and grade. What’s below is visible only to you.',
    privateMineLink: 'Change visibility',
    privateBadge: 'Private',

    // Author bio on the post page (public profiles only)
    authorCardLabel: 'Author',
    authorCardProfile: 'View profile',
    authorCardBioEmpty: 'No bio yet.',
    authorCardPrivate: 'This profile is private.',
    authorCardMore: 'More',
    authorCardLess: 'Less',

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
    tabGroups: 'Clubs',
    postsErrorTitle: 'Couldn’t load posts',
    commentsErrorTitle: 'Couldn’t load comments',
    emptyPostsTitle: 'No posts yet',
    emptyPostsMine: 'Write your first post.',
    emptyPostsOther: "{nickname}'s posts will show up here.",
    emptyCommentsTitle: 'No comments yet',
    emptyCommentsText: 'Comments left on posts will show up here.',
    groupsErrorTitle: 'Couldn’t load clubs',
    emptyGroupsTitle: 'Not in any clubs yet',
    emptyGroupsMine: 'Find a club you’re interested in.',
    groupsNote: 'Members-only club posts aren’t shown on profiles',
    write: 'Write',
    hiddenPostsNote: 'Deleted, hidden, and members-only club posts aren’t shown',
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
