import type { AuthUser, LoginRequest, MeResponse, SignupRequest, SocialSignupRequest } from '~/composables/auth/api/useAuthApi.types';
import type { Restriction } from '~/composables/auth/api/useAuthApi.types';
import UtilAdminLink from '~/utils/util-admin-link';

/**
 * 지금 누가 로그인해 있는가 — 화면 전체가 공유하는 로그인 상태 (`useState` 하나).
 *
 * **토큰을 저장하지 않는다.** 세션은 서버가 준 HttpOnly 쿠키(`SESSION`)가 들고 있고 브라우저가 알아서 붙인다.
 * 화면은 "지금 누구인지"만 기억하고, 새로고침하면 `useAuthApi().me()` 로 다시 받는다.
 * 사용자 정보를 로컬 스토리지에 두지 않는다(서버에서 계정이 바뀌어도 옛 정보를 보여 주게 된다).
 *
 * 서버와 말하는 것은 `useAuthApi` 다(frontend-guide 3장). 여기는 상태만 든다.
 *
 * 이메일 가입은 `signup`, 소셜 가입 마무리는 `completeSocialSignup`. 원본(community)의 사업자 가입(`signupBusiness`)은 로보얌에 없어 뺐다.
 */

/** 진행 중인 `me` 호출 — 동시에 여러 곳이 물어도 한 번만 보낸다 */
let inflight: Promise<AuthUser | null> | null = null;

export function useAuth() {
    const api = useAuthApi();

    /** 로그인한 사람. 로그인 안 했으면 null */
    const user = useState<AuthUser | null>(AUTH_USER_STATE_KEY, () => null);
    /** `me` 를 한 번이라도 받았는가. false 인 동안 null 은 "로그인 안 함"이 아니라 "아직 모름"이다 */
    const loaded = useState<boolean>('auth-loaded', () => false);
    /** 서버가 로그인 정보에 실어 준 제한 요약 원본(7-2). 화면은 로그인 여부를 거친 `restriction` 을 쓴다 */
    const restrictionState = useState<Restriction | null>('auth-restriction', () => null);
    /** 서버가 로그인 정보에 실어 준 관리자 앱 주소 원본(7-4). 화면은 역할·주소 모양을 거른 `adminUrl` 을 쓴다 */
    const adminUrlState = useState<string | null>('auth-admin-url', () => null);

    /**
     * 정지·영구 정지 중이거나 확인하지 않은 경고가 있을 때의 요약(`/api/auth/me`·로그인·가입 응답의 `restriction`, docs/api-sanction.md 2절).
     * 정상이고 확인할 경고도 없으면 null. **로그인하지 않았으면 늘 null** — 세션이 끊겨 `user` 가 비면 이 값도 함께 사라진다.
     * 전역 배너와 로그인 직후 안내 이동이 이것을 본다.
     */
    const restriction = computed<Restriction | null>(() => (user.value ? restrictionState.value : null));

    /**
     * 관리자 앱 주소(`/api/auth/me`·로그인·가입 응답의 `adminUrl`, docs/api-admin-ops.md 7절) — 사용자 메뉴의 "관리자 앱" 링크가 쓴다.
     * 서버는 관리자 앱에 들어갈 수 있는 운영자·운영진에게만 값을 준다. 화면도 한 번 더 거른다 — **운영진·운영자이고 `http(s)://` 주소일 때만** 값이 있고,
     * 로그인하지 않았거나 일반 회원이면 늘 null 이다(`UtilAdminLink.resolve`). null 이면 링크를 그리지 않는다.
     */
    const adminUrl = computed<string | null>(() => UtilAdminLink.resolve(user.value?.role, adminUrlState.value));

    const isLoggedIn = computed(() => user.value !== null);
    const emailVerified = computed(() => user.value?.emailVerified ?? false);

    function apply(res: MeResponse): AuthUser | null {
        user.value = res.authenticated ? res.user : null;
        restrictionState.value = res.authenticated ? (res.restriction ?? null) : null;
        adminUrlState.value = res.authenticated ? (res.adminUrl ?? null) : null;
        return user.value;
    }

    /**
     * 서버에 로그인 상태를 묻는다. 앱이 시작할 때 먼저 부른다 — CSRF 쿠키도 이때 내려온다.
     * 서버에 닿지 못하면 던진다(호출하는 쪽이 정한다). `loaded` 는 성공·실패와 상관없이 true 가 된다.
     */
    async function refresh(): Promise<AuthUser | null> {
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
    async function ensureLoaded(): Promise<AuthUser | null> {
        if (loaded.value) return user.value;
        try {
            return await refresh();
        } catch {
            return null;
        }
    }

    /** 로그인. 실패하면 `ApiError` 를 던진다(잘못된 정보 401 · 잠금 429 …). */
    async function login(body: LoginRequest): Promise<AuthUser | null> {
        return apply(await api.login(body));
    }

    /**
     * 이메일 가입 — 가입과 동시에 로그인된다(`201`, me 와 같은 모양). 실패하면 `ApiError` 를 던진다(409 EMAIL_TAKEN·NICKNAME_TAKEN · 400 VALIDATION …).
     * ⚠ MOCK: 서버에 가입 API 가 없어 목업 응답을 담는다 — 서버 세션이 없으므로 화면 상태만 로그인한 것처럼 된다(`member-account-contract.md` 「목업 가정」).
     */
    async function signup(body: SignupRequest): Promise<AuthUser | null> {
        return apply(await api.signup(body));
    }

    /** 소셜 가입 마무리 — 가입과 동시에 로그인된다(`201`, me 와 같은 모양). 실패하면 `ApiError` 를 던진다(409 NICKNAME_TAKEN · 400 VALIDATION …). */
    async function completeSocialSignup(body: SocialSignupRequest): Promise<AuthUser | null> {
        return apply(await api.completeSocialSignup(body));
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
            restrictionState.value = null;
            adminUrlState.value = null;
        }
    }

    /** 세션이 서버에서 끝났음을 알았을 때(비밀번호 재설정 뒤 등) 화면 상태만 비운다 */
    function clear(): void {
        user.value = null;
        restrictionState.value = null;
        adminUrlState.value = null;
    }

    return { user, restriction, adminUrl, loaded, isLoggedIn, emailVerified, refresh, ensureLoaded, login, signup, completeSocialSignup, logout, clear };
}
