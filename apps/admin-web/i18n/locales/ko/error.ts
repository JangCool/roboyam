/**
 * 서버 오류 코드 → 화면 문구. **서버 `message` 가 있으면 그것을 먼저 쓴다**(`useApiClient` 가 그렇게 채운다) — 여기 있는 것은 서버 문구가 없을 때(네트워크 끊김·본문 없는 403/5xx 등)의 문구다.
 * 키는 서버 오류 코드 그대로(`error.INVALID_CREDENTIALS`) — `utils/util-admin-auth-messages.ts` 가 코드로 고른다. 분기는 문구가 아니라 `code` 로 한다.
 * `en/error.ts` 와 같은 키, 같은 차례.
 */
export default {
    VALIDATION: '입력한 내용을 다시 확인해 주세요.',
    INVALID_CREDENTIALS: '이메일 또는 비밀번호가 맞지 않아요. 다시 확인해 주세요.',
    LOGIN_LOCKED: '로그인이 잠시 제한됐어요. 잠시 후 다시 시도해 주세요.',
    UNAUTHENTICATED: '로그인이 필요해요.',
    FORBIDDEN: '권한이 없어요.',
    PERMISSION_DENIED: '이 작업을 할 권한이 없어요.',
    CSRF_INVALID: '요청을 확인할 수 없어요. 페이지를 새로 고친 뒤 다시 시도해 주세요.',
    NOT_FOUND: '찾을 수 없어요.',
    NOT_INSTALLED: '설치가 끝나지 않았어요. 공개 사이트의 설치 화면에서 먼저 설치해 주세요.',
    INTERNAL: '서버에 문제가 생겼어요. 잠시 후 다시 시도해 주세요.',
    NETWORK: '서버에 연결하지 못했어요. 네트워크를 확인하고 다시 시도해 주세요.',
    SERVER_ERROR: '서버에 문제가 생겼어요. 잠시 후 다시 시도해 주세요.',

    // 코드가 없을 때 HTTP 상태로 고르는 문구
    status403: '요청을 처리하지 못했어요. 페이지를 새로고침한 뒤 다시 시도해 주세요.',
    status404: '요청한 내용을 찾지 못했어요.',
    status429: '요청이 너무 많아요. 잠시 후 다시 시도해 주세요.',
    generic: '문제가 생겼어요. 잠시 후 다시 시도해 주세요.',
};
