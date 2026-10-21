import type { Translate } from '#layers/i18n/utils/util-i18n';
import { ApiError } from '~/composables/common/useApiClient';
import type { SocialProviderCode } from '~/composables/install/api/useInstallApi.types';
import type { SocialProviderSettingView } from '~/utils/util-admin-social';
import type { InstallBoardPreset, InstallCheck, InstallChecksView, InstallCompleteRequest, InstallRootInput, InstallSocialInput } from '~/composables/install/api/useInstallApi.types';
import UtilAdminSocial, { type SocialForm, type SocialProblems } from '~/utils/util-admin-social';
import UtilMail, { type MailForm } from '~/utils/util-mail';
import UtilSiteName from '~/utils/util-site-name';
import UtilSite, { type SiteForm } from '~/utils/util-site';
import UtilValidation, { type EmailProblem, type NicknameProblem } from '~/utils/util-validation';

/**
 * 설치 마법사(`/install`)의 순수 로직 — 단계·입력 규칙·요청 만들기·점검 결과 해석·서버 오류 경로 가르기.
 * 입력 규칙은 docs/api-install.md 와 `docs/api-auth.md` 의 "입력 규칙"과 같다(서버가 거절할 것을 화면이 미리 알려 주는 용도이고 판정은 서버).
 * 화면에 보이는 문구는 `util-install-messages.ts` 에 있다. 사이트·메일·소셜 키 입력의 규칙은 각각 `UtilSite`·`UtilMail`·`UtilAdminSocial` 을 그대로 쓴다.
 *
 * ⚠ 설치 토큰·비밀번호·시크릿은 여기서 어디에도 저장하지 않는다 — 인자로 지나가기만 한다.
 */

/** 마법사 단계 — 설치 토큰 + 점검 → 루트 계정 → 사이트 → 메일 서버 → 소셜 로그인 키 → 기본 게시판 구성 → 확인 */
export type InstallStepKey = 'token' | 'root' | 'site' | 'mail' | 'social' | 'boards' | 'review';

/** 건너뛸 수 있는 단계(메일·소셜)의 선택 — 지금 설정하는가 · 건너뛰는가 */
export type InstallMode = 'set' | 'skip';

/** 루트 계정 입력값 */
export interface RootForm {
    email: string;
    nickname: string;
    password: string;
    passwordConfirm: string;
}

export type RootField = keyof RootForm;

/** 루트 계정 입력칸의 문제 — 비밀번호 칸은 `REQUIRED`·`RULE`(8~64자·영문과 숫자), 확인 칸은 `REQUIRED`·`MISMATCH` */
export interface RootProblems {
    email?: EmailProblem;
    nickname?: NicknameProblem;
    password?: 'REQUIRED' | 'RULE' | 'TOO_LONG';
    passwordConfirm?: 'REQUIRED' | 'MISMATCH';
}

/** 소셜 로그인 키 세 곳의 입력값 */
export type SocialForms = Record<SocialProviderCode, SocialForm>;
export type SocialFormProblems = Partial<Record<SocialProviderCode, SocialProblems>>;

/** 칸 경로 → 오류 문구. 경로는 서버 `fields` 와 같은 꼴이다(`root.email` · `site.publicUrl` · `site.tagline` · `mail.port` · `social.NAVER.clientId`) */
export type InstallFieldErrors = Record<string, string>;

/** 요청을 만드는 데 쓰는 입력 전체 */
export interface InstallFormState {
    root: RootForm;
    site: SiteForm;
    mailMode: InstallMode;
    mail: MailForm;
    socialMode: InstallMode;
    social: SocialForms;
    /** 고른 기본 게시판 구성의 아이디 — 목록을 못 받았으면 빈 글자(요청에 담지 않는다 — 서버가 기본 구성을 쓴다) */
    boardPreset: string;
    /** 서버가 준 프리셋 목록(확인 화면이 이름을 보이려는 것) */
    boardPresets: readonly InstallBoardPreset[];
}

/** 점검 항목의 갈래 — 통과 · 경고(설치는 계속할 수 있음) · 실패(설치를 막음) */
export type CheckTone = 'ok' | 'warn' | 'fail';

/** 공급자 → 이름의 `social.*` 키(글자가 아니라 키라서 모듈 상수여도 언어를 따른다) */
const SOCIAL_LABEL_KEYS: Record<SocialProviderCode, string> = { NAVER: 'social.providerNaver', KAKAO: 'social.providerKakao', GOOGLE: 'social.providerGoogle' };
const STEPS: readonly InstallStepKey[] = ['token', 'root', 'site', 'mail', 'social', 'boards', 'review'];
/** 서버가 `social.providers[0].clientId` 꼴로 알려 주는 경로 */
const SOCIAL_PATH = /^social\.providers\[(\d+)\]\.(\w+)$/;

