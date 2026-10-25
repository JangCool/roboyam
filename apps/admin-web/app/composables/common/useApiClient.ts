import UtilI18n, { type Translate } from '#layers/i18n/utils/util-i18n';
import UtilAdminAuthMessages from '~/utils/util-admin-auth-messages';
import UtilRedirect from '~/utils/util-redirect';

/**
 * 서버에 요청을 보내는 유일한 통로 (frontend-guide 4장). 화면과 다른 컴포저블은 `$fetch` 를 직접 부르지 않는다.
 * user-web 의 `useApiClient` 와 같은 동작이다 — 두 앱이 같은 통로를 갖게 되면 공유 레이어로 옮길 후보(설치 상태 처리만 앱마다 다르다).
 *
 * 하는 일 셋 —
 *
 *  1. **CSRF**: 쿠키 `XSRF-TOKEN` 을 읽어 POST·PUT·PATCH·DELETE 에 `X-XSRF-TOKEN` 헤더로 넣는다.
 *     ⚠ **요청마다 쿠키를 다시 읽는다.** 로그인·로그아웃 때 서버가 토큰을 바꿀 수 있어 처음 값을 들고 있으면 안 된다.
 *     CSRF 거절(서버 코드 `CSRF_INVALID` 이거나 본문 없는 403)이면 `refreshCsrf` 로 쿠키를 다시 받고 **한 번만** 다시 보낸다.
 *  2. **응답 래퍼 벗기기 · 오류 정규화**: 서버는 모든 JSON 응답을 `{success, data, meta}` / `{success:false, error, meta}` 로 준다
 *     (`.claude/guides/backend/exception.md` 2-1). 성공이면 **`data` 만** 돌려주고(본문 없는 `204` 는 `undefined`),
 *     어떤 실패든 {@link ApiError} 로 바꿔 던진다 — `{ status, code, message, fields?, retryAfterSeconds?, requestId? }`.
 *     서버 `message` 가 있으면 그것을, 없으면 `UtilAdminAuthMessages.fallback` 의 문구(지금 언어)를 `message` 에 담는다.
 *     `requestId` 는 본문 `meta.requestId`, 없으면 응답 헤더 `X-Request-Id`(문의·로그용).
 *     래퍼가 아닌 응답(`success` 칸 없음 — 프록시·게이트웨이의 HTML·글자, 502 등)은 래퍼 이전 방식 그대로 처리한다.
 *  3. **언어**: 모든 요청(GET 포함)에 `Accept-Language: ko|en`(지금 화면 언어 = vue-i18n 로캘)을 붙인다.
 *
 * 로그인해 있다고 믿는데 `401 UNAUTHENTICATED` 가 오면 세션이 끝난 것이다(관리자 세션은 마지막 활동 뒤 2시간) — 상태를 비우고 `/login?next=…&expired=1` 로 보낸다.
 * **경로가 아니라 "지금 상태"로 가른다**(로그인 실패의 401 은 `INVALID_CREDENTIALS` 라 코드가 다르고, 그때는 원래 로그인 상태가 아니다).
 *
 * `baseURL` 은 두지 않는다. 화면과 API 가 같은 출처이므로 절대 경로(`/api/admin/…`) 그대로면 되고, 개발 중에는 Nuxt 프록시가 잇는다.
 */

/**
 * 이 통로가 공유 상태에서 보는 최소 모양 — 업무(auth) 타입을 가져오지 않으려고(common → 업무 의존을 두지 않는다) 여기서 쓰는 칸만 적는다.
 * 실제 모양은 `AdminUser`(auth/api/useAdminAuthApi.types) 이고 이 모양의 상위 집합이다.
 */
type SessionUser = { id: string };

/** 로그인한 관리자 상태의 키 — `useAdminAuth` 와 같은 값을 본다. */
export const ADMIN_AUTH_USER_STATE_KEY = 'admin-auth-user';

export interface ApiErrorInit {
    status: number;
    code: string;
    message: string;
    fields?: Record<string, string>;
    retryAfterSeconds?: number;
    /** 이 요청을 서버 로그와 잇는 번호 — 본문 `meta.requestId`, 없으면 `X-Request-Id` 응답 헤더. 문의·로그용 */
    requestId?: string;
    extras?: Record<string, unknown>;
}

