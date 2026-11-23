<template>
    <!--
        광고 자리 겹침 층 — 관리자 「광고 자리 관리」(admin-web `/ads/slots`)의 미리보기 iframe 안에서만 켜진다(`useAdminPreview().adSlotMode`, 주소 `adSlotPreview=1`). 공개 화면에는 그려지지 않는다.
        화면에서 `data-ad-slot` 이 붙은 요소(`util-ad-slot-area.ts` 머리의 표)를 찾아 테두리 상자와 이름표(관리자가 보낸 「종류 이름 · 진행 중 수」)를 겹치고, 나머지 화면은 흐리게 덮는다(자리만 뚫은 SVG 막).
        빈 자리(`data-ad-slot-empty`)는 점선 상자다. 드로어에 열린 종류(`active`)는 주황으로 진하게. 위치는 그리는 동안 매 프레임 다시 잰다(캐시 영역 겹침 층 `CacheAreaOverlay` 와 같은 방식).
        상자를 누르면 관리자에게 그 종류를 알리고(`irobit:ad-slot-preview-pick`), 찾은 자리 수가 바뀌면 종류마다 수·빈 자리 수를 알린다(`irobit:ad-slot-preview-areas`).
        이 층의 눌림은 미리보기 클릭 막기(`plugins/03.admin-preview.client.ts`)가 건너뛴다(`data-ad-slot-overlay`).
    -->
    <div v-if="adSlotMode && adSlot" data-ad-slot-overlay class="pointer-events-none fixed inset-0 z-[2950] overflow-hidden" data-testid="ad-slot-overlay">
        <svg class="absolute inset-0 size-full" aria-hidden="true">
            <path :d="veil" fill-rule="evenodd" class="ad-slot-veil" />
        </svg>
        <div
            v-for="box in boxes"
            :key="box.id"
            role="button"
            tabindex="-1"
            class="ad-slot-box pointer-events-auto absolute"
            :class="[box.empty ? 'ad-slot-box--empty' : '', box.on ? 'ad-slot-box--on' : '']"
            :style="{ left: `${box.left}px`, top: `${box.top}px`, width: `${box.width}px`, height: `${box.height}px` }"
            :title="box.label"
            :data-ad-slot-box="box.type"
            @click.stop.prevent="pick(box.type)"
        >
            <span class="ad-slot-label" :class="[box.empty ? 'ad-slot-label--empty' : '', box.on ? 'ad-slot-label--on' : '']" :style="{ top: `${box.labelTop}px` }">{{ box.label }}</span>
        </div>
    </div>
</template>

<script setup lang="ts">
import UtilAdSlotArea, { AD_SLOT_PREVIEW_AREAS, AD_SLOT_PREVIEW_PICK } from '~/utils/util-ad-slot-area';

interface Rect {
    left: number;
    top: number;
    width: number;
    height: number;
}

interface Box extends Rect {
    id: string;
    type: string;
    label: string;
    empty: boolean;
    /** 드로어에 열린 종류 */
    on: boolean;
    /** 이름표 위치 — 상자 위 끝이 창 밖이면 창 안쪽으로 내린다 */
    labelTop: number;
}

const { adSlotMode, adSlot } = useAdminPreview();

const boxes = ref<Box[]>([]);
const veil = ref('');
let frame = 0;
let lastSignature = '';
let lastCounts = '';

/** 보이는 광고 자리들 — 이 층 안의 요소는 뺀다 */
function measure(): { type: string; empty: boolean; rect: Rect }[] {
    const out: { type: string; empty: boolean; rect: Rect }[] = [];
    for (const el of Array.from(document.querySelectorAll<HTMLElement>('[data-ad-slot]'))) {
        if (el.closest('[data-ad-slot-overlay]')) continue;
        const type = el.dataset.adSlot;
        if (!UtilAdSlotArea.isType(type)) continue;
        const r = el.getBoundingClientRect();
        if (r.width <= 0 || r.height <= 0) continue;
        out.push({ type, empty: el.hasAttribute('data-ad-slot-empty'), rect: { left: r.left, top: r.top, width: r.width, height: r.height } });
    }
    return out;
}

function tick(): void {
    frame = 0;
    const msg = adSlot.value;
    if (!adSlotMode.value || !msg) {
        boxes.value = [];
        return;
    }
    const found = measure();

    // 찾은 자리 수(종류마다) — 바뀔 때만 알린다
    const counts = UtilAdSlotArea.counts(found);
    const countsSig = JSON.stringify(counts);
    if (countsSig !== lastCounts) {
        lastCounts = countsSig;
        postToAdminPreview({ type: AD_SLOT_PREVIEW_AREAS, counts: counts.counts, empty: counts.empty });
    }

    const next: Box[] = found.map((f, i) => ({
        ...f.rect,
        id: `${f.type}#${i}`,
        type: f.type,
        label: msg.labels[f.type] ?? f.type,
        empty: f.empty,
        on: msg.active === f.type,
        labelTop: Math.min(Math.max(0, -f.rect.top), Math.max(0, f.rect.height - 20)),
    }));
    // 큰 상자를 먼저(아래에) 그려 안쪽 작은 상자를 누를 수 있게
    next.sort((a, b) => b.width * b.height - a.width * a.height);
    const signature = `${window.innerWidth}x${window.innerHeight}|${next.map((b) => `${b.id}:${Math.round(b.left)},${Math.round(b.top)},${Math.round(b.width)},${Math.round(b.height)},${b.label}${b.on}${b.empty}`).join('|')}`;
    if (signature !== lastSignature) {
        lastSignature = signature;
        boxes.value = next;
        veil.value = UtilAdSlotArea.veilPath(window.innerWidth, window.innerHeight, next);
    }
    frame = requestAnimationFrame(tick);
}

function pick(type: string): void {
    if (type) postToAdminPreview({ type: AD_SLOT_PREVIEW_PICK, slotType: type });
}

watch(
    adSlot,
    () => {
        // 새 값이 오면 수를 다시 알린다(관리자가 화면을 바꿔 다시 띄웠을 때 등)
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
.ad-slot-veil {
    fill: rgb(15 23 42 / 0.45);
}
.ad-slot-box {
    border: 2px solid var(--p-primary-color);
    border-radius: 6px;
    cursor: pointer;
}
.ad-slot-box:hover {
    background: color-mix(in srgb, var(--p-primary-color) 12%, transparent);
}
.ad-slot-box--empty {
    border-style: dashed;
    border-color: #e5e7eb;
    background: color-mix(in srgb, #9ca3af 18%, transparent);
}
.ad-slot-box--on {
    border-width: 3px;
    border-color: #f59e0b;
}
.ad-slot-label {
    position: absolute;
    left: 0;
    max-width: 100%;
    overflow: hidden;
    padding: 2px 6px;
    border-radius: 0 0 6px 0;
    background: var(--p-primary-color);
    color: var(--p-primary-contrast-color);
    font-size: 11px;
    line-height: 1.4;
    text-overflow: ellipsis;
    white-space: nowrap;
}
.ad-slot-label--empty {
    background: #6b7280;
    color: #fff;
}
.ad-slot-label--on {
    background: #f59e0b;
    color: #111827;
}
</style>