export default class UtilInstall {
    /** 마법사 단계 — 화면에 보이는 순서 */
    static readonly STEPS = STEPS;
    /** 소셜 로그인 공급자 — 서버 목록과 같은 순서 */
    static readonly PROVIDERS: readonly SocialProviderCode[] = ['NAVER', 'KAKAO', 'GOOGLE'];
    /** 루트 계정 입력 칸 */
    static readonly ROOT_FIELDS: readonly RootField[] = ['email', 'nickname', 'password', 'passwordConfirm'];
    /** 사이트 이름 처음 값 */
    /** 설치 마법사의 사이트 이름 처음 입력값 — 임시 이름(`UtilSiteName.DEFAULT`) */
    static readonly DEFAULT_SITE_NAME = UtilSiteName.DEFAULT;

    /**
     * 단계 번호(0부터).
     * @param {InstallStepKey} step - 단계.
     * @returns {number} 단계의 순서.
     */
    static stepIndex(step: InstallStepKey): number {
        return STEPS.indexOf(step);
    }

    /**
     * 다음 단계.
     * @param {InstallStepKey} step - 지금 단계.
     * @returns {InstallStepKey} 다음 단계. 마지막이면 그대로.
     */
    static stepAfter(step: InstallStepKey): InstallStepKey {
        return STEPS[Math.min(STEPS.length - 1, UtilInstall.stepIndex(step) + 1)]!;
    }

    /**
     * 이전 단계.
     * @param {InstallStepKey} step - 지금 단계.
     * @returns {InstallStepKey} 이전 단계. 처음이면 그대로.
     */
    static stepBefore(step: InstallStepKey): InstallStepKey {
        return STEPS[Math.max(0, UtilInstall.stepIndex(step) - 1)]!;
    }

    /**
     * 소셜 로그인 공급자의 이름(지금 언어).
     * @param {Translate} t - 번역 함수(`useI18n().t`).
     * @param {SocialProviderCode} provider - 공급자 코드.
     * @returns {string} "네이버" · "카카오" · "구글" (영어는 Naver · Kakao · Google).
     */
    static providerLabel(t: Translate, provider: SocialProviderCode): string {
        return t(SOCIAL_LABEL_KEYS[provider]);
    }

    /* ── 처음 입력값 ── */

    /**
     * 루트 계정 처음 입력값 — 모두 비어 있다.
     * @returns {RootForm} 빈 입력값.
     */
    static initialRoot(): RootForm {
        return { email: '', nickname: '', password: '', passwordConfirm: '' };
    }

    /**
     * 사이트 처음 입력값 — 이름은 임시 이름(`UtilSiteName.DEFAULT`), 공개 주소는 **지금 접속한 주소**로 채운다(운영자가 고친다).
     * 한 줄 소개(슬로건)는 비워 둔다 — 비어 있으면 없음이고, 고른 기본 게시판 구성이 슬로건을 갖고 있으면 서버가 그것을 넣는다(화면은 신경 쓰지 않는다).
     * @returns {SiteForm} 처음 입력값.
     */
    static initialSite(): SiteForm {
        return { name: UtilInstall.DEFAULT_SITE_NAME, publicUrl: UtilSite.currentOrigin(), tagline: '' };
    }

    /**
     * 소셜 로그인 키 세 곳의 처음 입력값 — 비어 있고 **꺼 둔 채 시작한다**(설정 화면의 `UtilAdminSocial.initialForm` 은 켜 두고 시작하지만 마법사는 쓰려는 곳만 켠다).
     * @returns {SocialForms} 공급자별 빈 입력값.
     */
    static initialSocial(): SocialForms {
        const forms = {} as SocialForms;
        for (const code of UtilInstall.PROVIDERS) forms[code] = { ...UtilAdminSocial.initialForm(UtilInstall.newSocialSetting(code, '')), enabled: false };
        return forms;
    }

    /**
     * 아직 등록 안 된(`NONE`) 공급자 설정 — 설정 화면의 입력 규칙(`UtilAdminSocial.validate`: 처음 등록이라 시크릿 필수)을 그대로 쓰려는 틀이다.
     * @param {SocialProviderCode} provider - 공급자 코드.
     * @param {string} callbackUrl - 각 사 콘솔에 등록할 콜백 주소(모르면 빈 문자열).
     * @returns {SocialProviderSettingView} 미등록 상태의 설정.
     */
    static newSocialSetting(provider: SocialProviderCode, callbackUrl: string): SocialProviderSettingView {
        // `label` 은 화면에 보이지 않는다(이 틀은 입력 규칙을 쓰려는 것) — 이름은 `providerLabel(t, …)` 로 그린다
        return { provider, label: provider, callbackUrl, source: 'NONE', enabled: false, clientId: null, secretSet: false, updatedAt: null };
    }

