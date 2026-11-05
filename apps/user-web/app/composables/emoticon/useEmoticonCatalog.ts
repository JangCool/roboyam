import type { RichEmoticonImage, RichEmoticonResolve } from '#layers/rich-editor/utils/util-rich-emoticon';
import type { RichEmoticonSource } from '#layers/rich-editor/components/RichEditor.types';
import type { EmoticonCatalogItem } from '~/composables/emoticon/api/useEmoticonApi.types';

/**
 * 이모티콘 카탈로그(모든 이모티콘 id → 그림) — 글·댓글을 그리는 곳과 에디터가 같이 쓴다. 처음 필요할 때 한 번 받아 메모리에 둔다(`GET /api/emoticons/catalog`).
 * 토큰이 든 본문을 그릴 때만 `ensureLoaded()` 를 부른다(토큰이 없는 화면은 받지 않는다). 못 받으면 토큰은 글자 그대로 보인다(오류를 띄우지 않는다).
 * `version` 은 내용이 바뀔 때마다 오른다 — 에디터가 이모티콘 노드를 다시 그리는 신호(`RichEmoticonSource.version`).
 * 받은 사본의 `ETag` 를 함께 둔다 — `refresh()` 는 `If-None-Match` 로 물어 `304` 면 가진 사본을 그대로 쓴다(앱을 연 뒤 상점에 새로 나온 팩을 받았을 때 등).
 */
let inflight: Promise<void> | null = null;

export function useEmoticonCatalog() {
    const api = useEmoticonApi();
    /** undefined = 아직 안 받음 · 받았으면 id → 그림 */
    const items = useState<Record<string, EmoticonCatalogItem> | undefined>('emoticon-catalog', () => undefined);
    const version = useState<number>('emoticon-catalog-version', () => 0);
    /** 받은 사본의 `ETag` — 없으면 null(조건부로 묻지 않는다) */
    const etag = useState<string | null>('emoticon-catalog-etag', () => null);

    /** 받는다 — 가진 사본이 있으면 조건부로(같이 부르면 한 번만) */
    function fetchCatalog(): Promise<void> {
        if (inflight) return inflight;
        const known = items.value !== undefined ? etag.value : null;
        inflight = api
            .catalog(known)
            .then((result) => {
                etag.value = result.etag;
                if (!result.catalog) return; // 304 — 가진 사본이 맞다
                const map: Record<string, EmoticonCatalogItem> = {};
                for (const item of result.catalog.items) map[item.id] = item;
                items.value = map;
                version.value += 1;
            })
            .catch(() => {
                // 못 받았다 — 토큰은 글자로 남는다(가진 사본이 있으면 그대로). 다음 필요할 때 다시 받는다
            })
            .finally(() => {
                inflight = null;
            });
        return inflight;
    }

    /** 아직 안 받았으면 받는다(같이 부르면 한 번만) */
    function ensureLoaded(): Promise<void> {
        if (items.value !== undefined) return Promise.resolve();
        return fetchCatalog();
    }

    /** 가진 사본이 맞는지 다시 묻는다(`304` 면 그대로) — 아직 안 받았으면 처음 받기와 같다 */
    function refresh(): Promise<void> {
        return fetchCatalog();
    }

    /** 이모티콘 하나 — 모르면 null(글자 그대로) */
    const resolve: RichEmoticonResolve = (id: string): RichEmoticonImage | null => {
        const item = items.value?.[id];
        return item ? { url: item.url, label: item.label } : null;
    };

    /** 에디터에 넘기는 그림 찾기(`RichEditor` 의 `emoticons`) — 카탈로그를 받으면 `version` 이 올라 에디터가 다시 그린다 */
    const editorSource = computed<RichEmoticonSource>(() => ({ resolve, version: version.value }));

    return { items, version, ensureLoaded, refresh, resolve, editorSource };
}
