/**
 * commerce texts — commerce settings (`/settings/commerce`). Same keys and order as `ko/commerce.ts`.
 * Policy source: `docs/domain/commerce.md` 12-4 (rate limits on money-related requests).
 */
export default {
    // ── Commerce settings (/settings/commerce) ─
    settingsTitle: 'Commerce settings',
    settingsSubtitle: 'Settings shared by sales and payments. Only request limits are here for now; the sales switch, business details and more will be added to this screen later. Changes are recorded in the audit log.',
    loadFailed: 'Could not load commerce settings.',

    // ── Request limits section ─
    limitsSection: 'Request limits',
    limitsLead: 'How many money-related requests one member can send per minute. Over the limit, the member is told how many seconds to wait. Set 0 for no limit.',
    limitsNotReady: 'Group buys, top-ups, shop purchases and refunds are not available yet, so these values are only saved for now. They apply as soon as those features arrive.',
    actionName: {
        GROUPBUY_JOIN: 'Join a group buy',
        POINT_CHARGE: 'Point top-up request',
        POINT_SHOP_PURCHASE: 'Point shop purchase',
        REFUND_REQUEST: 'Refund request',
    },
    actionDesc: {
        GROUPBUY_JOIN: 'Joining a group buy and holding a quantity',
        POINT_CHARGE: 'Opening a payment to top up points',
        POINT_SHOP_PURCHASE: 'Buying an item in the point shop',
        REFUND_REQUEST: 'Asking for a refund of an item, a top-up or a return',
    },
    perMinute: 'per minute',
    unlimited: 'No limit',
    changedFrom: 'Changed from saved value {value}',
    defaultValue: 'Default {value}',
    useDefault: 'Use default',
    limitRequired: 'Enter a value.',
    limitNotInteger: 'Enter a whole number.',
    limitOutOfRange: 'Enter a number from 0 to 100.',
    noteApply: 'Once saved, every server picks this up within 15 seconds.',
    noteWindow: 'Requests are counted per calendar minute, so up to twice the limit can briefly get through around a minute boundary. This is a first line of defense against abuse; the real limits (monthly top-up cap, per-member quantity, and so on) are enforced by each feature’s own rules.',
    lastSaved: 'Last saved: {at} · {by}',
    neverSaved: 'Never saved — using defaults.',

    // ── Save ─
    limitsSaved: 'Request limits saved.',
    saveFieldsRejected: 'Some fields were not accepted. Check the messages under them.',
    leaveTitle: 'You have unsaved changes',
    leaveMessage: 'If you leave, your changes will be lost.',
    leaveConfirm: 'Discard and leave',
    leaveCancel: 'Keep editing',
};
