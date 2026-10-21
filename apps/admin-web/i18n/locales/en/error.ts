/**
 * Server error code → screen text. **A server `message` is used first** (`useApiClient` fills it in) — these are for when the server gave none (network down, 403/5xx with no body, …).
 * Keys are the server error codes themselves (`error.INVALID_CREDENTIALS`) — `utils/util-admin-auth-messages.ts` picks by code. Branch on `code`, never on text.
 * Same keys and order as `ko/error.ts`.
 */
export default {
    VALIDATION: 'Please check what you entered.',
    INVALID_CREDENTIALS: 'The email or password is incorrect. Please check and try again.',
    LOGIN_LOCKED: 'Sign-in is temporarily restricted. Please try again shortly.',
    UNAUTHENTICATED: 'You need to sign in.',
    FORBIDDEN: 'You do not have permission.',
    PERMISSION_DENIED: 'You do not have permission to do this.',
    CSRF_INVALID: 'The request could not be verified. Refresh the page and try again.',
    NOT_FOUND: 'Not found.',
    NOT_INSTALLED: 'Setup is not finished. Run the install screen on the public site first.',
    INTERNAL: 'Something went wrong on the server. Please try again shortly.',
    NETWORK: 'Could not reach the server. Check your network and try again.',
    SERVER_ERROR: 'Something went wrong on the server. Please try again shortly.',

    // Picked by HTTP status when there is no code
    status403: 'The request could not be processed. Refresh the page and try again.',
    status404: 'The requested item was not found.',
    status429: 'Too many requests. Please try again shortly.',
    generic: 'Something went wrong. Please try again shortly.',
};
