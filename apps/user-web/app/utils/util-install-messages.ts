import type { Translate } from '#layers/i18n/utils/util-i18n';
import type { InstallCheck } from '~/composables/install/api/useInstallApi.types';
import UtilAdminSocialMessages from '~/utils/util-admin-social-messages';
import UtilAuthMessages from '~/utils/util-auth-messages';
import UtilInstall, { type InstallFieldErrors, type InstallFormState, type InstallStepKey, type RootProblems, type SocialFormProblems } from '~/utils/util-install';
import type { MailProblems } from '~/utils/util-mail';
import UtilMailMessages from '~/utils/util-mail-messages';
import UtilSiteMessages from '~/utils/util-site-messages';
import type { SiteProblems } from '~/utils/util-site';
import UtilValidation from '~/utils/util-validation';

/**
 * 설치 마법사(`/install`)에 보이는 문구 중 **계산이 끼는 것** — 단계 제목·안내 고르기, 점검 결과 설명, 입력 오류, 확인 화면의 요약.
 * **문구 본문은 `i18n/locales/{ko,en}/install.ts`** 에 있고 여기는 "어느 키를 고를지"만 안다. 상수 문구는 컴포넌트가 `t('install.xxx')` 를 바로 부른다.
 * 번역 함수 `t` 를 **첫 인자로 받는다**(`.claude/guides/frontend/i18n.md` — 메시지 유틸 변환 규약).
 *
 * **서버 `message`·`fields` 가 있으면 그것을 먼저 쓴다**(frontend-guide 6-1) — 여기 있는 것은 상태 설명·입력 안내·화면이 미리 검사한 오류의 문구다.
 * 분기는 문구가 아니라 코드로 한다. ⚠ **토큰·비밀번호·시크릿 값은 어떤 문구에도 넣지 않는다**(확인 화면은 "입력함"만).
 */

/** 단계의 제목과 설명 */
export interface InstallStepCopy {
    label: string;
    title: string;
    description: string;
}

/** 확인 화면의 한 줄 — `secret` 이면 값 대신 "입력함"만 보인다 */
export interface ReviewRow {
    label: string;
    value: string;
    secret?: boolean;
}

/** 확인 화면의 한 묶음 — 단계마다 하나. 건너뛴 단계는 `skipped` 이고 줄이 없다 */
export interface ReviewSection {
    step: InstallStepKey;
    title: string;
    skipped: boolean;
    rows: ReviewRow[];
}

/** 단계 → 이름·제목·설명의 `install.*` 키(글자가 아니라 키라서 모듈 상수여도 언어를 따른다) */
const STEP_KEYS: Record<InstallStepKey, { label: string; title: string; description: string }> = {
    token: { label: 'install.stepTokenLabel', title: 'install.stepTokenTitle', description: 'install.stepTokenDescription' },
    root: { label: 'install.stepRootLabel', title: 'install.stepRootTitle', description: 'install.stepRootDescription' },
    site: { label: 'install.stepSiteLabel', title: 'install.stepSiteTitle', description: 'install.stepSiteDescription' },
    mail: { label: 'install.stepMailLabel', title: 'install.stepMailTitle', description: 'install.stepMailDescription' },
    social: { label: 'install.stepSocialLabel', title: 'install.stepSocialTitle', description: 'install.stepSocialDescription' },
    boards: { label: 'install.stepBoardsLabel', title: 'install.stepBoardsTitle', description: 'install.stepBoardsDescription' },
    review: { label: 'install.stepReviewLabel', title: 'install.stepReviewTitle', description: 'install.stepReviewDescription' },
};

export default class UtilInstallMessages {
    /**
     * 단계의 이름·제목·설명.
     * @param {Translate} t - 번역 함수(`useI18n().t`).
     * @param {InstallStepKey} step - 단계.
     * @returns {InstallStepCopy} 진행 표시에 쓰는 이름, 제목, 설명.
     */
    static step(t: Translate, step: InstallStepKey): InstallStepCopy {
        const keys = STEP_KEYS[step];
        return { label: t(keys.label), title: t(keys.title), description: t(keys.description) };
    }

    /**
     * 진행 표시 한 줄 — "2 / 6 · 루트 계정".
     * @param {Translate} t - 번역 함수(`useI18n().t`).
     * @param {InstallStepKey} step - 지금 단계.
     * @returns {string} 진행 표시 글자.
     */
    static progress(t: Translate, step: InstallStepKey): string {
        return t('install.progress', { current: UtilInstall.stepIndex(step) + 1, total: UtilInstall.STEPS.length, label: t(STEP_KEYS[step].label) });
    }

    /**
     * 점검 항목의 한 줄 요약(스크린리더용) — "데이터베이스 연결: 통과".
     * @param {Translate} t - 번역 함수(`useI18n().t`).
     * @param {InstallCheck} check - 점검 항목.
     * @returns {string} 항목 이름과 결과.
     */
    static checkLine(t: Translate, check: InstallCheck): string {
        const tone = UtilInstall.checkTone(check);
        const key = tone === 'ok' ? 'install.checkLineOk' : tone === 'warn' ? 'install.checkLineWarn' : 'install.checkLineFail';
        return t(key, { label: check.label });
    }

