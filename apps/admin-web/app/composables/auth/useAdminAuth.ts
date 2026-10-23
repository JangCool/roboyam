import type { AdminLoginMfa, AdminLoginRequest, AdminMeResponse, AdminUser } from '~/composables/auth/api/useAdminAuthApi.types';
import UtilAdminSite from '~/utils/util-admin-site';

/**
 * 지금 누가 로그인해 있는가 — 화면 전체가 공유하는 관리자 로그인 상태 (`useState`).
 *
 * **토큰을 저장하지 않는다.** 세션은 서버가 준 HttpOnly 쿠키(`ADMIN_SESSION`)가 들고 있고 브라우저가 알아서 붙인다.
 * 화면은 "지금 누구인지"만 기억하고, 새로고침하면 `useAdminAuthApi().me()` 로 다시 받는다. 사용자 정보를 로컬 스토리지에 두지 않는다.
 * 서버와 말하는 것은 `useAdminAuthApi` 다(frontend-guide 3장). 여기는 상태만 든다.
 *
 * 사이트 이름(`siteName`)과 로고(`siteLogoUrl`)도 같은 응답에 실려 와서 여기서 함께 든다 — 비로그인에도 온다(로그인 화면의 로고·탭 제목이 쓴다).
 * 관리자 세션은 공개 사이트와 별개다(쿠키 이름·표·기한이 다르다) — 공개 사이트에 로그인해 있어도 여기서는 비로그인이다.
 */

/** 진행 중인 `me` 호출 — 동시에 여러 곳이 물어도 한 번만 보낸다 */
let inflight: Promise<AdminUser | null> | null = null;

