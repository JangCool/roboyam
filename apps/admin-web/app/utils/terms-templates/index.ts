import type { TermsKind } from '~/composables/terms/api/useTermsApi.types';
import operationPolicy from '~/utils/terms-templates/operation-policy';
import privacy from '~/utils/terms-templates/privacy';
import terms from '~/utils/terms-templates/terms';

/**
 * 약관 종류 → 기본 문안 — 관리자 약관 화면의 [기본 문안으로 초안 만들기]가 편집기에 넣는다([초안 저장]을 눌러야 남는다).
 * 문안 파일(`terms.ts`·`privacy.ts`·`operation-policy.ts`)은 자리표시(`{{운영자}}` 등 — `UtilTermsPlaceholder`)를 담은 서식 HTML 이다.
 * 로보티콘 상품화 계약(`ROBOTIKON_CONTRACT`)은 기본 문안이 없다(단추를 숨긴다).
 */
const TEMPLATES: Partial<Record<TermsKind, string>> = {
    TERMS: terms,
    PRIVACY: privacy,
    OPERATION_POLICY: operationPolicy,
};

export default class UtilTermsTemplates {
    /**
     * 그 종류에 기본 문안이 있는가.
     * @param {TermsKind} kind - 약관 종류.
     * @returns {boolean} 이용약관·개인정보처리방침·운영정책이면 true.
     */
    static has(kind: TermsKind): boolean {
        return Boolean(TEMPLATES[kind]);
    }

    /**
     * 그 종류의 기본 문안.
     * @param {TermsKind} kind - 약관 종류.
     * @returns {string | null} 서식 HTML(없으면 null).
     */
    static of(kind: TermsKind): string | null {
        return TEMPLATES[kind] ?? null;
    }
}
