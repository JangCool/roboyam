/**
 * Profile avatars — the Profile avatar section in settings, the point shop Avatars tab, and avatars in My items. Same keys and order as `ko/avatar.ts`.
 * Avatar names are the catalog’s Korean names (from the server — not here).
 */
export default {
    sectionTitle: 'Profile avatar',
    sectionHint: 'Saved as soon as you pick — separate from saving your nickname and bio below.',
    change: 'Change',
    loading: 'Loading avatars',
    loadFailed: 'Couldn’t load avatars',
    categoriesLabel: 'Avatar types',
    moreInShop: 'More avatars in the point shop',
    uploadOwn: 'Upload your own image',
    saved: 'Profile avatar changed',
    inUse: 'In use',
    refundableCaption: 'Refundable',
    notForSale: 'Not for sale',
    notForSaleText: 'This avatar isn’t for sale right now.',
    shopPickHint: 'Tap an avatar to see it large on your profile.',
    previewNote: 'Your avatar shows on your profile and as the author on post pages and post lists. An equipped border goes around it.',
    useNow: 'Use on my profile',
    useInProfile: 'Pick in profile settings',
    useInProfileShort: 'Use on profile',
    useConfirmTitle: 'Use this avatar on your profile?',
    useConfirmText: 'Once you use it on your profile, this avatar can’t be refunded — even if you switch to another one.',

    // Animated avatars — only one of an animated avatar or an animated nickname image (the later choice stays, `useAvatarChoice`)
    motionBadge: 'Animated',
    motionAria: 'Animated avatar {name}',
    motionConflictTitle: 'Switch to an animated avatar?',
    motionConflictText: 'You can use only one of an animated avatar and an animated nickname image. If you switch, your nickname image takes a break and your text nickname shows instead. The image isn’t deleted — turn it back on from the nickname image page without another review.',
    motionConflictConfirm: 'Use animated avatar',
    motionShopNote: 'It animates in posts, comments and profiles, and appears still in post lists. You can use only one of this and an animated nickname image (the later choice stays).',

    // Category names (`UtilAvatar.categoryKey`)
    category: {
        basic: 'Basic',
        hobby: 'Hobbies & characters',
        job: 'Jobs',
        sports: 'Sports',
        military: 'Military & police',
        transport: 'Air & sea',
        animal: 'Animals',
        office: 'Office',
        factory: 'Factory & field',
        care: 'Medical & public',
        service: 'Education & service',
        'arts-driver': 'Arts & driving',
        sports2: 'Sports 2',
        traditional: 'Traditional',
        motion: 'Animated avatars',
        upload: 'My uploaded avatar',
    },
};
