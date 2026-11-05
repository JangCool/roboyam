import type { EmoticonBodyProblem } from '~/utils/util-emoticon-rule';
import UtilEmoticonRule from '~/utils/util-emoticon-rule';

/**
 * 글·댓글 폼의 로보티콘 미리 막기 — 저장 직전 서버 검사와 **같은 규칙 함수**(`UtilEmoticonRule.check`)를 화면이 가진 사실(카탈로그·내 팩)로 돌린다.
 * 개수(21개부터)는 늘 보고, 자격(가지지 않은 팩)은 카탈로그와 내 팩을 받았을 때만 본다 — 받기 전에는 막지 않는다(판정은 서버 `EmoticonGuard` — 저장 때 `400 VALIDATION fields.content`·`409 EMOTICON_NOT_OWNED`).
 * 로그인하지 않았으면(비회원 글·댓글) 가진 팩이 없다 — 카탈로그에 있는 로보티콘 토큰은 막힌다(서버와 같다).
 * @param content - 지금 본문(읽는 자리에서 푼다)
 */
export function useEmoticonBodyCheck(content: MaybeRefOrGetter<string>) {
    const catalog = useEmoticonCatalog();
    const mine = useMyEmoticons();

    const problem = computed<EmoticonBodyProblem | null>(() => {
        const text = toValue(content);
        const known = catalog.items.value !== undefined && mine.packs.value !== undefined;
        return UtilEmoticonRule.check(text, {
            packOf: (id) => (known ? (catalog.items.value?.[id]?.packId ?? null) : null),
            owns: (packId) => mine.has(packId),
            unlimited: false,
        });
    });

    /** 토큰이 있으면 자격 판단에 필요한 것을 받아 둔다 */
    function prepare(): void {
        void catalog.ensureLoaded();
        void mine.ensureLoaded();
    }

    return { problem, prepare, labelOf: (id: string) => catalog.resolve(id)?.label ?? id };
}