/** 서버가 준(또는 화면이 만든) 오류. 화면은 `code` 로 상태를, `message` 로 문구를 고른다. */
export class ApiError extends Error {
    readonly status: number;
    readonly code: string;
    readonly fields?: Record<string, string>;
    readonly retryAfterSeconds?: number;
    readonly requestId?: string;
    /** 서버가 오류 본문(`error`)에 더 실어 준 값(`code`·`message`·`fields`·`retryAfterSeconds` 는 뺀다). 해당 오류에서만 있다 */
    readonly extras?: Record<string, unknown>;

    constructor(init: ApiErrorInit) {
        super(init.message);
        this.name = 'ApiError';
        this.status = init.status;
        this.code = init.code;
        this.fields = init.fields;
        this.retryAfterSeconds = init.retryAfterSeconds;
        this.requestId = init.requestId;
        this.extras = init.extras;
    }

    /** 입력칸 하나의 서버 오류 문구 */
    field(name: string): string | undefined {
        return this.fields?.[name];
    }
}

/** 오류 응답에서 `extras` 로 옮기지 않는 이름 — 이미 정해진 칸이거나 Spring 기본 오류 본문의 잡음이다 */
const KNOWN_ERROR_KEYS: ReadonlySet<string> = new Set(['code', 'message', 'fields', 'retryAfterSeconds', 'status', 'error', 'path', 'timestamp', 'trace']);

type QueryScalar = string | number | boolean;
/** 목록 값은 같은 이름을 반복해 보낸다(`statuses=A&statuses=B`) — 서버가 반복과 쉼표로 이은 값을 모두 받는다. 빈 목록은 보내지 않는다 */
type QueryValue = QueryScalar | readonly QueryScalar[] | null | undefined;

export interface RequestOptions {
    query?: Record<string, QueryValue>;
    /** 화면이 떠나거나 새 요청이 앞 요청을 대신할 때 끊는다 */
    signal?: AbortSignal;
}

export interface ApiClientOptions {
    /** CSRF 쿠키를 다시 받는 호출. 주소는 부르는 쪽(`useAdminAuthApi`)이 안다. */
    refreshCsrf?: () => Promise<unknown>;
}

const CSRF_COOKIE = 'XSRF-TOKEN';
const CSRF_HEADER = 'X-XSRF-TOKEN';
/** 서버가 CSRF 검사에 실패했을 때 주는 오류 코드 */
const CSRF_INVALID = 'CSRF_INVALID';
const MUTATING = new Set(['POST', 'PUT', 'PATCH', 'DELETE']);
const TIMEOUT_MS = 30_000;

/** `$fetch` 가 던지는 오류 중 우리가 읽는 부분 (`ofetch` 를 직접 import 하지 않으려고 모양만 적는다) */
interface FetchFailure {
    name?: string;
    message?: string;
    status?: number;
    statusCode?: number;
    data?: unknown;
    response?: { status: number; headers?: { get(name: string): string | null } };
}

function readCookie(name: string): string | null {
    if (typeof document === 'undefined') return null;
    for (const part of document.cookie.split('; ')) {
        const eq = part.indexOf('=');
        if (eq < 0 || part.slice(0, eq) !== name) continue;
        const raw = part.slice(eq + 1);
        try {
            return decodeURIComponent(raw);
        } catch {
            return raw;
        }
    }
    return null;
}

/** 질의 문자열은 우리가 만든다 — 값을 예외 없이 인코딩한다. 없는 값(undefined·null)과 빈 목록은 뺀다. */
function withQuery(url: string, query?: Record<string, QueryValue>): string {
    if (!query) return url;
    const parts: string[] = [];
    for (const [key, value] of Object.entries(query)) {
        if (value === undefined || value === null) continue;
        for (const item of Array.isArray(value) ? value : [value]) {
            parts.push(`${encodeURIComponent(key)}=${encodeURIComponent(String(item))}`);
        }
    }
    return parts.length ? `${url}${url.includes('?') ? '&' : '?'}${parts.join('&')}` : url;
}