    /* ── 루트 계정 ── */

    /**
     * 루트 계정 입력을 미리 검사한다. 닉네임은 예약어를 검사하지 않는다("운영자" 같은 이름을 쓸 수 있다 — 중복은 서버가 본다).
     * @param {RootForm} form - 입력값.
     * @returns {RootProblems} 문제가 있는 칸과 까닭. 비어 있으면 통과.
     */
    static validateRoot(form: RootForm): RootProblems {
        const problems: RootProblems = {};
        const email = UtilValidation.email(form.email);
        if (email) problems.email = email;
        const nickname = UtilValidation.nickname(form.nickname);
        if (nickname) problems.nickname = nickname;
        if (!form.password) problems.password = 'REQUIRED';
        else {
            // 서버 `UserPolicy.passwordViolation` 순서: 길이 → UTF-8 바이트 → 영문+숫자 (하나만 알린다)
            const check = UtilValidation.password(form.password);
            if (!check.length) problems.password = 'RULE';
            else if (!check.bytes) problems.password = 'TOO_LONG';
            else if (!check.letterAndDigit) problems.password = 'RULE';
        }
        if (!form.passwordConfirm) problems.passwordConfirm = 'REQUIRED';
        else if (form.passwordConfirm !== form.password) problems.passwordConfirm = 'MISMATCH';
        return problems;
    }

    /**
     * 서버로 보낼 루트 계정 — 이메일 앞뒤 공백을 자른다(비밀번호 확인 칸은 보내지 않는다).
     * @param {RootForm} form - 입력값.
     * @returns {InstallRootInput} 요청의 `root`.
     */
    static rootInput(form: RootForm): InstallRootInput {
        return { email: form.email.trim(), nickname: form.nickname, password: form.password };
    }

    /* ── 소셜 로그인 키 ── */

    /**
     * 이 공급자를 쓰려는가 — 켜 둔 것만 쓴다. 꺼 둔 공급자는 입력해 둔 값이 있어도 보내지도 검사하지도 않는다(다시 켜면 값이 그대로 돌아온다).
     * @param {SocialForm} form - 공급자 한 곳의 입력값.
     * @returns {boolean} 켜 두었으면 true.
     */
    static socialUsed(form: SocialForm): boolean {
        return form.enabled;
    }

    /**
     * 요청에 담길 공급자들 — 켜 둔 것만, 서버 순서(네이버·카카오·구글)로. 서버 오류의 `social.providers[N]` 이 가리키는 N 번째가 이 순서다.
     * @param {SocialForms} forms - 공급자별 입력값.
     * @returns {SocialProviderCode[]} 입력한 공급자 코드들.
     */
    static usedProviders(forms: SocialForms): SocialProviderCode[] {
        return UtilInstall.PROVIDERS.filter((code) => UtilInstall.socialUsed(forms[code]));
    }

    /**
     * 켜 둔 공급자를 미리 검사한다 — 규칙은 소셜 설정 화면과 같다(클라이언트 ID·시크릿 필수, 길이 한도, 공백 불가). 켜 놓고 비워 두면 필수 오류다.
     * @param {SocialForms} forms - 공급자별 입력값.
     * @returns {SocialFormProblems} 공급자별 문제. 꺼 둔 공급자는 없다. 비어 있으면 통과.
     */
    static validateSocial(forms: SocialForms): SocialFormProblems {
        const out: SocialFormProblems = {};
        for (const code of UtilInstall.usedProviders(forms)) {
            const problems = UtilAdminSocial.validate(forms[code], UtilInstall.newSocialSetting(code, ''));
            if (Object.keys(problems).length) out[code] = problems;
        }
        return out;
    }

    /**
     * 서버로 보낼 소셜 로그인 키 — 켜 둔 공급자만 담는다. 하나도 켜지 않았으면 null(건너뜀과 같다).
     * @param {SocialForms} forms - 공급자별 입력값(미리 검사를 통과한 것).
     * @returns {InstallSocialInput | null} 요청의 `social`. 켜 둔 공급자가 없으면 null.
     */
    static socialInput(forms: SocialForms): InstallSocialInput | null {
        const providers = UtilInstall.usedProviders(forms).map((code) => {
            const request = UtilAdminSocial.toRequest(forms[code]);
            return { provider: code, clientId: request.clientId, clientSecret: request.clientSecret ?? '', enabled: request.enabled };
        });
        return providers.length ? { providers } : null;
    }