    /**
     * 루트 계정 입력 문제의 문구.
     * @param {Translate} t - 번역 함수(`useI18n().t`).
     * @param {RootProblems} problems - 칸별 문제.
     * @param {number} nicknameLength - 입력한 닉네임의 글자 수(1자일 때 "2자 이상"이라고 말한다).
     * @returns {InstallFieldErrors} `root.*` 경로의 문구.
     */
    static rootErrors(t: Translate, problems: RootProblems, nicknameLength: number): InstallFieldErrors {
        const out: Record<string, string | undefined> = {};
        if (problems.email) out.email = UtilAuthMessages.emailProblem(t, problems.email);
        if (problems.nickname) out.nickname = UtilAuthMessages.nicknameProblem(t, problems.nickname, nicknameLength);
        if (problems.password) {
            if (problems.password === 'REQUIRED') out.password = t('install.passwordRequired');
            else if (problems.password === 'TOO_LONG') out.password = t('install.passwordTooLong', { max: UtilValidation.PASSWORD_MAX_BYTES });
            else out.password = t('install.passwordRule', { min: UtilValidation.PASSWORD_MIN, max: UtilValidation.PASSWORD_MAX });
        }
        if (problems.passwordConfirm) out.passwordConfirm = problems.passwordConfirm === 'REQUIRED' ? t('install.passwordConfirmRequired') : t('install.passwordMismatch');
        return UtilInstall.prefixErrors('root', out);
    }

    /**
     * 사이트 입력 문제의 문구.
     * @param {Translate} t - 번역 함수(`useI18n().t`).
     * @param {SiteProblems} problems - 칸별 문제.
     * @returns {InstallFieldErrors} `site.*` 경로의 문구.
     */
    static siteErrors(t: Translate, problems: SiteProblems): InstallFieldErrors {
        return UtilInstall.prefixErrors('site', UtilSiteMessages.fieldErrors(t, problems));
    }

    /**
     * 메일 입력 문제의 문구.
     * @param {Translate} t - 번역 함수(`useI18n().t`).
     * @param {MailProblems} problems - 칸별 문제.
     * @returns {InstallFieldErrors} `mail.*` 경로의 문구.
     */
    static mailErrors(t: Translate, problems: MailProblems): InstallFieldErrors {
        return UtilInstall.prefixErrors('mail', UtilMailMessages.fieldErrors(t, problems));
    }

    /**
     * 소셜 로그인 키 입력 문제의 문구 — 문구는 소셜 설정 화면과 같다.
     * @param {Translate} t - 번역 함수(`useI18n().t`).
     * @param {SocialFormProblems} problems - 공급자별 문제.
     * @returns {InstallFieldErrors} `social.<공급자>.*` 경로의 문구.
     */
    static socialErrors(t: Translate, problems: SocialFormProblems): InstallFieldErrors {
        let out: InstallFieldErrors = {};
        for (const code of UtilInstall.PROVIDERS) {
            const found = problems[code];
            if (found) out = { ...out, ...UtilInstall.prefixErrors(`social.${code}`, UtilAdminSocialMessages.fieldErrors(t, found)) };
        }
        return out;
    }

    /**
     * 확인 화면 — 입력한 내용을 단계별로 묶어 보여 준다. **비밀번호·시크릿은 값 대신 "입력함"** 만, 건너뛴 단계는 `skipped`.
     * @param {Translate} t - 번역 함수(`useI18n().t`).
     * @param {InstallFormState} state - 입력 전체.
     * @returns {ReviewSection[]} 루트 계정 · 사이트 · 메일 서버 · 소셜 로그인 · 기본 게시판 구성 순서의 묶음.
     */
    static review(t: Translate, state: InstallFormState): ReviewSection[] {
        const { root, site, mail } = state;
        const preset = state.boardPresets.find((p) => p.id === state.boardPreset);
        const mailSkipped = state.mailMode === 'skip';
        const used = UtilInstall.usedProviders(state.social);
        const socialSkipped = state.socialMode === 'skip' || used.length === 0;
        const filled = t('install.filled');

        const mailRows: ReviewRow[] = [
            { label: t('install.reviewServer'), value: `${mail.host.trim()}:${mail.port.trim()}` },
            { label: t('install.reviewSecurity'), value: UtilMailMessages.securityLabel(t, mail.security) },
            { label: t('mail.usernameLabel'), value: mail.username.trim() || t('install.reviewNoAuth') },
            { label: t('mail.passwordLabel'), value: mail.password !== '' ? filled : t('common.none'), secret: mail.password !== '' },
            { label: t('mail.fromLabel'), value: mail.from.trim() },
        ];
        const socialRows: ReviewRow[] = used.flatMap((code) => {
            const form = state.social[code];
            const provider = UtilInstall.providerLabel(t, code);
            return [
                { label: t('install.reviewSocialClientId', { provider }), value: form.clientId.trim() },
                { label: t('install.reviewSocialSecret', { provider }), value: filled, secret: true },
            ];
        });

        return [
            {
                step: 'root',
                title: t(STEP_KEYS.root.title),
                skipped: false,
                rows: [
                    { label: t('install.emailLabel'), value: root.email.trim() },
                    { label: t('install.nicknameLabel'), value: root.nickname },
                    { label: t('install.passwordLabel'), value: filled, secret: true },
                ],
            },
            {
                step: 'site',
                title: t(STEP_KEYS.site.title),
                skipped: false,
                rows: [
                    { label: t('site.nameLabel'), value: site.name.trim() },
                    { label: t('install.reviewTagline'), value: site.tagline.trim() || t('site.taglineNone') },
                    { label: t('site.publicUrlLabel'), value: site.publicUrl.trim() },
                ],
            },
            { step: 'mail', title: t(STEP_KEYS.mail.title), skipped: mailSkipped, rows: mailSkipped ? [] : mailRows },
            { step: 'social', title: t(STEP_KEYS.social.title), skipped: socialSkipped, rows: socialSkipped ? [] : socialRows },
            {
                step: 'boards',
                title: t(STEP_KEYS.boards.title),
                skipped: false,
                rows: [{ label: t('install.reviewPreset'), value: preset?.name ?? t('install.boardsServerDefault') }],
            },
        ];
    }
}
