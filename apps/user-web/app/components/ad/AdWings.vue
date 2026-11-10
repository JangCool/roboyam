<template>
    <!--
        본문 상자 바깥 좌우에 덮는 층(본문 배치를 밀지 않는다) — 바깥 여백이 날개+간격보다 좁으면 숨는다.
        장부 날개 배너가 없는 쪽은 애드센스 단위(160×600 고정)로 채운다: 「광고 표시」 단계 + 날개 단위 ID · 그 쪽이 켜짐 · 장부 후보를 받았는데 그 쪽 광고가 없음 ·
        게시판 화면·글 보기는 맥락이 19금(옵션과 상관없이)·광고 숨김·소모임·비밀글이 아님. 광고가 오지 않으면 그 쪽을 접는다(이 화면에서만).
    -->
    <div ref="host" class="pointer-events-none absolute inset-x-0 top-0 z-10 max-lg:hidden" :style="layerStyle" data-testid="ad-wings">
        <template v-if="shown">
            <div v-if="picked.left || adsenseLeft" class="absolute top-0 h-full" :style="{ left: `${box.left - UtilAd.WING.gap - UtilAd.WING.width}px`, width: `${UtilAd.WING.width}px` }">
                <aside class="pointer-events-auto sticky" :style="{ top: `${box.stickyTop}px` }" :aria-label="t('ad.wingLeftLabel')" data-testid="ad-wing-left">
                    <AdSlot v-if="picked.left" :key="`${route.path}:${picked.left.id}`" type="WING_BANNER" :ads="[picked.left]" placement="WING_LEFT" />
                    <AdSenseUnit v-else-if="adsenseLeft" :key="`${route.path}:adsense-left`" :spec="adsenseLeft" @unfilled="unfilledAt.left = route.path" />
                </aside>
            </div>
            <div v-if="picked.right || adsenseRight" class="absolute top-0 h-full" :style="{ left: `${box.right + UtilAd.WING.gap}px`, width: `${UtilAd.WING.width}px` }">
                <aside class="pointer-events-auto sticky" :style="{ top: `${box.stickyTop}px` }" :aria-label="t('ad.wingRightLabel')" data-testid="ad-wing-right">
                    <AdSlot v-if="picked.right" :key="`${route.path}:${picked.right.id}`" type="WING_BANNER" :ads="[picked.right]" placement="WING_RIGHT" />
                    <AdSenseUnit v-else-if="adsenseRight" :key="`${route.path}:adsense-right`" :spec="adsenseRight" @unfilled="unfilledAt.right = route.path" />
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

/**
 * 그릴 쪽과 물을 게시판 — 홈·가젯 페이지는 `board` 없이, 게시판 화면(BOARD)·글 보기(POST)는 그 게시판 `board` 로(서버 판정: 소모임·숨김·19금 옵션 꺼짐이면 빈 목록).
 * 소모임 목록·상세(`/groups`, `/groups/{id}`)는 화면 GROUPS(2026-11-06 넓힘 — 홈처럼 `board` 없이 묻는다), 소모임 글 보기는 그대로 뺀다. 글 보기는 글을 받아 맥락을 알려 주기 전에는 그리지 않고(소모임 글에 잠깐 비치지 않게),
 * 게시판 화면은 맥락(상세의 `adsHidden`)을 아직 못 받았으면 주소의 slug 로 불러 서버 판정에 맡긴다.
 */
const wing = computed<{ left: boolean; right: boolean; board: string }>(() => {
    const none = { left: false, right: false, board: '' };
    const w = ads.placements.value?.wings;
    const screen = UtilAd.wingScreen(route.path);
    if (!w || !screen || !UtilAd.wingScreens(w.screens).includes(screen)) return none;
    let board = '';
    if (screen === 'BOARD' || screen === 'POST') {
        const ctx = ads.wingContextOf(route.path);
        if (screen === 'POST' && !ctx) return none;
        if (ctx && UtilAd.wingContextHidden(ctx, ads.adultEnabled.value)) return none;
        board = ctx?.board || UtilAd.boardSlugOf(route.path);
        if (!board) return none;
    }
    return { left: w.left === true, right: w.right === true, board };
});

watch(
    () => (wing.value.left || wing.value.right ? `WING@${wing.value.board}` : ''),
    (key) => {
        if (key) void ads.ensure(['WING_BANNER'], wing.value.board);
    },
    { immediate: true },
);

const sides = computed(() => ({ left: wing.value.left, right: wing.value.right }));
const candidates = computed(() => (wing.value.left || wing.value.right ? (ads.candidates('WING_BANNER', wing.value.board) ?? null) : null));

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

/*
 * 애드센스 — 장부 광고가 없는 쪽만. 장부 후보를 받기 전(`candidates` null)에는 그리지 않는다(애드센스가 먼저 떴다가 장부로 바뀌지 않게).
 * 게시판 화면·글 보기는 맥락을 받은 뒤에만(19금은 옵션과 상관없이 뺀다 — `useAdSense().contextAllows`).
 */
const adsense = useAdSense();
/** 광고가 오지 않아 접은 쪽 — 그때의 주소(다른 화면으로 가면 다시 그린다) */
const unfilledAt = ref<{ left: string | null; right: string | null }>({ left: null, right: null });
const adsenseSpec = computed(() => {
    if (!candidates.value) return null;
    const spec = adsense.unit('WING_BANNER');
    if (!spec) return null;
    const screen = UtilAd.wingScreen(route.path);
    if ((screen === 'BOARD' || screen === 'POST') && !adsense.contextAllows(route.path)) return null;
    return spec;
});
const adsenseLeft = computed(() => (wing.value.left && !picked.value.left && unfilledAt.value.left !== route.path ? adsenseSpec.value : null));
const adsenseRight = computed(() => (wing.value.right && !picked.value.right && unfilledAt.value.right !== route.path ? adsenseSpec.value : null));

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

const shown = computed(() => box.value.ok && (picked.value.left !== null || picked.value.right !== null || adsenseLeft.value !== null || adsenseRight.value !== null));
const layerStyle = computed<CSSProperties>(() => (shown.value ? { top: `${box.value.top}px`, height: `${box.value.height}px` } : { height: '0px' }));
</script>
