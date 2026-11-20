import type { RichJjalImage, RichJjalResolve } from '#layers/rich-editor/utils/util-rich-jjal';
import type { RichJjalSource } from '#layers/rich-editor/components/RichEditor.types';
import type { JjalView } from '~/composables/jjal/api/useJjalApi.types';

/**
 * 글·댓글 속 짤의 표시 정보(짤 ID → 그림·크기·내려감) — 글·댓글을 그리는 곳·에디터·짤 메뉴가 같이 쓴다.
 * 본문에 토큰이 있을 때만 그 ID 들을 묻는다(`ensure`) — 이미 아는 ID·묻는 중인 ID 는 다시 묻지 않는다. 못 받으면 토큰은 글자 그대로 보인다(오류를 띄우지 않는다).
 * 내 짤 목록·올리기·담기·고르기 창이 준 값은 `remember` 로 바로 기억한다(넣자마자 그림으로).
 * `version` 은 그림 정보가 바뀔 때마다 오른다 — 에디터·본문이 다시 그리는 신호.
 *
 * **담기 가능(`collectable`)은 글 맥락마다 따로 둔다**: 그림 정보는 짤마다 하나라 전역에 두고, 서버의 `collectable` 은 같은 짤이라도 글(19금·그 글에 쓰였나)마다 달라
 * `글 ID → 짤 ID → 참·거짓` 으로 따로 기억한다(`ensure(ids, postId)` 가 그 글 맥락으로 물을 때 채운다). 맥락 없이 받은 값(에디터·내 짤)은 담기에 쓰지 않는다.
 * 내려간 짤은 서버가 `url: null` 로 준다 — 그리기는 `blocked` 로 본다(주소가 없으면 내려간 짤로 친다).
 */
const pending = new Set<string>();

/** 묻는 중 표시 열쇠 — 맥락 없이 `|id`, 글 맥락이면 `postId|id` */
function pendingKey(id: string, postId: string | null): string {
    return `${postId ?? ''}|${id}`;
}

/** 주소가 없으면 내려간 짤 — 서버는 내려간 짤(공개 위치로 옮기기 전 포함)에 `url: null` 을 준다 */
function normalize(view: JjalView): JjalView {
    const blocked = view.blocked || !view.url;
    return { id: view.id, url: blocked ? null : view.url, width: view.width, height: view.height, animated: view.animated, blocked, collectable: !blocked && view.collectable };
}

export function useJjalCatalog() {
    const api = useJjalApi();
    const t = useTranslate();
    /** id → 표시 정보(그림 — 글 맥락과 상관없다) */
    const items = useState<Record<string, JjalView>>('jjal-catalog', () => ({}));
    /** 글 ID → 짤 ID → 그 글에서 담을 수 있나(서버 `collectable`, 맥락 글을 주고 받은 값) */
    const collectables = useState<Record<string, Record<string, boolean>>>('jjal-catalog-collectable', () => ({}));
    const version = useState<number>('jjal-catalog-version', () => 0);

    /**
     * 모르는 것만 묻는다(같이 불러도 한 번만). 100개가 넘으면 API 가 나눠 묻는다.
     * @param ids - 본문의 짤 ID 들.
     * @param postId - 짤이 보이는 글(글·댓글을 그리는 곳). 주면 그림을 알아도 그 글 맥락의 담기 가능을 모르면 묻는다. 에디터·미리 보기는 주지 않는다.
     */
    async function ensure(ids: readonly string[], postId?: string | null): Promise<void> {
        const context = postId || null;
        const known = context ? collectables.value[context] : null;
        const missing = [...new Set(ids)].filter((id) => (context ? known?.[id] === undefined : !items.value[id]) && !pending.has(pendingKey(id, context)));
        if (missing.length === 0) return;
        missing.forEach((id) => pending.add(pendingKey(id, context)));
        try {
            const result = await api.lookup(missing, context);
            if (result.items.length) {
                const next = { ...items.value };
                for (const raw of result.items) next[raw.id] = normalize(raw);
                items.value = next;
                version.value += 1;
            }
            if (context) {
                // 모르는 ID(빠진 것)는 담기 없음으로 기억한다 — 다시 묻지 않게
                const marks: Record<string, boolean> = { ...collectables.value[context] };
                for (const id of missing) marks[id] = false;
                for (const raw of result.items) marks[raw.id] = normalize(raw).collectable;
                collectables.value = { ...collectables.value, [context]: marks };
            }
        } catch {
            // 못 받았다 — 토큰은 글자로 남는다. 다음 필요할 때 다시 묻는다
        } finally {
            missing.forEach((id) => pending.delete(pendingKey(id, context)));
        }
    }

    /** 이미 가진 값(내 짤·올리기·담기)을 기억한다 — 그림만(담기 가능은 글 맥락으로 받은 값만 쓴다) */
    function remember(view: JjalView): void {
        const next = normalize(view);
        const known = items.value[next.id];
        if (known && known.url === next.url && known.blocked === next.blocked) return;
        items.value = { ...items.value, [next.id]: next };
        version.value += 1;
    }

    /** 짤 하나 — 모르면 null(글자 그대로) */
    function resolve(id: string): JjalView | null {
        return items.value[id] ?? null;
    }

    /** 그 글에서 「내 짤에 담기」를 보일까 — 그 글 맥락으로 받은 서버 값(아직 모르면 false) */
    function collectableAt(id: string, postId: string | null): boolean {
        if (!postId) return false;
        return collectables.value[postId]?.[id] === true;
    }

    /** 그리기용 그림(레이어 `UtilRichJjal` 모양) — 내려간 짤은 주소 없이 `blocked`(레이어가 「내려간 짤」 자리로 그린다) */
    const image: RichJjalResolve = (id: string): RichJjalImage | null => {
        const item = items.value[id];
        return item ? { url: item.url ?? '', width: item.width, height: item.height, blocked: item.blocked } : null;
    };

    /** 에디터에 넘기는 그림 찾기(`RichEditor` 의 `jjals`) — 정보를 받으면 `version` 이 올라 에디터가 다시 그린다 */
    const editorSource = computed<RichJjalSource>(() => ({ resolve: image, version: version.value, blockedLabel: t('jjal.blockedLabel') }));

    return { items, version, ensure, remember, resolve, collectableAt, image, editorSource };
}
