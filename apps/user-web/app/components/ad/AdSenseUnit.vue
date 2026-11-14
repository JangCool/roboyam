<template>
    <!--
        구글 애드센스 단위 하나 — 장부 광고가 없는 자리를 채운다. 부르는 쪽이 자리·단위(`spec`)를 정한다(`useAdSense().unit`).
        스크립트 전제: `adsbygoogle.js` 는 서버가 index.html 머리에 넣는다(api `SitePageHandler`/`IndexHtmlRenderer` — 「사이트 확인만」·「광고 표시」 단계).
          이 부품은 스크립트를 넣지 않고 `<ins class="adsbygoogle">` 를 그린 뒤 `window.adsbygoogle` 큐에 push 만 한다 — 스크립트가 늦게 와도(또는 아직 없어도) 큐에 남았다가 동작한다.
        push 는 새 `<ins>` 마다 한 번 — 같은 요소에 두 번 하지 않는다(`pushed`·`data-adsbygoogle-status`). 단위가 바뀌면(`insKey`) 요소를 새로 만들어 다시 한 번.
          자리가 폭 0(숨은 모바일/데스크톱 짝 · `display:none` 조상)이면 `<ins>` 를 아직 만들지 않는다 — push 는 문서의 처리 안 된 첫 `<ins>` 를 채우므로, 숨은 요소가 먼저 잡혀 「availableWidth=0」 오류가 나지 않게. 폭이 생기면 그때 만든다.
        광고가 오지 않으면(`data-ad-status="unfilled"` — 애드센스가 붙인다) 자리를 통째로 접고 `unfilled` 를 알린다(MutationObserver). 오면(`filled`) 자리 최소 높이를 푼다(짧은 광고 아래 빈칸이 남지 않게).
        광고 차단기: push 뒤 `UtilAdSense.BLOCKED_COLLAPSE_MS`(5초) 안에 스크립트가 `<ins>` 를 처리하지 않았거나 차단기가 숨겼으면(높이 0) 같은 식으로 접는다.
        「광고」 표시: 단위 위 작은 글씨(애드센스 정책상 광고 위에 덮지 않는다) — `mark` 로 끈다(목록 한 줄은 줄 모양이 따로 붙인다).
        우리 노출·클릭 집계(`/api/ads/impressions`·`clicks`)는 보내지 않는다(애드센스 콘솔에서 본다).
    -->
    <div v-if="!collapsed" ref="root" class="min-w-0" :class="isWing ? 'w-[160px]' : 'w-full'" :style="rootStyle" :data-adsense-place="spec.place" data-testid="adsense-unit">
        <span v-if="mark" class="mb-1 block text-[0.625rem] leading-none text-muted-color" data-testid="adsense-mark">{{ t('ad.mark') }}</span>
        <ins v-if="ready" :key="insKey" ref="ins" class="adsbygoogle" :style="insStyle" v-bind="attrs" />
    </div>
</template>

<script lang="ts">
/** 이미 push 한 `<ins>` — 같은 요소에 두 번 push 하지 않는다(창 하나에 하나) */
const pushed = new WeakSet<Element>();

type AdsWindow = Window & { adsbygoogle?: object[] };

/** 새 `<ins>` 하나를 채워 달라고 큐에 넣는다 — 스크립트(서버가 머리에 넣는다)가 아직 없어도 큐 배열에 남는다 */
function pushOnce(el: Element): void {
    if (pushed.has(el) || el.getAttribute('data-adsbygoogle-status')) return;
    pushed.add(el);
    try {
        const w = window as AdsWindow;
        (w.adsbygoogle = w.adsbygoogle || []).push({});
    } catch (e) {
        console.warn('[adsense] 광고 단위를 요청하지 못했어요', e);
    }
}
</script>

<script setup lang="ts">
import type { CSSProperties } from 'vue';
import type { BannerAdType } from '~/composables/ad/api/usePublicAdApi.types';
import type { AdSenseUnitSpec } from '~/utils/util-adsense';
import UtilAd from '~/utils/util-ad';
import UtilAdSense from '~/utils/util-adsense';

interface Props {
    spec: AdSenseUnitSpec;
    /** 「광고」 작은 글씨를 단위 위에 */
    mark?: boolean;
    /** 자리 최소 높이(CSS 길이) — 인피드는 목록 글 줄 높이를 넘긴다. 비우면 모양별 기본(`UtilAdSense.minHeight`) */
    minHeight?: string;
}

const props = withDefaults(defineProps<Props>(), { mark: true, minHeight: '' });
const emit = defineEmits<{ (e: 'unfilled'): void; (e: 'filled'): void }>();

const { t } = useI18n();

