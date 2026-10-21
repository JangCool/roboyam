/**
 * Server error code → on-screen text, used when the server sends no `message` (network loss, bodiless 403/5xx …). Same keys and order as `ko/error.ts`.
 * Note: a `message` sent by the server is Korean (the server has no i18n yet), so it stays Korean on an English screen.
 */
export default {
    VALIDATION: 'Please check what you entered.',
    INVALID_CREDENTIALS: 'The email or password is incorrect. Please check and try again.',
    LOGIN_LOCKED: 'Log-in is temporarily locked. Please try again in a moment.',
    EMAIL_TAKEN: 'This email is already registered.',
    NICKNAME_TAKEN: 'This nickname is already taken. Please enter another one.',
    NICKNAME_CHANGE_TOO_SOON: 'The second free change is available 30 days after the first. With a nickname change ticket you can change it now.',
    NICKNAME_TICKET_REQUIRED: 'You have used all free changes. You can change it with a nickname change ticket.',
    UNAUTHENTICATED: 'Please log in.',
    TOKEN_INVALID: 'This link is invalid or has already been used.',
    TOKEN_EXPIRED: 'This link has expired.',
    ALREADY_VERIFIED: 'Your email is already verified.',
    RESEND_COOLDOWN: 'Please try again in a moment.',
    SAME_PASSWORD: 'That is the same as your current password. Please enter a different one.',
    NO_PENDING: 'Your social sign-up has timed out. Please start again.',
    FORBIDDEN: 'You do not have permission.',
    CSRF_INVALID: 'We could not verify the request. Please refresh the page and try again.',
    NOT_FOUND: 'Not found.',
    INTERNAL: 'Something went wrong on the server. Please try again in a moment.',
    NETWORK: 'Could not reach the server. Check your network and try again.',
    SERVER_ERROR: 'Something went wrong on the server. Please try again in a moment.',

    // Text picked by HTTP status when there is no code
    status403: 'The request could not be processed. Please refresh the page and try again.',
    status404: 'The requested item could not be found.',
    status429: 'Too many requests. Please try again in a moment.',
    generic: 'Something went wrong. Please try again in a moment.',
};
