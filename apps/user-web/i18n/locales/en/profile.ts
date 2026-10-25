/**
 * Profile settings (`/settings/profile`) — nickname and bio. Same keys and order as `ko/profile.ts`.
 */
export default {
    title: 'Profile',
    seoDescription: 'Edit the nickname and bio on your {site} profile.',
    loading: 'Loading your profile',
    loadFailed: 'Couldn’t load your profile',

    // Nickname changes — only once after signing up (no waiting period); none after that
    nicknameOnce: 'You can change your nickname only once after signing up.',
    nicknameUsedUp: 'You’ve already changed your nickname and can’t change it again.',
    nicknameConfirmTitle: 'Change your nickname?',
    nicknameConfirmText: '“{from}” becomes “{to}”. After this change, you won’t be able to change your nickname again.',
    nicknameConfirmButton: 'Change',

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
