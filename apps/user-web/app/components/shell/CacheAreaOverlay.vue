<template>
    <!--
        캐시 영역 겹침 층 — 관리자 캐시 관리(admin-web `/settings/caches`)의 「미리보기」 iframe 안에서만 켜진다(`useAdminPreview().cacheMode`, 주소 `cachePreview=1`). 공개 화면에는 그려지지 않는다.
        화면에서 `data-cache-area` 가 붙은 요소(`util-cache-area.ts` 머리의 표)를 찾아, 관리자가 고른 캐시(`keys`) 또는 모든 영역(`showAll`)의 위치에 테두리 상자와 이름표를 겹쳐 그린다.
        한 요소에 키가 여럿이면(공백 구분) 상자는 하나, 이름표는 보이는 키마다 하나씩이고 이름표를 누르면 그 키를 고른다(상자 몸통은 첫 키).
        위치는 그리는 동안 매 프레임 다시 잰다 — 스크롤·창 크기·늦게 뜨는 가젯·붙는 메뉴·열린 알림 패널을 그대로 따라간다. 겹친 영역은 작은 것이 위라 안쪽 상자를 누를 수 있다.
        상자를 누르면 관리자에게 그 키를 알리고(`irobit:cache-preview-pick`), 찾은 영역 수나 로그인 상태가 바뀌면 키마다 수와 로그인 여부를 알린다(`irobit:cache-preview-areas` { counts, loggedIn } — 「이 쪽에는 이 영역이 없습니다」·「로그인한 회원에게만」 판정).
        강조할 키가 새로 오면 그 첫 영역으로 스크롤한다. 캐시하지 않는 가젯(`nocache:<종류>`)은 점선 회색 상자다. 이 층의 눌림은 미리보기 클릭 막기(`plugins/03.admin-preview.client.ts`)가 건너뛴다(`data-cache-overlay`).
    -->
    <div v-if="cacheMode && cache" data-cache-overlay class="pointer-events-none fixed inset-0 z-[2950] overflow-hidden" data-testid="cache-area-overlay">
        <div
            v-for="box in boxes"
            :key="box.id"
            role="button"
            tabindex="-1"
            class="cache-area-box pointer-events-auto absolute"
            :class="[box.nocache ? 'cache-area-box--nocache' : '', box.emphasis ? 'cache-area-box--on' : '', box.off ? 'cache-area-box--off' : '']"
            :style="{ left: `${box.left}px`, top: `${box.top}px`, width: `${box.width}px`, height: `${box.height}px` }"
            :title="box.keys.join(' · ')"
            :data-cache-box="box.keys.join(' ')"
            @click.stop.prevent="pick(box.keys[0] ?? '')"
        >
            <span class="cache-area-labels" :style="{ top: `${box.labelTop}px` }">
                <button
                    v-for="label in box.labels"
                    :key="label.key"
                    type="button"
                    tabindex="-1"
                    class="cache-area-label"
                    :class="[label.nocache ? 'cache-area-label--nocache' : '', label.on ? 'cache-area-label--on' : '', label.off ? 'cache-area-label--off' : '']"
                    @click.stop.prevent="pick(label.key)"
                >
                    {{ label.text }}
                </button>
            </span>
        </div>
    </div>
</template>

<script setup lang="ts">
import UtilCacheArea, { CACHE_PREVIEW_AREAS, CACHE_PREVIEW_PICK, type CacheAreaRect } from '~/utils/util-cache-area';

interface BoxLabel {
    key: string;
    text: string;
    nocache: boolean;
    /** 고른 키(모든 영역 보기에서도 진하게) */
    on: boolean;
    /** 꺼진 캐시 */
    off: boolean;
}

interface Box extends CacheAreaRect {
    id: string;
    /** 이 상자에 보이는 키들(요소의 키 중 그릴 것만) */
    keys: string[];
    labels: BoxLabel[];
    nocache: boolean;
    emphasis: boolean;
    /** 보이는 키가 모두 꺼진 캐시 */
    off: boolean;
    /** 이름표 위치 — 상자 위 끝이 창 밖이면 창 안쪽으로 내린다 */
    labelTop: number;
}

const { cacheMode, cache, cacheDataCounts } = useAdminPreview();
/** 이 미리보기 창의 로그인 상태 — 로그인해야 보이는 쪽(내 정보 등) 안내에 쓰라고 관리자에게 함께 알린다 */
const { user } = useAuth();

const boxes = ref<Box[]>([]);
let frame = 0;
let lastSignature = '';
let lastCounts = '';
let lastKeys = '';

/** 요소의 화면 위 사각형 — `display: contents`(자기 상자 없음)는 자식들을 합친다. 안 보이면 null */
function rectOf(el: HTMLElement): CacheAreaRect | null {
    const r = el.getBoundingClientRect();
    if (r.width > 0 && r.height > 0) return { left: r.left, top: r.top, width: r.width, height: r.height };
    if (getComputedStyle(el).display !== 'contents') return null;
    return UtilCacheArea.union(
        Array.from(el.children).map((child) => {
            const c = child.getBoundingClientRect();
            return { left: c.left, top: c.top, width: c.width, height: c.height };
        }),
    );
}

