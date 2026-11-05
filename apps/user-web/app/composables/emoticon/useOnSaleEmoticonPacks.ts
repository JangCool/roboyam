import type { EmoticonPackCard } from '~/composables/emoticon/api/useEmoticonApi.types';

/**
 * 상점의 **판매 중** 팩 전부 — 운영자 회원 계정(`useMyEmoticons().allPacks`)만 쓴다(고르기 창이 가진 팩 다음에 「운영자」 표시로 더 보이고, 미리 검사가 가진 것처럼 통과시킨다).
 * 원천은 상점 목록(`GET /api/emoticons/packs` — 판매 중인 팩만, 키셋 쪽)을 끝까지 넘겨 받은 것이고, 로보티콘 그림은 카탈로그(`useEmoticonCatalog` — 팩 ID 로 묶는다)에서 찾는다.
 * 판매 중단 팩은 목록에 오지 않으므로 일반 규칙(가진 사람만)이 그대로다. 처음 필요할 때 한 번 받아 메모리에 둔다. 못 받으면 빈 목록으로 두고 다음 필요할 때 다시 받는다.
 * undefined = 아직 안 받음 · 배열 = 받은 팩(최신순).
 */
let inflight: Promise<void> | null = null;

/** 쪽을 넘기는 상한 — 서버가 커서를 끝없이 주는 일이 있어도 멈춘다 */
const MAX_PAGES = 50;

export function useOnSaleEmoticonPacks() {
    const api = useEmoticonApi();
    const packs = useState<EmoticonPackCard[] | undefined>('emoticon-on-sale-packs', () => undefined);

    function load(): Promise<void> {
        if (inflight) return inflight;
        inflight = (async () => {
            try {
                const out: EmoticonPackCard[] = [];
                let cursor: string | undefined;
                for (let page = 0; page < MAX_PAGES; page++) {
                    const result = await api.packs({ sort: 'LATEST', cursor });
                    out.push(...result.items);
                    if (!result.nextCursor) break;
                    cursor = result.nextCursor;
                }
                packs.value = out;
            } catch {
                // 못 받았다 — 미리 검사는 막지 않고(받기 전과 같다) 고르기 창은 가진 팩만. 다음 필요할 때 다시 받는다
            } finally {
                inflight = null;
            }
        })();
        return inflight;
    }

    /** 아직 안 받았으면 받는다(같이 부르면 한 번만) */
    function ensureLoaded(): Promise<void> {
        if (packs.value !== undefined) return Promise.resolve();
        return load();
    }

    /** 판매 중 팩인가 — 받기 전이면 false(부르는 쪽이 `packs` 가 undefined 인지 먼저 본다) */
    function onSale(packId: string): boolean {
        return Boolean(packs.value?.some((p) => p.id === packId));
    }

    return { packs, ensureLoaded, reload: load, onSale };
}