function isObject(value: unknown): value is Record<string, unknown> {
    return typeof value === 'object' && value !== null && !Array.isArray(value);
}

/** 오류 본문에서 읽는 부분 — 래퍼(`{success:false, error, meta}`)면 `error` 를, 아니면 본문 전체를 쓴다 */
interface ErrorBody {
    body: Record<string, unknown>;
    requestId?: string;
}

function nonEmptyString(value: unknown): string | undefined {
    return typeof value === 'string' && value.trim() ? value : undefined;
}

function errorBodyOf(data: unknown): ErrorBody {
    if (!isObject(data)) return { body: {} };
    if (data.success === false && isObject(data.error)) {
        return { body: data.error, requestId: isObject(data.meta) ? nonEmptyString(data.meta.requestId) : undefined };
    }
    return { body: data }; // 래퍼가 아니다(프록시·게이트웨이가 준 본문 등) — 래퍼 이전 방식 그대로
}

function serverCodeOf(failure: FetchFailure): string | null {
    return nonEmptyString(errorBodyOf(failure.data).body.code) ?? null;
}

function statusOf(failure: FetchFailure): number {
    return failure.response?.status ?? failure.status ?? failure.statusCode ?? 0;
}

function toApiError(error: unknown, t: Translate): ApiError {
    if (error instanceof ApiError) return error;

    const failure = (isObject(error) ? error : {}) as FetchFailure;
    const status = statusOf(failure);

    if (status === 0) {
        const aborted = failure.name === 'AbortError' || /aborted/i.test(failure.message ?? '');
        const code = aborted ? 'ABORTED' : 'NETWORK';
        return new ApiError({ status: 0, code, message: UtilAdminAuthMessages.fallback(t, 0, code) });
    }

    const { body, requestId: metaRequestId } = errorBodyOf(failure.data);
    const code = serverCodeOf(failure) ?? (status >= 500 ? 'SERVER_ERROR' : `HTTP_${status}`);
    const message = nonEmptyString(body.message) ?? UtilAdminAuthMessages.fallback(t, status, code);

    let fields: Record<string, string> | undefined;
    if (isObject(body.fields)) {
        fields = {};
        for (const [key, value] of Object.entries(body.fields)) {
            if (typeof value === 'string') fields[key] = value;
        }
    }

    let retryAfterSeconds = typeof body.retryAfterSeconds === 'number' ? body.retryAfterSeconds : undefined;
    if (retryAfterSeconds === undefined) {
        const header = Number(failure.response?.headers?.get('retry-after'));
        if (Number.isFinite(header) && header > 0) retryAfterSeconds = header;
    }

    const requestId = metaRequestId ?? nonEmptyString(failure.response?.headers?.get('x-request-id'));

    let extras: Record<string, unknown> | undefined;
    for (const [key, value] of Object.entries(body)) {
        if (KNOWN_ERROR_KEYS.has(key)) continue;
        (extras ??= {})[key] = value;
    }

    return new ApiError({ status, code, message, fields, retryAfterSeconds, requestId, extras });
}

