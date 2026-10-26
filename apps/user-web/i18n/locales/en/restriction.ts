/**
 * Feature restriction notice — used by `components/sanction/FeatureRestrictionNotice.vue` and `utils/util-feature-restriction-messages.ts`. Same keys and order as `ko/restriction.ts`.
 */
export default {
    withPeriod: '{title} · {period}',
    until: 'until {month}/{day} {clock}',
    untilYear: 'until {month}/{day}/{year} {clock}',
    untilLifted: 'until lifted',
    reason: 'Reason: {reason}',

    title_POINT_FREEZE: 'Your points are frozen',
    title_GROUP_BUY_BAN: 'Joining group buys is restricted',
    title_CHARGE_BAN: 'Charging points is restricted',

    type_POINT_FREEZE: 'Point freeze',
    type_GROUP_BUY_BAN: 'Group-buy ban',
    type_CHARGE_BAN: 'Charge ban',

    allowed_POINT_FREEZE: 'You can still request refunds of charged points.',
    allowed_GROUP_BUY_BAN: 'You can still track, cancel or return orders you’ve already paid for.',
    allowed_CHARGE_BAN: 'You can still use your points and request refunds.',
};
