<template>
    <!-- 본문 상자 바깥 좌우에 덮는 층(본문 배치를 밀지 않는다) — 바깥 여백이 날개+간격보다 좁으면 숨는다 -->
    <div ref="host" class="pointer-events-none absolute inset-x-0 top-0 z-10 max-lg:hidden" :style="layerStyle" data-testid="ad-wings">
        <template v-if="shown">
            <div v-if="picked.left" class="absolute top-0 h-full" :style="{ left: `${box.left - UtilAd.WING.gap - UtilAd.WING.width}px`, width: `${UtilAd.WING.width}px` }">
                <aside class="pointer-events-auto sticky" :style="{ top: `${box.stickyTop}px` }" :aria-label="t('ad.wingLeftLabel')" data-testid="ad-wing-left">
                    <AdSlot :key="`${route.path}:${picked.left.id}`" type="WING_BANNER" :ads="[picked.left]" placement="WING_LEFT" />
                </aside>
            </div>
            <div v-if="picked.right" class="absolute top-0 h-full" :style="{ left: `${box.right + UtilAd.WING.gap}px`, width: `${UtilAd.WING.width}px` }">
                <aside class="pointer-events-auto sticky" :style="{ top: `${box.stickyTop}px` }" :aria-label="t('ad.wingRightLabel')" data-testid="ad-wing-right">
                    <AdSlot :key="`${route.path}:${picked.right.id}`" type="WING_BANNER" :ads="[picked.right]" placement="WING_RIGHT" />
                </aside>
            </div>
        </template>
    </div>
</template>

<script setup lang="ts">
import type { CSSProperties } from 'vue';
import type { PublicAd } from '~/composables/ad/api/usePublicAdApi.types';
import UtilAd from '~/utils/util-ad';

const { t } = useI18n();
const route = useRoute();
const { theme } = useTheme();
const ads = usePublicAds();
void ads.ensurePlacements();

const sides = computed(() => {
    const w = ads.placements.value?.wings;
    const screen = UtilAd.wingScreen(route.path);
    // 소모임 화면(`/groups/**`)은 `wingScreen` 이 null, 소모임 글은 글 보기가 알려 준다
    if (!w || !screen || !UtilAd.wingScreens(w.screens).includes(screen)) return { left: false, right: false };
    if (screen === 'POST' && !ads.postWingsAllowed(route.path)) return { left: false, right: false };
    return { left: w.left === true, right: w.right === true };
});

watch(
    () => sides.value.left || sides.value.right,
    (on) => {
        if (on) void ads.ensure(['WING_BANNER']);
    },
    { immediate: true },
);

const candidates = computed(() => ads.candidates('WING_BANNER') ?? null);

const picked = ref<{ left: PublicAd | null; right: PublicAd | null }>({ left: null, right: null });
watch(
    [candidates, () => sides.value.left, () => sides.value.right],
    ([list, left, right]) => {
        if (!list) {
            picked.value = { left: null, right: null };
            return;
        }
        const cur = picked.value;
        // 켠 쪽은 보이던 광고가 아직 후보에 있고, 끈 쪽은 비어 있으면 그대로(오른쪽은 왼쪽 말고 남은 후보가 없어 빈 것도 그대로)
        const valid = (on: boolean, ad: PublicAd | null) => (on ? ad !== null && UtilAd.stillIn(ad, list) : ad === null);
        const rightEmptyOk = right && cur.right === null && !list.some((ad) => ad.id !== cur.left?.id);
        if (valid(left, cur.left) && (valid(right, cur.right) || rightEmptyOk)) return;
        picked.value = UtilAd.pickWings(list, { left, right });
    },
    { immediate: true },
);

const host = ref<HTMLElement | null>(null);
const box = ref({ ok: false, top: 0, height: 0, left: 0, right: 0, stickyTop: 0 });

/** 셸마다 붙는 막대가 달라 값을 박지 않고 레이아웃 직속 sticky 자식의 아래 끝을 잰다 */
function stickyBottom(root: HTMLElement): number {
    let bottom = 0;
    for (const el of Array.from(root.children)) {
        if (el === host.value) continue;
        const cs = getComputedStyle(el);
        if (cs.position !== 'sticky' || cs.display === 'none') continue;
        const h = el.getBoundingClientRect().height;
        if (h > 0) bottom = Math.max(bottom, (parseFloat(cs.top) || 0) + h);
    }
    return bottom;
}

let watchedMain: Element | null = null;

function measure(): void {
    const root = host.value?.parentElement;
    const main = typeof document === 'undefined' ? null : document.getElementById('main-content');
    const body = main?.parentElement;
    if (!root || !main || !body) {
        box.value = { ...box.value, ok: false };
        return;
    }
    if (watchedMain !== main) {
        if (watchedMain) observer?.unobserve(watchedMain);
        observer?.observe(main);
        watchedMain = main;
    }
    const r = root.getBoundingClientRect();
    const b = body.getBoundingClientRect();
    const m = main.getBoundingClientRect();
    const need = UtilAd.WING.width + UtilAd.WING.gap + UtilAd.WING.edge;
    const top = m.top - r.top + (parseFloat(getComputedStyle(main).paddingTop) || 0);
    box.value = {
        ok: b.left - r.left >= need && r.right - b.right >= need,
        top,
        height: Math.max(0, m.bottom - r.top - top),
        left: b.left - r.left,
        right: b.right - r.left,
        stickyTop: stickyBottom(root) + UtilAd.WING.gap,
    };
}

let frame = 0;
function schedule(): void {
    if (typeof window === 'undefined' || frame) return;
    frame = window.requestAnimationFrame(() => {
        frame = 0;
        measure();
    });
}

let observer: ResizeObserver | null = null;
onMounted(() => {
    if (typeof ResizeObserver !== 'undefined') {
        observer = new ResizeObserver(schedule);
        const root = host.value?.parentElement;
        if (root) observer.observe(root);
    }
    window.addEventListener('resize', schedule);
    measure();
});
onBeforeUnmount(() => {
    observer?.disconnect();
    observer = null;
    window.removeEventListener('resize', schedule);
    if (frame) window.cancelAnimationFrame(frame);
});
// 화면·테마(셸)가 바뀌면 본문 요소가 바뀔 수 있다 — 그린 뒤 다시 잰다
watch([() => route.path, theme], () => nextTick(schedule));

const shown = computed(() => box.value.ok && (picked.value.left !== null || picked.value.right !== null));
const layerStyle = computed<CSSProperties>(() => (shown.value ? { top: `${box.value.top}px`, height: `${box.value.height}px` } : { height: '0px' }));
</script>
