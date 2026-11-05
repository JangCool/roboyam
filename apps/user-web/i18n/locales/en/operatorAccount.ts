/**
 * Operator member account (a member account linked to an admin-app operator): the read-only card on the account screen (`/settings/account`) and the blocked notice on the withdrawal screen.
 * Same keys and order as `ko/operatorAccount.ts`.
 */
export default {
    /* Account screen card */
    sectionTitle: 'Operator link',
    cardTitle: 'This member account is linked to an operator account',
    cardOperator: 'Operator {name}',
    powersLabel: 'What this account can do',
    powerRobotikon: 'Use every Robotikon pack',
    powerBadge: 'A “{badge}” badge appears next to your nickname',
    powerBoards: 'Moderate every board like a manager · no daily limit',
    unlinkNote: 'Unlinking is done in the admin app.',

    /* Withdrawal screen */
    withdrawBlockedTitle: "You can't delete your account while it's linked to an operator account.",
    withdrawBlockedText: 'Ask an administrator to unlink it.',
};