    /* ── 요청 ── */

    /**
     * 설치 요청 — 건너뛴 메일·소셜은 `null` 이다.
     * @param {InstallFormState} state - 입력 전체(미리 검사를 통과한 것).
     * @returns {InstallCompleteRequest} `POST …/complete` 의 요청 본문.
     */
    static buildRequest(state: InstallFormState): InstallCompleteRequest {
        return {
            root: UtilInstall.rootInput(state.root),
            site: UtilSite.toInput(state.site),
            mail: state.mailMode === 'set' ? UtilMail.toInput(state.mail) : null,
            social: state.socialMode === 'set' ? UtilInstall.socialInput(state.social) : null,
            ...(state.boardPreset ? { boardPreset: state.boardPreset } : {}),
        };
    }

    /* ── 점검 결과 ── */

    /**
     * 점검 항목의 갈래 — 통과 · 실패(`blocking` 이 아니면 경고).
     * @param {InstallCheck} check - 점검 항목.
     * @returns {CheckTone} `ok` · `warn`(실패했지만 설치는 계속할 수 있음) · `fail`(설치를 막음).
     */
    static checkTone(check: InstallCheck): CheckTone {
        if (check.ok) return 'ok';
        return check.blocking === false ? 'warn' : 'fail';
    }

    /**
     * 설치를 계속할 수 있는가 — 막는(`blocking`) 실패가 하나도 없어야 한다. 경고(암호화 키 없음)는 계속할 수 있다.
     * @param {InstallChecksView | null} view - 점검 결과. 아직 없으면 null.
     * @returns {boolean} 계속할 수 있으면 true. 점검 결과가 없거나 항목이 비어 있으면 false.
     */
    static canProceed(view: InstallChecksView | null): boolean {
        return view !== null && view.checks.length > 0 && view.checks.every((c) => UtilInstall.checkTone(c) !== 'fail');
    }

    /**
     * 암호화 키가 있는가 — 없으면 메일 비밀번호·소셜 시크릿을 저장할 수 없어 그 입력을 잠근다. 점검에 이 항목이 없으면 있는 것으로 본다.
     * @param {InstallChecksView | null} view - 점검 결과. 아직 없으면 null.
     * @returns {boolean} 암호화 키 점검이 실패하지 않았으면 true.
     */
    static cryptoKeyOk(view: InstallChecksView | null): boolean {
        return view?.checks.find((c) => c.key === 'CRYPTO_KEY')?.ok !== false;
    }

    /* ── 서버 오류 ── */

    /**
     * 이미 설치됐다(`409 ALREADY_INSTALLED`) — 마법사는 닫혔다. 홈으로 보낸다.
     * @param {unknown} e - 잡은 오류.
     * @returns {boolean} 이미 설치됐다는 오류이면 true.
     */
    static isAlreadyInstalled(e: unknown): boolean {
        return e instanceof ApiError && e.status === 409 && e.code === 'ALREADY_INSTALLED';
    }

    /**
     * 설치 인증이 없다(`401 INSTALL_AUTH_REQUIRED`) — 토큰을 맞히지 않았거나 세션이 만료됐다(30분 유휴). 1단계로 되돌린다.
     * @param {unknown} e - 잡은 오류.
     * @returns {boolean} 설치 인증이 필요하다는 오류이면 true.
     */
    static isAuthRequired(e: unknown): boolean {
        return e instanceof ApiError && e.status === 401 && e.code === 'INSTALL_AUTH_REQUIRED';
    }

    /**
     * 토큰을 여러 번 틀려 잠겼다(`429 INSTALL_TOKEN_LOCKED`, `retryAfterSeconds` 가 함께 온다).
     * @param {unknown} e - 잡은 오류.
     * @returns {boolean} 토큰 입력이 잠겼다는 오류이면 true.
     */
    static isTokenLocked(e: unknown): boolean {
        return e instanceof ApiError && e.status === 429 && e.code === 'INSTALL_TOKEN_LOCKED';
    }

    /**
     * 그 이메일로 이미 계정이 있다(`409 EMAIL_TAKEN`) — 비정상 상황이지만 루트 계정 이메일 칸에 알린다.
     * @param {unknown} e - 잡은 오류.
     * @returns {boolean} 이메일이 이미 있다는 오류이면 true.
     */
    static isEmailTaken(e: unknown): boolean {
        return e instanceof ApiError && e.status === 409 && e.code === 'EMAIL_TAKEN';
    }