export function useAdminAuth() {
    const api = useAdminAuthApi();

    /** 로그인한 관리자. 로그인 안 했으면 null */
    const user = useState<AdminUser | null>(ADMIN_AUTH_USER_STATE_KEY, () => null);
    /** 서버가 준 사이트 이름 원본. 화면은 기본 이름까지 거친 `siteLabel` 을 쓴다 */
    const siteName = useState<string | null>('admin-site-name', () => null);
    /** 올려 둔 로고 주소(이미지 주소 또는 SVG data URI). 없으면 null — 이름 글자만 보인다 */
    const siteLogoUrl = useState<string | null>('admin-site-logo-url', () => null);
    /** `me` 를 한 번이라도 받았는가. false 인 동안 null 은 "로그인 안 함"이 아니라 "아직 모름"이다 */
    const loaded = useState<boolean>('admin-auth-loaded', () => false);
    /**
     * 비밀번호는 맞았고 2차 인증이 남은 「2차 대기」 단계(`VERIFY`·`ENROLL`) — 로그인이 아니다(`user` 는 null).
     * 서버가 `me`·비밀번호 재설정 응답에 실어 준 값이고, 로그인 화면이 열릴 때 이 단계로 이어 간다(새로고침해도 2단계 유지). 없으면 null.
     */
    const pendingMfa = useState<AdminLoginMfa | null>('admin-auth-pending-mfa', () => null);

    const isLoggedIn = computed(() => user.value !== null);
    /** 화면에 글자로 꼭 보여야 하는 사이트 이름 — 모르면 기본 이름 */
    const siteLabel = computed(() => UtilAdminSite.label(siteName.value));

    function apply(res: AdminMeResponse): AdminUser | null {
        user.value = res.authenticated ? res.user : null;
        const stage = res.mfa?.stage;
        pendingMfa.value = !res.authenticated && res.mfa && (stage === 'VERIFY' || stage === 'ENROLL') ? res.mfa : null;
        // 이름을 못 받았으면(응답에 칸이 없거나 쓸 수 없는 값) 알고 있던 이름을 지우지 않는다
        const name = UtilAdminSite.clean(res.siteName);
        if (name) siteName.value = name;
        // 로고는 "없음(null)"이 올바른 답이라 이름과 달리 null 로도 바꾼다. 칸이 아예 없을 때(undefined)만 알던 값을 둔다
        if (res.siteLogoUrl !== undefined) siteLogoUrl.value = res.siteLogoUrl;
        return user.value;
    }

    /** 사이트 이름을 바꾼 직후(설정 메뉴의 「일반」 저장) 로고 글자·탭 제목이 새 이름을 따르게 한다. 쓸 수 없는 값이면 무시한다 */
    function setSiteName(name: string): void {
        const cleaned = UtilAdminSite.clean(name);
        if (cleaned) siteName.value = cleaned;
    }

    /** 로고를 올리거나 뗀 직후(설정 메뉴의 「일반」) 상단 로고 자리가 바로 따르게 한다 */
    function setSiteLogoUrl(url: string | null): void {
        siteLogoUrl.value = url;
    }

    /**
     * 서버에 로그인 상태를 묻는다. 앱이 시작할 때 먼저 부른다 — CSRF 쿠키도 이때 내려온다.
     * 서버에 닿지 못하면 던진다(호출하는 쪽이 정한다). `loaded` 는 성공·실패와 상관없이 true 가 된다.
     */
    async function refresh(): Promise<AdminUser | null> {
        if (inflight) return inflight;
        inflight = api
            .me()
            .then(apply)
            .finally(() => {
                loaded.value = true;
                inflight = null;
            });
        return inflight;
    }

    /** 이미 받았으면 다시 묻지 않는다. 라우트 가드가 이동마다 부른다. */
    async function ensureLoaded(): Promise<AdminUser | null> {
        if (loaded.value) return user.value;
        try {
            return await refresh();
        } catch {
            return null;
        }
    }

    /** 2차 대기 단계를 잊는다 — 로그인 화면이 대기를 버렸거나(다른 계정으로 로그인) 서버가 대기 만료를 알렸을 때 */
    function clearPendingMfa(): void {
        pendingMfa.value = null;
    }

    /** 로그인. 실패하면 `ApiError` 를 던진다(잘못된 정보 401 · 잠금 429 · 입력 400). */
    async function login(body: AdminLoginRequest): Promise<AdminUser | null> {
        return apply(await api.login(body));
    }

    /**
     * 로그인 요청만 보내고 응답을 그대로 돌려준다 — **로그인 상태(`user`)를 채우지 않는다.** 2차 인증 단계(`res.mfa`)가 있는 로그인 화면이 쓴다:
     * 단계가 끝나면(또는 단계가 없으면) 화면이 `adopt` 로 받아들인다. 실패는 `login` 과 같다.
     */
    async function authenticate(body: AdminLoginRequest): Promise<AdminMeResponse> {
        return api.login(body);
    }

    /**
     * 서버가 로그인시킨 응답(`me` 모양)을 로그인 상태로 받아들인다 — 로그인 2단계 통과·비밀번호 재설정 성공(`POST /password-reset`)이 이 경우다.
     * 응답이 2차 대기(`authenticated:false` + `mfa.stage = VERIFY|ENROLL`)면 로그인되지 않고 `pendingMfa` 만 남는다(로그인 화면이 이어 간다).
     * `login` 과 같은 `apply` 를 거치므로 로그인 성공과 같은 결과다(표시 값 플러그인이 사용자가 바뀐 것을 보고 내 표시 형식을 받는다).
     */
    function adopt(res: AdminMeResponse): AdminUser | null {
        return apply(res);
    }

    /**
     * 로그아웃. **서버가 실패해도 화면 상태는 지운다** — 여기서 붙잡으면 눌렀는데 로그인 상태로 남는다
     * (공용 PC 에서 가장 나쁜 결과).
     */
    async function logout(): Promise<void> {
        try {
            await api.logout();
        } finally {
            user.value = null;
            pendingMfa.value = null;
        }
    }

    return { user, siteLabel, siteLogoUrl, setSiteName, setSiteLogoUrl, loaded, isLoggedIn, pendingMfa, clearPendingMfa, refresh, ensureLoaded, login, authenticate, adopt, logout };
}
