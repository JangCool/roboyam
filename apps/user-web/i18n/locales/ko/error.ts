/**
 * 서버 오류 코드 → 화면 문구. **서버 `message` 가 있으면 그것을 먼저 쓴다**(`useApiClient` 가 그렇게 채운다) — 여기 있는 것은 서버 문구가 없을 때(네트워크 끊김·본문 없는 403/5xx 등)의 문구다.
 * 키는 서버 오류 코드 그대로(`error.INVALID_CREDENTIALS`) — `utils/util-auth-messages.ts` 가 코드로 고른다. 분기는 문구가 아니라 `code` 로 한다.
 * ⚠ 서버가 내려 주는 `message` 자체는 한국어다(서버에 i18n 이 없다) — 영어 화면에서도 서버 문구는 한국어로 보인다.
 * `en/error.ts` 와 같은 키, 같은 차례.
 */
export default {
    VALIDATION: '입력한 내용을 다시 확인해 주세요.',
    INVALID_CREDENTIALS: '이메일 또는 비밀번호가 맞지 않아요. 다시 확인해 주세요.',
    LOGIN_LOCKED: '로그인이 잠시 제한됐어요. 잠시 후 다시 시도해 주세요.',
    EMAIL_TAKEN: '이미 가입된 이메일이에요.',
    NICKNAME_TAKEN: '이미 쓰고 있는 닉네임이에요. 다른 닉네임을 입력해 주세요.',
    NICKNAME_CHANGE_LIMIT: '닉네임을 이미 바꿔서 더 바꿀 수 없어요. 닉네임은 가입 뒤 1번만 바꿀 수 있어요.',
    NICKNAME_TICKET_REQUIRED: '무료 변경 횟수를 모두 사용했어요. 닉네임 변경권으로 바꿀 수 있어요.',
    UNAUTHENTICATED: '로그인이 필요해요.',
    TOKEN_INVALID: '링크가 올바르지 않거나 이미 사용됐어요.',
    TOKEN_EXPIRED: '링크가 만료됐어요.',
    ALREADY_VERIFIED: '이미 이메일 인증이 끝났어요.',
    RESEND_COOLDOWN: '잠시 후 다시 시도해 주세요.',
    SAME_PASSWORD: '지금 쓰는 비밀번호와 같아요. 다른 비밀번호를 입력해 주세요.',
    CURRENT_PASSWORD_WRONG: '현재 비밀번호가 맞지 않아요.',
    PASSWORD_NOT_SET: '소셜 로그인으로 가입한 계정은 비밀번호가 없어요.',
    EMAIL_CHANGE_NOT_ALLOWED: '소셜 로그인으로 가입한 계정은 이메일을 바꿀 수 없어요.',
    NO_PENDING: '소셜 가입 시간이 지났어요. 처음부터 다시 시도해 주세요.',
    FORBIDDEN: '권한이 없어요.',
    CSRF_INVALID: '요청을 확인할 수 없어요. 페이지를 새로 고친 뒤 다시 시도해 주세요.',
    NOT_FOUND: '찾을 수 없어요.',
    INTERNAL: '서버에 문제가 생겼어요. 잠시 후 다시 시도해 주세요.',
    NETWORK: '서버에 연결하지 못했어요. 네트워크를 확인하고 다시 시도해 주세요.',
    SERVER_ERROR: '서버에 문제가 생겼어요. 잠시 후 다시 시도해 주세요.',

    // 코드가 없을 때 HTTP 상태로 고르는 문구
    status403: '요청을 처리하지 못했어요. 페이지를 새로고침한 뒤 다시 시도해 주세요.',
    status404: '요청한 내용을 찾지 못했어요.',
    status429: '요청이 너무 많아요. 잠시 후 다시 시도해 주세요.',
    generic: '문제가 생겼어요. 잠시 후 다시 시도해 주세요.',
};
