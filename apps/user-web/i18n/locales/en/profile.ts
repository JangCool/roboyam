/**
 * Profile settings (`/settings/profile`) — nickname and bio. Same keys and order as `ko/profile.ts`.
 */
export default {
    title: 'Profile',
    seoDescription: 'Edit the nickname and bio on your {site} profile.',
    loading: 'Loading your profile',
    loadFailed: 'Couldn’t load your profile',

    // Nickname change interval
    nicknameInterval: 'After you change your nickname, you can’t change it again for {days} days.',
    nicknameLocked: 'You can change your nickname again from {date}.',

    // Bio
    bioLabel: 'Bio',
    bioHint: 'Up to {max} characters',
    bioHelp: 'Shown on your page and profile.',
    bioTooLong: 'Your bio can be up to {max} characters.',
};