const isWing = computed(() => props.spec.format === 'wing');
const attrs = computed(() => UtilAdSense.insAttrs(props.spec));
const insKey = computed(() => `${props.spec.client}:${props.spec.slot}:${props.spec.format}:${props.spec.layoutKey ?? ''}:${props.spec.adtest ? 1 : 0}`);

/** 배너 가젯 자리의 틀(비율·폭) — 장부 배너와 같은 값(`UtilAd.frameOf`) */
const bannerFrame = computed(() => (UtilAd.isBanner(props.spec.place) ? UtilAd.frameOf(props.spec.place as BannerAdType) : null));
const minHeightPx = computed(() => props.minHeight || (UtilAdSense.minHeight(props.spec.format) !== null ? `${UtilAdSense.minHeight(props.spec.format)}px` : ''));

const collapsed = ref(false);
const filled = ref(false);
/** 폭이 생겨 `<ins>` 를 만들어도 되는가 */
const ready = ref(false);

const rootStyle = computed<CSSProperties>(() => {
    const style: CSSProperties = {};
    // 네모·작은 배너는 장부 배너처럼 300px 까지
    if (bannerFrame.value && bannerFrame.value.maxWidth !== '100%') style.maxWidth = bannerFrame.value.maxWidth;
    return style;
});

/** 실제 단위 — 날개는 160×600 고정, 나머지는 폭을 채우고 광고가 오기 전까지만 최소 높이 */
const insStyle = computed<CSSProperties>(() => {
    if (isWing.value) return { display: 'inline-block', width: `${UtilAdSense.WING.width}px`, height: `${UtilAdSense.WING.height}px` };
    return filled.value || !minHeightPx.value ? { display: 'block' } : { display: 'block', minHeight: minHeightPx.value };
});

const root = ref<HTMLElement | null>(null);
const ins = ref<HTMLElement | null>(null);
let sizeObserver: ResizeObserver | null = null;
let statusObserver: MutationObserver | null = null;
let blockedTimer: ReturnType<typeof setTimeout> | null = null;

/** 자리를 접고 알린다(한 번) */
function collapse(): void {
    if (collapsed.value) return;
    collapsed.value = true;
    emit('unfilled');
}

function readStatus(el: Element): void {
    const status = el.getAttribute('data-ad-status');
    if (status === 'unfilled') {
        collapse();
    } else if (status === 'filled' && !filled.value) {
        filled.value = true;
        emit('filled');
    }
}

/** 광고 차단 판정 — 기다린 뒤에도 스크립트가 처리하지 않았거나(스크립트가 막힘) 차단기가 `<ins>` 를 숨겼으면(높이 0) 접는다 */
function watchBlocked(el: HTMLElement): void {
    if (blockedTimer) clearTimeout(blockedTimer);
    blockedTimer = setTimeout(() => {
        blockedTimer = null;
        if (filled.value || collapsed.value || ins.value !== el) return;
        if (!el.getAttribute('data-adsbygoogle-status') || el.offsetHeight === 0) collapse();
    }, UtilAdSense.BLOCKED_COLLAPSE_MS);
}

/** 새로 그린 `<ins>` 를 지켜보고 한 번 push */
function attach(): void {
    statusObserver?.disconnect();
    const el = ins.value;
    if (!el) return;
    if (typeof MutationObserver !== 'undefined') {
        statusObserver = new MutationObserver(() => readStatus(el));
        statusObserver.observe(el, { attributes: true, attributeFilter: ['data-ad-status'] });
    }
    pushOnce(el);
    readStatus(el);
    watchBlocked(el);
}

/** 자리에 폭이 생기면 `<ins>` 를 만든다(한 번) */
function checkWidth(): void {
    if (ready.value || !root.value || root.value.clientWidth <= 0) return;
    ready.value = true;
    sizeObserver?.disconnect();
    sizeObserver = null;
    void nextTick(attach);
}

onMounted(() => {
    if (typeof ResizeObserver !== 'undefined' && root.value) {
        sizeObserver = new ResizeObserver(checkWidth);
        sizeObserver.observe(root.value);
    }
    checkWidth();
});

// 단위가 바뀌면(사본이 새 단위 ID 를 줌) 새 `<ins>` 가 그려진다 — 상태를 처음으로 돌리고 그 요소에 한 번 push(외부 SDK 동기화)
watch(insKey, () => {
    if (!ready.value) return;
    collapsed.value = false;
    filled.value = false;
    void nextTick(attach);
});

onBeforeUnmount(() => {
    sizeObserver?.disconnect();
    statusObserver?.disconnect();
    if (blockedTimer) clearTimeout(blockedTimer);
});
</script>