/** 보이는 영역들 — 이 층 안의 요소는 뺀다 */
function measure(): { keys: string[]; el: HTMLElement; rect: CacheAreaRect }[] {
    const out: { keys: string[]; el: HTMLElement; rect: CacheAreaRect }[] = [];
    for (const el of Array.from(document.querySelectorAll<HTMLElement>('[data-cache-area]'))) {
        if (el.closest('[data-cache-overlay]')) continue;
        const keys = UtilCacheArea.keysOf(el.dataset.cacheArea);
        if (!keys.length) continue;
        const rect = rectOf(el);
        if (rect) out.push({ keys, el, rect });
    }
    return out;
}

function tick(): void {
    frame = 0;
    const msg = cache.value;
    if (!cacheMode.value || !msg) {
        boxes.value = [];
        return;
    }
    const found = measure();

    // 찾은 영역 수(키마다) — 바뀔 때만 알린다
    // 데이터로 센 키(숨겨 둔 사이트 팝업 등)는 그 수를 쓴다 — 0 이면 뺀다
    const counts = UtilCacheArea.merge(UtilCacheArea.counts(found.flatMap((f) => f.keys)), cacheDataCounts.value);
    const loggedIn = user.value != null;
    const countsSig = JSON.stringify({ counts, loggedIn });
    if (countsSig !== lastCounts) {
        lastCounts = countsSig;
        postToAdminPreview({ type: CACHE_PREVIEW_AREAS, counts, loggedIn });
    }

    // 새로 고른 키면 그 첫 영역으로 스크롤한다(모든 영역 보기에서는 옮기지 않는다)
    const keysSig = `${msg.showAll}|${msg.keys.join(',')}`;
    if (keysSig !== lastKeys) {
        const first = !msg.showAll ? found.find((f) => f.keys.some((k) => msg.keys.includes(k))) : undefined;
        if (first || msg.showAll || !msg.keys.length) lastKeys = keysSig;
        if (first) first.el.scrollIntoView({ block: 'center', behavior: 'smooth' });
    }

    const next: Box[] = [];
    found.forEach((f, i) => {
        const keys = f.keys.filter((k) => UtilCacheArea.shown(k, msg));
        if (!keys.length) return;
        const labels = keys.map((key) => ({ key, text: msg.labels[key] ?? key, nocache: UtilCacheArea.isNoCache(key), on: msg.showAll && msg.keys.includes(key), off: msg.off.includes(key) }));
        next.push({
            ...f.rect,
            id: `${keys.join('+')}#${i}`,
            keys,
            labels,
            nocache: keys.every((k) => UtilCacheArea.isNoCache(k)),
            emphasis: labels.some((l) => l.on),
            off: labels.every((l) => l.off),
            labelTop: Math.min(Math.max(0, -f.rect.top), Math.max(0, f.rect.height - 20)),
        });
    });
    // 큰 상자를 먼저(아래에) 그려 안쪽 작은 상자를 누를 수 있게
    next.sort((a, b) => b.width * b.height - a.width * a.height);
    const signature = next.map((b) => `${b.id}:${Math.round(b.left)},${Math.round(b.top)},${Math.round(b.width)},${Math.round(b.height)},${b.labels.map((l) => `${l.text}${l.on}`).join('/')}`).join('|');
    if (signature !== lastSignature) {
        lastSignature = signature;
        boxes.value = next;
    }
    frame = requestAnimationFrame(tick);
}

function pick(key: string): void {
    if (key) postToAdminPreview({ type: CACHE_PREVIEW_PICK, key });
}

watch(
    cache,
    () => {
        // 새 값이 오면 수를 다시 알린다(관리자가 쪽을 바꿔 다시 띄웠을 때 등)
        lastCounts = '';
        if (!frame) frame = requestAnimationFrame(tick);
    },
    { immediate: true },
);

onBeforeUnmount(() => {
    if (frame) cancelAnimationFrame(frame);
    frame = 0;
});
</script>

<style scoped>
.cache-area-box {
    border: 2px solid var(--p-primary-color);
    border-radius: 6px;
    background: color-mix(in srgb, var(--p-primary-color) 7%, transparent);
    cursor: pointer;
}
.cache-area-box:hover {
    background: color-mix(in srgb, var(--p-primary-color) 14%, transparent);
}
.cache-area-box--on {
    border-width: 3px;
    border-color: #f59e0b;
    background: color-mix(in srgb, #f59e0b 12%, transparent);
}
.cache-area-box--nocache {
    border-style: dashed;
    border-color: #9ca3af;
    background: color-mix(in srgb, #9ca3af 8%, transparent);
}
.cache-area-box--off {
    border-style: dashed;
    border-color: #9ca3af;
    background: repeating-linear-gradient(135deg, color-mix(in srgb, #9ca3af 10%, transparent) 0 6px, transparent 6px 12px);
}
.cache-area-box--off.cache-area-box--on {
    border-color: #f59e0b;
}
.cache-area-labels {
    position: absolute;
    left: 0;
    display: flex;
    max-width: 100%;
    flex-wrap: wrap;
    gap: 2px;
}
.cache-area-label {
    max-width: 100%;
    overflow: hidden;
    padding: 2px 6px;
    border: 0;
    border-radius: 0 0 6px 0;
    background: var(--p-primary-color);
    color: var(--p-primary-contrast-color);
    cursor: pointer;
    font-size: 11px;
    line-height: 1.4;
    text-overflow: ellipsis;
    white-space: nowrap;
}
.cache-area-label--on {
    background: #f59e0b;
    color: #111827;
}
.cache-area-label--off {
    background: #9ca3af;
    color: #111827;
}
.cache-area-label--nocache {
    background: #6b7280;
    color: #fff;
}
</style>
