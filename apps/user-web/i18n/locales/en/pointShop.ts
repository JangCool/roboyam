/**
 * Point shop — list (`/point-shop`), item detail (`/point-shop/{id}`), purchase confirmation, my items (`/me/items`). Same keys and order as `ko/pointShop.ts`.
 */
export default {
    seoDescription: 'Buy nickname colors, profile borders and nickname change tickets with {site} points.',

    // List
    myPoints: 'My points',
    restrictedTitle: "You can't change equipped items while suspended",
    restrictedText: "While your account is suspended you can't equip, unequip or change your avatar. What you have on stays as it is, and you can still see your items.",
    myItems: 'My items',
    kindFilter: 'Filter by type',
    kindNicknameColor: 'Nickname color',
    kindProfileBorder: 'Profile border',
    kindAvatar: 'Avatars',
    kindTicket: 'Tickets',
    loading: 'Loading the point shop',
    loadFailed: 'Couldn’t load the point shop',
    empty: 'Nothing for sale yet',
    emptyFiltered: 'Nothing of this type for sale yet',
    emptyText: 'New items will show up here.',
    listNote: 'Point shop items are bought with points only. If you haven’t used (equipped) an item within {days} days of buying it, you can get a refund in My items.',

    // Date — month and day in your time zone
    monthDay: '{month}/{day}',

    // Usage type
    usagePermanent: 'Permanent',
    usageConsumable: 'Consumable',
    usagePeriod: 'Limited time',
    usagePeriodDays: '{days} days',

    // Sale state · ownership
    saleFrom: 'From {date}',
    saleEnded: 'Sale ended',
    soldOut: 'Sold out',
    saleUntil: 'Until {date}',
    stockLeft: '{count} left',
    owned: 'Owned',
    ownedCount: '{count} owned',
    ownedUntil: 'Active until {date}',

    // Detail — why you can’t buy
    blockedUpcoming: 'Available from {date}.',
    blockedEnded: 'This item is no longer for sale.',
    blockedSoldOut: 'Sold out.',
    blockedOwned: 'You already own this item. You can equip it in My items.',
    blockedInsufficient: 'You need {points} more.',

    // Remaining time
    daysLeft: '{days} days left',
    periodOver: 'Expired',

    // 429 — server message + wait
    retryAfter: '{message} (try again in {wait})',

    // Detail
    notFoundTitle: 'Item not found',
    previewTitle: 'Preview — on your nickname',
    previewLight: 'Light mode',
    previewDark: 'Dark mode',
    previewCorrectedLight: 'Shown a little darker in light mode so it’s easy to read.',
    previewCorrectedDark: 'Shown a little lighter in dark mode so it’s easy to read.',
    previewCorrectedBoth: 'Adjusted slightly in light and dark mode so it’s easy to read.',
    previewInPost: 'How it looks in posts and comments',
    previewJustNow: 'just now',
    previewComment: 'Comments & lists',
    previewProfile: 'My profile',
    previewPostHead: 'Post author',
    borderName: 'Ring · {name}',
    borderWhere: 'The border shows on your profile and as the author on a post page. Lists, comments and messages show only the nickname color.',

    // Ticket — five lines instead of a preview
    ticketAboutTitle: 'About the nickname change ticket',
    ticketWhatLabel: 'What it does',
    ticketWhat: 'Lets you change your nickname one more time. One ticket, one change.',
    ticketWhenLabel: 'When',
    ticketWhen: 'Needed after you’ve used your one free change. While you still have the free change, tickets aren’t used.',
    ticketWhereLabel: 'Where',
    ticketWhere: 'Used when you change your nickname in Settings › Profile.',
    ticketPeriodLabel: 'Validity',
    ticketPeriod: 'No expiry — keep it until you use it.',
    ticketRefundLabel: 'Refunds',
    ticketRefund: 'If unused and within {days} days of buying, you can get a refund in My items. Used tickets can’t be refunded.',
    ticketFreeLeft: 'You still have your free change, so you can change your nickname without a ticket.',
    ticketFreeUsed: 'You’ve used your free change · tickets you have: {count}',

    // Purchase
    buy: 'Buy for {points}',
    useInProfile: 'Use in profile',
    goEquip: 'Go equip',
    confirmTitle: 'Buy this item?',
    confirmPrice: 'Price',
    confirmBalance: 'My points',
    confirmBalanceChange: 'Now {before} → after {after}',
    confirmFreeFirst: 'Earned points are used first.',
    confirmPeriodFromNow: 'Usable for {days} days from the moment you buy.',
    confirmPeriodContinue: '{days} days are added after your current period ends ({date}).',
    confirmNotice: 'Once used (equipped), it can’t be refunded · if unused, you can get a refund within {days} days. I’ve read this.',
    confirmBuy: 'Buy',
    bought: 'You bought {name}',

    // My items
    myItemsLoading: 'Loading your items',
    myItemsLoadFailed: 'Couldn’t load your items',
    myItemsEmpty: 'You don’t have any items yet',
    myItemsEmptyText: 'Buy nickname colors, profile borders and tickets in the point shop.',
    equippedTitle: 'Equipped now',
    slotEmpty: 'Nothing equipped',
    equippedNote: 'One item per slot. An item you’ve equipped even once can’t be refunded, even after unequipping (unused items can be refunded within {days} days).',
    equippedTag: 'Equipped',
    equip: 'Equip',
    unequip: 'Unequip',
    refund: 'Refund',
    // Refund requests while suspended (approved by an operator)
    refundRequest: 'Request refund',
    refundRequestConfirmTitle: 'Request a refund?',
    refundRequestConfirmText: '{name} — refunded after an operator reviews it ({points}, refundable until {date}). You can’t use this item while you wait.',
    refundRequested: 'Refund requested. We’ll let you know once an operator reviews it.',
    refundRequestPending: 'Refund requested',
    refundRequestPendingText: 'Waiting for operator review',
    refundRequestRejected: 'Refund declined',
    refundRequestRejectedReason: 'Your refund request was declined: {reason}',
    refundRequestRejectedPlain: 'Your refund request was declined.',
    ticketCount: '{count} owned',
    ticketOwned: 'Owned',
    ticketUsed: 'Used',
    boughtOn: 'Bought {date}',
    refundUntil: 'Refundable until {date}',
    usedNoRefund: 'Used · not refundable',
    refundExpired: 'Refund period over',
    revokedTitle: 'Refunded ({count})',
    equipConfirmTitle: 'Equip this item?',
    equipConfirmText: 'Once equipped, this item can’t be refunded — even if you unequip it.',
    equipped: 'Equipped {name}',
    unequipped: 'Unequipped',
    refundConfirmTitle: 'Get a refund?',
    refundConfirmText: '{name} — you’ll get {points} back (refundable until {date}). The item will be removed. Items you’ve equipped or used can’t be refunded.',
    refunded: 'Refunded {points} · now {balance}',

    // Profile border palette names (`UtilPointShop.BORDERS` `nameKey`)
    border_GOLD: 'Gold',
    border_SILVER: 'Silver',
    border_ROSE: 'Rose',
    border_OCEAN: 'Ocean',
    border_FOREST: 'Forest',
    border_NIGHT: 'Night sky',
    border_RAINBOW: 'Rainbow',
};
