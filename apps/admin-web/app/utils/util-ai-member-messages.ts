import type { Translate } from '#layers/i18n/utils/util-i18n';
import type { ApiError } from '~/composables/common/useApiClient';
import type { AiMemberRelation, AiProvider, AiRestingReason } from '~/composables/aimember/api/useAiMemberAdminApi.types';
import UtilAdminAuthMessages from '~/utils/util-admin-auth-messages';

/** AI 회원 화면이 코드로 아는 서버 오류 — 서버 문구가 없을 때 `aiMember.error.<코드>` 를 쓴다 */
const ERROR_CODES: ReadonlySet<string> = new Set(['NICKNAME_TAKEN', 'GROUP_NOT_FOUND', 'GROUP_NOT_PUBLIC', 'CRYPTO_KEY_MISSING', 'BLOCKED_WORD', 'VALIDATION', 'AI_MEMBER_NOT_FOUND', 'AI_DRAFT_NOT_FOUND']);

/** AI 회원 관리 화면의 계산이 낀 문구(i18n.md 3절) */
export default class UtilAiMemberMessages {
    /** 상태 꼬리표 — 쉬는 이유가 없으면 「활동 중」, 키 없음은 제공사 이름을 넣는다(「쉬는 중 · Google 키 없음」) */
    static status(t: Translate, reason: AiRestingReason | null, provider: AiProvider): string {
        if (!reason) return t('aiMember.active');
        return t(`aiMember.resting.${reason}`, { provider: t(`aiMember.provider.${provider}`) });
    }

    static relation(t: Translate, relation: AiMemberRelation | null): string {
        if (!relation) return t('aiMember.relationNone');
        return t('aiMember.relationLine', { name: relation.partner.nickname, kind: t(`aiMember.relationKind.${relation.kind}`) });
    }

    /** 처리 결과 토스트 — 건너뛴 것이 있으면 덧붙인다 */
    static batchDone(t: Translate, key: 'aiMember.published' | 'aiMember.discarded', done: number, skipped: number): string {
        const head = t(key, { n: done });
        return skipped > 0 ? `${head} · ${t('aiMember.skipped', { n: skipped })}` : head;
    }

    /**
     * 오류 한 줄 — 서버 문구가 있으면 그대로(forms.md 2-2). 서버가 문구를 주지 않아 `useApiClient` 가 일반 문구로 채웠으면(본문 없는 503 등) 아는 코드의 문구로 바꾼다.
     * @param {Translate} t - 번역 함수.
     * @param {ApiError} error - `useApiErrorOf()` 로 바꾼 오류.
     * @returns {string} 보일 한 줄.
     */
    static error(t: Translate, error: ApiError): string {
        const generic = !error.message || error.message === UtilAdminAuthMessages.fallback(t, error.status, error.code);
        return generic && ERROR_CODES.has(error.code) ? t(`aiMember.error.${error.code}`) : error.message;
    }
}
