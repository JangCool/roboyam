import type { Translate } from '#layers/i18n/utils/util-i18n';
import type { MeSocialProvider } from '~/composables/auth/api/useAuthApi.types';
import { ApiError } from '~/composables/common/useApiClient';
import UtilAuthMessages from '~/utils/util-auth-messages';
import UtilProvider from '~/utils/util-provider';

/**
 * 계정 흐름(비밀번호 재설정·변경·이메일 변경·이메일 인증) 화면의 **계산이 낀** 문구 — 상수 문구는 화면이 `t('password.*')`·`t('account.*')` 로 바로 부른다.
 * 번역 함수 `t` 를 첫 인자로 받는다(`.claude/guides/frontend/i18n.md`). 원본(community) `utils/util-account-messages.ts` 에서 갈래가 있는 것만 옮겼다.
 *
 * **서버 `message` 가 있으면 그것을 그대로 쓴다** — "현재 비밀번호가 맞지 않아요"·"이미 사용 중인 이메일이에요" 같은 오류 문구는 서버가 정한다. 분기는 문구가 아니라 `code` 로 한다.
 * ⚠ 이 파일의 어디에도 비밀번호 값은 들어가지 않는다.
 */

/** 제목 한 줄 + 설명 — 상태 화면이 같은 모양을 쓴다 */
export interface AccountCopy {
    title: string;
    text: string;
}

export default class UtilAccountMessages {
    /**
     * 서버 문구가 있으면 그대로, 화면 오류(네트워크 등)면 대신 쓸 문구
     * @param {Translate} t - 번역 함수.
     * @param {unknown} e - 잡은 오류.
     * @returns {string} 화면에 보일 한 줄.
     */
    static of(t: Translate, e: unknown): string {
        return e instanceof ApiError ? e.message : UtilAuthMessages.fallback(t, 0, 'NETWORK');
    }

    /**
     * 소셜로 가입한 계정이 비밀번호 변경 화면에 들어왔을 때 — 연결된 소셜이 하나면 그 이름으로 안내한다
     * @param {Translate} t - 번역 함수.
     * @param {readonly MeSocialProvider[]} providers - 소셜 공급자 전부(연결된 것만 고른다).
     * @returns {AccountCopy} 결과 화면의 제목과 설명.
     */
    static passwordNotSet(t: Translate, providers: readonly MeSocialProvider[]): AccountCopy {
        const linked = providers.filter((p) => p.linked).map((p) => UtilProvider.name(t, p.id, p.name));
        const guide = linked.length === 1 ? t('password.socialGuideOne', { provider: linked[0] }) : t('password.socialGuideMany');
        return { title: t('password.socialAccountTitle'), text: `${t('password.notSetText')} ${guide}` };
    }
}
