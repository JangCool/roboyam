import type { JjalCollectRequest, JjalSaveResult, JjalUploadRequest, MyJjalListResponse } from '~/composables/jjal/api/useJjalApi.types';

/**
 * 내 짤 보관함 — 고르기 창 「짤」 탭·짤 메뉴(담기)·올리기 창·내 로보티콘 「내 짤」 칸이 같이 본다. 올리거나 담으면 바로 고르기 창에 보인다.
 * undefined = 아직 안 물어봄 · null = 로그인 안 함 · 값 = 목록(회원이 정한 순서 — 서버 배열 차례 그대로, 개수·상한·처음 안내 확인 여부 포함).
 * 로그인한 사람이 바뀌면(다른 계정·로그아웃) 다시 받는다. 계약 `docs/features/my-jjal-contract.md`.
 */
let inflight: Promise<void> | null = null;

export function useMyJjals() {
    const api = useJjalApi();
    const catalog = useJjalCatalog();
    const { user } = useAuth();
    const list = useState<MyJjalListResponse | null | undefined>('my-jjals', () => undefined);
    /** 받은 목록의 주인 — 로그인한 사람이 바뀌면 다시 받는다 */
    const owner = useState<string | null>('my-jjals-owner', () => null);
    const error = useState<boolean>('my-jjals-error', () => false);

    /** 받은 짤을 그리기 정보(카탈로그)에도 기억시킨다 — 내 짤 토큰을 넣자마자 그림으로 보이게 */
    function rememberAll(value: MyJjalListResponse): void {
        for (const item of value.items) catalog.remember({ id: item.id, url: item.url, width: item.width, height: item.height, animated: item.animated, blocked: item.blocked, collectable: !item.blocked });
    }

    async function load(): Promise<void> {
        const me = user.value?.id ?? null;
        if (!me) {
            list.value = null;
            owner.value = null;
            return;
        }
        if (inflight) return inflight;
        inflight = (async () => {
            try {
                const result = await api.list();
                list.value = result;
                owner.value = me;
                error.value = false;
                rememberAll(result);
            } catch {
                error.value = true;
            } finally {
                inflight = null;
            }
        })();
        return inflight;
    }

    /** 아직 안 받았거나 사람이 바뀌었으면 받는다 */
    function ensureLoaded(): Promise<void> {
        const me = user.value?.id ?? null;
        if (list.value !== undefined && owner.value === me && !(me && list.value === null)) return Promise.resolve();
        return load();
    }

    /** 올리기·담기 결과를 목록에 반영한다(새 짤은 맨 앞 — 계약 「순서」, 개수·상한은 서버 값) */
    function applySaved(result: JjalSaveResult): void {
        catalog.remember({ ...result.item, collectable: !result.item.blocked });
        const current = list.value;
        if (!current) return;
        list.value = { ...current, items: [result.item, ...current.items.filter((i) => i.id !== result.item.id)], count: result.count, limit: result.limit };
    }

    /** 올리기 — 실패는 부르는 쪽이 알린다 */
    async function upload(body: JjalUploadRequest): Promise<JjalSaveResult> {
        const result = await api.upload(body);
        applySaved(result);
        return result;
    }

    /** 담기 — 짤이 보이는 글·댓글과 함께. 실패(이미 있음·꽉 참·내려간 짤·19금·안 쓰인 짤)는 부르는 쪽이 알린다 */
    async function collect(body: JjalCollectRequest): Promise<JjalSaveResult> {
        const result = await api.collect(body);
        applySaved(result);
        return result;
    }

    /** 내 보관함에서 뺀다 — 성공하면 상태에서도 뺀다 */
    async function remove(id: string): Promise<void> {
        await api.remove(id);
        const current = list.value;
        if (current) list.value = { ...current, items: current.items.filter((i) => i.id !== id), count: Math.max(0, current.count - 1) };
    }

    /** 처음 안내 확인 — 계정에 기록한다(성공하면 다시 묻지 않는다) */
    async function acknowledgeIntro(): Promise<void> {
        await api.acknowledgeIntro();
        if (list.value) list.value = { ...list.value, introAcknowledged: true };
    }

    /**
     * 순서 저장 — 보관함 전체 ID 를 새 순서로 한 번에. 성공하면 상태도 그 순서로 바꾼다.
     * 실패(`409` 보관함과 맞지 않음 등)는 던진다 — 부르는 쪽이 알리고 `reload()` 한다.
     * @param ids - 새 순서의 전체 ID(내려간 짤 포함).
     */
    async function saveOrder(ids: string[]): Promise<void> {
        await api.saveOrder({ ids: [...ids] });
        const current = list.value;
        if (!current) return;
        const byId = new Map(current.items.map((i) => [i.id, i]));
        const ordered = ids.flatMap((id) => (byId.has(id) ? [byId.get(id)!] : []));
        // 그사이 들어온 짤(다른 탭 등)이 있으면 맨 앞에 남긴다
        const rest = current.items.filter((i) => !ids.includes(i.id));
        list.value = { ...current, items: [...rest, ...ordered] };
    }

    return { list, error, ensureLoaded, reload: load, upload, collect, remove, acknowledgeIntro, saveOrder };
}
