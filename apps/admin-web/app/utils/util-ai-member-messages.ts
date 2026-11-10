import type { Translate } from '#layers/i18n/utils/util-i18n';
import type { AiMemberRelation, AiProvider, AiRestingReason } from '~/composables/aimember/api/useAiMemberAdminApi.types';

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
}
