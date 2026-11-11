import type { Translate } from '#layers/i18n/utils/util-i18n';
import type { ApiError } from '~/composables/common/useApiClient';
import type { AiMemberRelation, AiModelOption, AiProvider, AiProviderKeyStatus, AiRestingReason } from '~/composables/aimember/api/useAiMemberAdminApi.types';
import UtilAdminAuthMessages from '~/utils/util-admin-auth-messages';

/** AI 회원 화면이 코드로 아는 서버 오류 — 서버 문구가 없을 때 `aiMember.error.<코드>` 를 쓴다 */
const ERROR_CODES: ReadonlySet<string> = new Set([
    'NICKNAME_TAKEN',
    'GROUP_NOT_FOUND',
    'GROUP_NOT_PUBLIC',
    'CRYPTO_KEY_MISSING',
    'BLOCKED_WORD',
    'VALIDATION',
    'AI_MEMBER_NOT_FOUND',
    'AI_DRAFT_NOT_FOUND',
    'AI_MODEL_NOT_FOUND',
    'AI_MODEL_IN_USE',
    'AI_MODEL_DEFAULT_LOCKED',
]);

/** AI 회원 관리 화면의 계산이 낀 문구(i18n.md 3절) */
export default class UtilAiMemberMessages {
    /** 상태 꼬리표 — 쉬는 이유가 없으면 「활동 중」, 키 없음은 제공사 이름을 넣는다(「쉬는 중 · Google 키 없음」) */
    static status(t: Translate, reason: AiRestingReason | null, provider: AiProvider): string {
        if (!reason) return t('aiMember.active');
        return t(`aiMember.resting.${reason}`, { provider: t(`aiMember.provider.${provider}`) });
    }

    /** 가격 한 칸 — 「$0.10 / $0.50」(100만 토큰당 USD). 둘 다 없으면 「—」 */
    static price(t: Translate, input: number | null, output: number | null): string {
        if (input == null && output == null) return '—';
        return t('aiMember.modelPrice', { input: UtilAiMemberMessages.usd(input), output: UtilAiMemberMessages.usd(output) });
    }

    /** 계정 상세의 모델 고르기 한 줄 — 「gpt-6-luna · $0.10 / $0.50 · 무료 · 기본」(값이 없는 꼬리는 뺀다) */
    static modelOption(t: Translate, model: AiModelOption): string {
        const parts = [model.modelId];
        if (model.priceInput != null || model.priceOutput != null) parts.push(UtilAiMemberMessages.price(t, model.priceInput, model.priceOutput));
        if (model.freeTier) parts.push(t('aiMember.modelFree'));
        if (model.defaultModel) parts.push(t('aiMember.modelOptionDefault'));
        return parts.join(' · ');
    }

    private static usd(value: number | null): string {
        if (value == null) return '—';
        // 0.1 → $0.10 · 10 → $10.00 · 0.075 → $0.075 — 달러 표기라 숫자 모양은 화면 언어와 상관없이 en-US
        return `$${Number(value).toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 4 })}`;
    }

    /** 키 줄 아래 한 줄 — 쉬는 중이면 시각 · 제공사 메시지 · 쉬는 계정 수 · 다시 돌리는 방법 */
    static keyPaused(t: Translate, status: AiProviderKeyStatus, at: string, detail: string | null, accounts: number): string {
        if (status === 'OK') return '';
        const hint = t(`aiMember.keyPausedHint.${status}`);
        return detail ? t('aiMember.keyPausedLine', { at, detail, n: accounts, hint }) : t('aiMember.keyPausedLineNoDetail', { at, n: accounts, hint });
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
