/**
 * Names, descriptions and recipient text of notification events — **mock only**. For real, the server resolves them in the request language from `messages_ko/en.properties` (`notification.event.<code>.name` and `.description`).
 * The mock (`composables/notification/api/mock/notification-event-mock.ts`) resolves them from this table in the current language to imitate the server response. When the server is connected, delete this file, `ko/notificationEvent.ts` and the registration lines in both `index.ts`.
 * Keys are `notificationEvent.<event code>.name|description|recipient`. Same keys and order as `ko/notificationEvent.ts`. Event definitions: `docs/features/notification-contract.md`.
 */
export default {
    // ── Events that already have a publisher ──
    BATCH_RUN_FAILED: {
        name: 'Batch run failed',
        description: 'Notifies you when a batch ends with an error. If the same batch keeps failing, you are notified once per 30 minutes.',
        recipient: 'Operators who can view batches',
    },
    OPERATOR_INVITATION_ACCEPTED: {
        name: 'Operator invitation accepted',
        description: 'Notifies you when an invited person accepts and the operator account is created.',
        recipient: 'The inviting operator and operators who can manage operators (not the person who accepted)',
    },
    OPERATOR_ACCOUNT_CREATED: {
        name: 'Operator added directly',
        description: 'Notifies you when an operator is added directly.',
        recipient: 'Operators who can manage operators (not the person who added)',
    },
    OPERATOR_STATUS_CHANGED_ME: {
        name: 'My account status changed',
        description: 'Notifies you when your account is locked, suspended, approved, or its validity period changes.',
        recipient: 'The person whose account changed',
    },
    OPERATOR_LOCKED_BY_FAILURES: {
        name: 'Account locked by sign-in failures',
        description: 'Notifies you when an account starts to be locked after 5 failed sign-ins in a row.',
        recipient: 'Operators who can lock operators',
    },
    ROLES_CHANGED_ME: {
        name: 'My roles or permissions changed',
        description: 'Notifies you when a role is added to or removed from you, or when the permissions of your role change.',
        recipient: 'The person whose roles changed',
    },
    PASSWORD_CHANGED_ME: {
        name: 'Password changed',
        description: 'Notifies you when your password is changed.',
        recipient: 'The person who changed the password',
    },
    PASSWORD_RESET_DONE_ME: {
        name: 'Password reset',
        description: 'Notifies you when your password is reset.',
        recipient: 'The person whose password was reset',
    },
    SITE_SETTINGS_CHANGED: {
        name: 'Site settings changed',
        description: 'Notifies you when settings such as the site name or display defaults change. Changes within 60 seconds are reported once.',
        recipient: 'Operators who can view site settings (not the person who changed them)',
    },
    GRADE_SETTINGS_SAVED: {
        name: 'Grade settings saved',
        description: 'Notifies you when the grade promotion conditions are saved.',
        recipient: 'Operators who can view grade settings (not the person who saved)',
    },
    GRADE_PROMOTION_RUN: {
        name: 'Grade promotion batch finished',
        description: 'Notifies you when the days-based promotion batch finishes and some members moved up.',
        recipient: 'Operators who can view grade settings',
    },

    // ── Reserved events (no publisher yet: "Coming soon") ──
    REPORT_RECEIVED: {
        name: 'Report received',
        description: 'Notifies you when a new report is waiting for review.',
        recipient: 'Operators who can handle reports',
    },
    SANCTION_APPEAL_FILED: {
        name: 'Sanction appeal filed',
        description: 'Notifies you when a member appeals a sanction.',
        recipient: 'Operators who can handle appeals',
    },
    BOARD_SANCTION_REPEAT_REVIEW: {
        name: 'Repeated board sanctions need review',
        description: 'Notifies you when sanctions against the same target repeat and need a review.',
        recipient: 'Operators who can handle sanctions',
    },
    NICKNAME_IMAGE_PENDING: {
        name: 'Nickname image awaiting review',
        description: 'Notifies you when a nickname image is waiting for review.',
        recipient: 'Operators who can handle nickname images',
    },
    CLUB_OPEN_REQUEST_PENDING: {
        name: 'Club opening request pending',
        description: 'Notifies you when a request to open a club comes in.',
        recipient: 'Operators who can handle clubs',
    },
    HOTDEAL_SUSPECTED_PROMO: {
        name: 'Suspected promotion in a hot deal',
        description: 'Notifies you when a hot deal post looks like a promotion.',
        recipient: 'Operators who can handle hot deals',
    },
    COMMERCE_ORDER_ANOMALY: {
        name: 'Order anomaly detected',
        description: 'Notifies you right away when a group-buy order looks abnormal.',
        recipient: 'Operators who can handle orders',
    },
    COMMERCE_REFUND_REQUESTED: {
        name: 'Refund requested',
        description: 'Notifies you when a refund request comes in.',
        recipient: 'Operators who can approve refunds',
    },
    BUSINESS_APPLICATION_PENDING: {
        name: 'Business application pending',
        description: 'Notifies you when a business application is waiting for review.',
        recipient: 'Operators who can handle business applications',
    },
    AD_REVIEW_PENDING: {
        name: 'Ad review pending',
        description: 'Notifies you when an ad is waiting for review.',
        recipient: 'Operators who can handle ads',
    },
    OPERATOR_APPLICATION_PENDING: {
        name: 'Operator application pending',
        description: 'Notifies you when an operator application comes in.',
        recipient: 'Operators who can manage operators',
    },
};
