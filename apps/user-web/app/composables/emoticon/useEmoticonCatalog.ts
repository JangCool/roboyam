import type { RichEmoticonImage, RichEmoticonResolve } from '#layers/rich-editor/utils/util-rich-emoticon';
import type { RichEmoticonSource } from '#layers/rich-editor/components/RichEditor.types';
import type { EmoticonCatalogItem, EmoticonItem } from '~/composables/emoticon/api/useEmoticonApi.types';

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

    /**
     * 고르기 창에서 고른 로보티콘의 그림(내 팩 API 가 준 값) — 카탈로그를 아직 안 받았거나 받은 사본이 낡아(브라우저 5분 캐시 · 앱을 연 뒤 나온 팩) 그 ID 가 없을 때
     * 에디터 노드·미리보기가 토큰 글자 대신 바로 그림을 그리게 한다(에디터 노드는 만들 때 그림을 찾는다).
     */
    const picked = useState<Record<string, RichEmoticonImage>>('emoticon-catalog-picked', () => ({}));

    /**
     * 고른 로보티콘을 넣기 직전에 부른다 — 카탈로그 사본에 없으면 그림을 기억하고, 카탈로그를 받거나(아직 안 받음) 다시 묻는다(낡은 사본일 수 있다).
     * @param item - 고르기 창의 로보티콘(ID·이름표·그림 주소)
     */
    function remember(item: EmoticonItem): void {
        if (items.value?.[item.id]) return;
        picked.value = { ...picked.value, [item.id]: { url: item.url, label: item.label } };
        void (items.value === undefined ? ensureLoaded() : refresh());
    }

    /** 이모티콘 하나 — 카탈로그, 없으면 고르기 창에서 고른 그림, 그것도 없으면 null(글자 그대로) */
    const resolve: RichEmoticonResolve = (id: string): RichEmoticonImage | null => {
        const item = items.value?.[id];
        return item ? { url: item.url, label: item.label } : (picked.value[id] ?? null);
    };

    /** 에디터에 넘기는 그림 찾기(`RichEditor` 의 `emoticons`) — 카탈로그를 받으면 `version` 이 올라 에디터가 다시 그린다 */
    const editorSource = computed<RichEmoticonSource>(() => ({ resolve, version: version.value }));

    return { items, version, ensureLoaded, refresh, remember, resolve, editorSource };
}
