import type { RichJjalImage, RichJjalResolve } from '#layers/rich-editor/utils/util-rich-jjal';
import type { RichJjalSource } from '#layers/rich-editor/components/RichEditor.types';
import type { JjalView } from '~/composables/jjal/api/useJjalApi.types';

/**
 * 글·댓글 속 짤의 표시 정보(짤 ID → 그림·크기·내려감·담기 가능) — 글·댓글을 그리는 곳·에디터·짤 메뉴가 같이 쓴다.
 * 본문에 토큰이 있을 때만 그 ID 들을 묻는다(`ensure`) — 이미 아는 ID·묻는 중인 ID 는 다시 묻지 않는다. 못 받으면 토큰은 글자 그대로 보인다(오류를 띄우지 않는다).
 * 내 짤 목록·올리기·담기·고르기 창이 준 값은 `remember` 로 바로 기억한다(넣자마자 그림으로).
 * `version` 은 내용이 바뀔 때마다 오른다 — 에디터·본문이 다시 그리는 신호.
 */
const pending = new Set<string>();

export function useJjalCatalog() {
    const api = useJjalApi();
    const t = useTranslate();
    /** id → 표시 정보 */
    const items = useState<Record<string, JjalView>>('jjal-catalog', () => ({}));
    const version = useState<number>('jjal-catalog-version', () => 0);

    /** 모르는 ID 만 묻는다(같이 불러도 한 번만) */
    async function ensure(ids: readonly string[]): Promise<void> {
        const missing = [...new Set(ids)].filter((id) => !items.value[id] && !pending.has(id));
        if (missing.length === 0) return;
        missing.forEach((id) => pending.add(id));
        try {
            const result = await api.lookup(missing);
            if (result.items.length) {
                const next = { ...items.value };
                for (const item of result.items) next[item.id] = item;
                items.value = next;
                version.value += 1;
            }
        } catch {
            // 못 받았다 — 토큰은 글자로 남는다. 다음 필요할 때 다시 묻는다
        } finally {
            missing.forEach((id) => pending.delete(id));
        }
    }

    /** 이미 가진 값(내 짤·올리기·담기)을 기억한다 */
    function remember(view: JjalView): void {
        const known = items.value[view.id];
        if (known && known.url === view.url && known.blocked === view.blocked) return;
        items.value = { ...items.value, [view.id]: { id: view.id, url: view.url, width: view.width, height: view.height, animated: view.animated, blocked: view.blocked, collectable: view.collectable } };
        version.value += 1;
    }

    /** 짤 하나 — 모르면 null(글자 그대로) */
    function resolve(id: string): JjalView | null {
        return items.value[id] ?? null;
    }

    /** 그리기용 그림(레이어 `UtilRichJjal` 모양) */
    const image: RichJjalResolve = (id: string): RichJjalImage | null => {
        const item = items.value[id];
        return item ? { url: item.url, width: item.width, height: item.height, blocked: item.blocked } : null;
    };

    /** 에디터에 넘기는 그림 찾기(`RichEditor` 의 `jjals`) — 정보를 받으면 `version` 이 올라 에디터가 다시 그린다 */
    const editorSource = computed<RichJjalSource>(() => ({ resolve: image, version: version.value, blockedLabel: t('jjal.blockedLabel') }));

    return { items, version, ensure, remember, resolve, image, editorSource };
}