export function useApiClient(options: ApiClientOptions = {}) {
    // ⚠ Nuxt 문맥은 부르는 순간(setup)에만 잡힌다. 요청이 끝난 뒤(await 뒤)에는 useNuxtApp() 이 안 된다.
    const nuxtApp = useNuxtApp();
    // 서버 문구가 없을 때의 문구를 지금 언어로 — 부르는 순간 붙잡아 둔다
    const t = useTranslate();
    // 요청 때 지금 언어를 읽으려고 로캘(반응형 값)을 붙잡아 둔다 — `.value` 는 요청을 보내는 순간 읽으므로 Nuxt 문맥이 없어도(await 뒤) 된다
    const locale = nuxtApp.$i18n.locale;

    /** 로그인해 있다고 믿는데 401 이면 세션이 끝난 것 — 로그인 화면으로 보낸다 */
    async function onSessionLost(): Promise<void> {
        await nuxtApp.runWithContext(async () => {
            const user = useState<SessionUser | null>(ADMIN_AUTH_USER_STATE_KEY);
            if (!user.value) return; // 원래 로그인 상태가 아니었다
            user.value = null;
            await navigateTo(UtilRedirect.toLogin(useRoute().fullPath, { expired: '1' }));
        });
    }

    /** 요청 한 번(`attempt`)을 보내고, CSRF 거절이면 쿠키를 다시 받아 한 번만 다시 보낸다. 실패는 모두 `ApiError` 로 던진다 */
    async function run<T>(mutating: boolean, attempt: () => Promise<T>): Promise<T> {
        try {
            return await attempt();
        } catch (first) {
            let failure = first;

            // CSRF 거절이면(코드 CSRF_INVALID, 또는 본문 없는 403) 쿠키를 다시 받아 한 번만 재시도한다.
            const firstFailure = (isObject(first) ? first : {}) as FetchFailure;
            const serverCode = serverCodeOf(firstFailure);
            const csrfRejected = statusOf(firstFailure) === 403 && (!serverCode || serverCode === CSRF_INVALID);
            if (mutating && csrfRejected && options.refreshCsrf) {
                try {
                    await options.refreshCsrf();
                    return await attempt();
                } catch (second) {
                    failure = second;
                }
            }

            const error = toApiError(failure, t);
            if (error.status === 401 && error.code === 'UNAUTHENTICATED') await onSessionLost();
            throw error;
        }
    }

    /** 요청마다 CSRF 쿠키를 다시 읽는다(로그인·로그아웃 때 서버가 토큰을 바꿀 수 있다) */
    function csrfHeaders(): Record<string, string> {
        const token = readCookie(CSRF_COOKIE);
        return token ? { [CSRF_HEADER]: token } : {};
    }

    /** 요청마다 지금 화면 언어를 `Accept-Language` 로 — 요청을 보내는 순간 읽으므로 언어를 바꾼 뒤의 요청부터 바뀐다 */
    function languageHeaders(): Record<string, string> {
        return { 'Accept-Language': UtilI18n.languageOf(locale.value) };
    }

    function send<T>(method: string, url: string, body?: unknown, request: RequestOptions = {}): Promise<T> {
        const mutating = MUTATING.has(method);
        const target = withQuery(url, request.query);

        return run<T>(mutating, async () => {
            const response = await $fetch.raw<unknown>(target, {
                method: method as 'GET',
                body: body as Record<string, unknown> | undefined,
                headers: { ...languageHeaders(), ...(mutating ? csrfHeaders() : {}) },
                signal: request.signal,
                timeout: TIMEOUT_MS,
                retry: 0,
            });
            return unwrap<T>(response._data, response.status, response.headers.get('x-request-id'));
        });
    }

    /**
     * 성공 응답에서 `data` 만 꺼낸다. 본문 없음(204·빈 본문)은 `undefined`.
     * `success` 칸이 없으면 래퍼가 아니므로 받은 그대로 돌려준다(방어). 2xx 인데 `success:false` 면 오류로 던진다.
     */
    function unwrap<T>(raw: unknown, status: number, headerRequestId: string | null): T {
        if (raw === undefined || raw === null || raw === '') return undefined as T;
        if (!isObject(raw) || typeof raw.success !== 'boolean') return raw as T;
        if (raw.success) return raw.data as T;
        throw toApiError({ status, data: raw, response: { status, headers: { get: (name: string) => (name.toLowerCase() === 'x-request-id' ? headerRequestId : null) } } }, t);
    }

    return {
        get: <T>(url: string, request?: RequestOptions) => send<T>('GET', url, undefined, request),
        post: <T>(url: string, body?: unknown, request?: RequestOptions) => send<T>('POST', url, body, request),
        put: <T>(url: string, body?: unknown, request?: RequestOptions) => send<T>('PUT', url, body, request),
        patch: <T>(url: string, body?: unknown, request?: RequestOptions) => send<T>('PATCH', url, body, request),
        /** 본문이 있는 DELETE(사유 등)는 세 번째 인자로 싣는다 */
        del: <T>(url: string, request?: RequestOptions, body?: unknown) => send<T>('DELETE', url, body, request),
    };
}
