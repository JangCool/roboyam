import type { KakaoVariableMapping } from '~/composables/template/api/useKakaoTemplateApi.types';
import type { MessageVariableView } from '~/composables/template/api/useNotificationMessageApi.types';

/**
 * 카카오톡 알림톡 템플릿(계약 `docs/features/message-template-contract.md` 「개정 4」 — 준비 중)의 순수 규칙 — 승인 본문의 `#{이름}` 자리를 변수 대응표로 채워 말풍선 미리보기 글을 만든다.
 * 서버 단계에서 실제로 보낼 때도 같은 대응으로 채운다(승인 본문과 글자가 하나라도 다르면 카카오가 거절한다 — 그래서 본문은 화면에서 고치지 않는다).
 */

const PLACEHOLDER_RE = /#\{([^}\n]+)\}/g;

export default class UtilKakaoTemplate {
    /**
     * 승인 본문의 `#{이름}` 자리 이름들(나온 차례, 겹침 없음).
     * @param body - 승인 본문.
     * @returns 자리 이름 목록.
     */
    static placeholdersIn(body: string): string[] {
        const out: string[] = [];
        for (const match of body.matchAll(PLACEHOLDER_RE)) {
            const name = (match[1] as string).trim();
            if (!out.includes(name)) out.push(name);
        }
        return out;
    }

    /**
     * 말풍선 미리보기 글 — `#{이름}` 을 대응하는 로보얌 변수의 견본 값으로(문구 대상의 변수가 있으면 그 견본, 없으면 대응표의 견본). 대응이 없는 자리는 그대로 둔다.
     * @param body - 승인 본문.
     * @param mappings - 변수 대응표.
     * @param variables - 문구 대상의 변수(없으면 빈 배열 — 템플릿 화면).
     * @returns 채운 글(평문 — 그리는 쪽이 글자로 보인다).
     */
    static fill(body: string, mappings: readonly KakaoVariableMapping[], variables: readonly Pick<MessageVariableView, 'name' | 'sample'>[] = []): string {
        const samples = new Map(variables.map((v) => [v.name, v.sample]));
        return body.replace(PLACEHOLDER_RE, (whole, raw: string) => {
            const mapping = mappings.find((item) => item.placeholder === raw.trim());
            if (!mapping) return whole;
            return samples.get(mapping.variable) ?? mapping.sample;
        });
    }

    /**
     * 대응표에서 빠진 자리 — 승인 본문에 있는데 대응하는 변수가 없는 `#{이름}`.
     * @param body - 승인 본문.
     * @param mappings - 변수 대응표.
     * @returns 빠진 자리 이름들.
     */
    static unmapped(body: string, mappings: readonly KakaoVariableMapping[]): string[] {
        return UtilKakaoTemplate.placeholdersIn(body).filter((name) => !mappings.some((item) => item.placeholder === name && item.variable));
    }
}
