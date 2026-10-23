import type { Translate } from '#layers/i18n/utils/util-i18n';
import type { SocialProviderSettingView } from '~/composables/social/api/useSocialProviderSettingsApi.types';
import type { SocialBadgeTone, SocialFieldErrors, SocialProblem, SocialProblems } from '~/utils/util-social-login';
import UtilSocialLogin from '~/utils/util-social-login';

/**
 * 소셜 로그인 설정 화면의 문구 중 **계산이 끼는 것** — 문구 본문은 `i18n/locales/{ko,en}/social.ts`, 여기는 어느 키를 고를지만 안다. `t` 를 첫 인자로 받는다.
 * 서버 `message`·`fields` 문구는 가공하지 않고 그대로 쓰므로 여기에 없다(`forms.md`). 분기는 코드(`source`·`SocialProblem`)로 한다.
 */

const BADGE_KEYS: Record<SocialBadgeTone, string> = {
    active: 'social.badgeActive',
    inactive: 'social.badgeInactive',
    off: 'social.badgeOff',
    none: 'social.badgeNone',
};

export default class UtilSocialLoginMessages {
    /**
     * 상태 딱지 글자.
     * @param t - 번역 함수.
     * @param tone - 딱지 갈래.
     * @returns 「켜짐」 꼴의 짧은 글자.
     */
    static badge(t: Translate, tone: SocialBadgeTone): string {
        return t(BADGE_KEYS[tone]);
    }

    /**
     * 카드 머리 아래 상태 설명 — 지금 공개 로그인 화면에 버튼이 보이는지와 그 까닭.
     * @param t - 번역 함수.
     * @param setting - 공급자 한 곳의 설정.
     * @param cryptoKeyConfigured - 서버에 암호화 키가 있는가.
     * @returns 한 줄.
     */
    static statusNote(t: Translate, setting: SocialProviderSettingView, cryptoKeyConfigured: boolean): string {
        const name = setting.label;
        if (setting.source === 'NONE') return t('social.statusNone', { name });
        if (!setting.enabled) return t('social.statusOff', { name });
        if (setting.active) return t('social.statusActive', { name });
        if (!cryptoKeyConfigured) return t('social.statusNoCryptoKey', { name });
        return t('social.statusNotConfigured', { name });
    }

    /**
     * 시크릿 칸 아래 안내.
     * @param t - 번역 함수.
     * @param setting - 공급자 한 곳의 설정.
     * @returns 등록 상태에 맞는 한 줄.
     */
    static secretHelp(t: Translate, setting: SocialProviderSettingView): string {
        if (setting.secretSet) return t('social.secretKeep');
        // 행은 있는데 시크릿을 지금 키로 풀 수 없다(키를 바꿈) — 다시 넣어야 저장된다
        if (setting.source === 'SETTINGS') return t('social.secretReenter');
        return t('social.secretNew');
    }

    /**
     * 화면이 미리 검사한 문제의 문구.
     * @param t - 번역 함수.
     * @param field - 어느 칸인지.
     * @param problem - 문제의 갈래.
     * @returns 칸 아래 한 줄.
     */
    static problem(t: Translate, field: 'clientId' | 'clientSecret', problem: SocialProblem): string {
        const max = field === 'clientId' ? UtilSocialLogin.CLIENT_ID_MAX : UtilSocialLogin.CLIENT_SECRET_MAX;
        if (problem === 'REQUIRED') return t(field === 'clientId' ? 'social.clientIdRequired' : 'social.clientSecretRequired');
        if (problem === 'LENGTH') return t(field === 'clientId' ? 'social.clientIdLength' : 'social.clientSecretLength', { max });
        return t(field === 'clientId' ? 'social.clientIdSpace' : 'social.clientSecretSpace');
    }

    /**
     * 미리 검사한 문제들을 칸별 문구로.
     * @param t - 번역 함수.
     * @param problems - 칸별 문제.
     * @returns 칸별 문구.
     */
    static fieldErrors(t: Translate, problems: SocialProblems): SocialFieldErrors {
        const out: SocialFieldErrors = {};
        for (const field of ['clientId', 'clientSecret'] as const) {
            const problem = problems[field];
            if (problem) out[field] = UtilSocialLoginMessages.problem(t, field, problem);
        }
        return out;
    }

    /**
     * 저장했다는 토스트 — 지금 버튼이 보이는지까지.
     * @param t - 번역 함수.
     * @param saved - 서버가 돌려준 그 공급자의 설정.
     * @returns 한 줄.
     */
    static saved(t: Translate, saved: SocialProviderSettingView): string {
        if (saved.active) return t('social.savedActive', { name: saved.label });
        return t(saved.enabled ? 'social.savedInactive' : 'social.savedOff', { name: saved.label });
    }
}