    /**
     * 서버 `fields` 의 경로를 화면이 쓰는 경로로 바꾼다 — `social.providers[N].x` 의 N 번째를 **요청에 담은 공급자 순서**로 풀어 `social.NAVER.x` 로.
     * 그 밖의 경로(`root.email` · `site.publicUrl` · `mail.port` …)는 그대로 둔다. 가리키는 공급자를 못 찾으면 `social` 로 묶는다.
     * @param {Record<string, string> | undefined} fields - 서버 오류의 `fields`.
     * @param {SocialProviderCode[]} sent - 요청에 담은 공급자들(`usedProviders` 순서).
     * @returns {InstallFieldErrors} 칸 경로 → 서버 문구.
     */
    static normalizeFields(fields: Record<string, string> | undefined, sent: SocialProviderCode[]): InstallFieldErrors {
        const out: InstallFieldErrors = {};
        for (const [key, message] of Object.entries(fields ?? {})) {
            const match = SOCIAL_PATH.exec(key);
            if (!match) {
                out[key] = message;
                continue;
            }
            const code = sent[Number(match[1])];
            out[code ? `social.${code}.${match[2]}` : 'social'] = message;
        }
        return out;
    }

    /**
     * 칸 이름 → 문구를 칸 경로 → 문구로 — `email` → `root.email`. 문구가 없는 칸은 뺀다.
     * @param {string} prefix - 접두어(`root` · `site` · `mail` · `social.NAVER`).
     * @param {Record<string, string | undefined>} errors - 칸 이름 → 문구.
     * @returns {InstallFieldErrors} 칸 경로 → 문구.
     */
    static prefixErrors(prefix: string, errors: Record<string, string | undefined>): InstallFieldErrors {
        const out: InstallFieldErrors = {};
        for (const [name, message] of Object.entries(errors)) if (message) out[`${prefix}.${name}`] = message;
        return out;
    }

    /**
     * 오류 경로에서 접두어 아래의 칸 이름만 뽑는다 — 입력 컴포넌트가 자기 칸의 문구만 받게.
     * @param {InstallFieldErrors} errors - 칸 경로 → 문구.
     * @param {string} prefix - 접두어(`root` · `site` · `mail` · `social.NAVER`).
     * @returns {Record<string, string>} 접두어를 뗀 칸 이름 → 문구(`root.email` → `email`).
     */
    static sliceErrors(errors: InstallFieldErrors, prefix: string): Record<string, string> {
        const out: Record<string, string> = {};
        const head = `${prefix}.`;
        for (const [key, message] of Object.entries(errors)) if (key.startsWith(head)) out[key.slice(head.length)] = message;
        return out;
    }

    /**
     * 오류 경로가 속한 단계 — `token` · `root.*` · `site.*` · `mail.*` · `social*` · `boardPreset`.
     * @param {string} key - 칸 경로.
     * @returns {InstallStepKey | null} 속한 단계. 알 수 없는 경로면 null.
     */
    static stepOfKey(key: string): InstallStepKey | null {
        const head = key.split(/[.[]/, 1)[0];
        if (head === 'boardPreset') return 'boards';
        return head === 'token' || head === 'root' || head === 'site' || head === 'mail' || head === 'social' ? head : null;
    }

    /**
     * 오류가 있는 가장 앞 단계 — 서버가 거절하면 그 단계로 돌아간다.
     * @param {InstallFieldErrors} errors - 칸 경로 → 문구.
     * @returns {InstallStepKey | null} 오류가 있는 가장 앞 단계. 어느 단계에도 속하지 않으면 null.
     */
    static firstStepWithErrors(errors: InstallFieldErrors): InstallStepKey | null {
        const steps = new Set<InstallStepKey>();
        for (const key of Object.keys(errors)) {
            const step = UtilInstall.stepOfKey(key);
            if (step) steps.add(step);
        }
        return STEPS.find((s) => steps.has(s)) ?? null;
    }

    /**
     * 어느 단계에도 속하지 않는 오류 문구들 — 확인 화면에서 한꺼번에 알린다.
     * @param {InstallFieldErrors} errors - 칸 경로 → 문구.
     * @returns {string[]} 단계를 알 수 없는 경로의 문구.
     */
    static unplacedMessages(errors: InstallFieldErrors): string[] {
        return Object.entries(errors)
            .filter(([key]) => UtilInstall.stepOfKey(key) === null)
            .map(([, message]) => message);
    }
}
