<template>
    <!--
        광고 자리 미리보기 — 공개 사이트(user-web)를 iframe 으로 띄운다(캐시 `CacheLiveFrame`·테마 `ThemeLivePreview` 와 같은 방식, 계약 ad-slot-manager-contract 「미리보기」).
        주소 `{공개 사이트 origin}{path}?adminPreview=1&adSlotPreview=1` — 공개 사이트는 화면 값을 덮지 않고 저장된 화면 그대로 그린 뒤, `data-ad-slot` 요소 위에 상자·이름표를 겹치고 나머지를 흐리게 한다(user-web `AdSlotOverlay`).
          빈 자리는 점선(그 미리보기에서만 빈 자리 틀을 남긴다). 보내는 것 `irobit:ad-slot-preview` { labels(종류 → 이름표), active(드로어에 열린 종류) } — 값이 바뀔 때와 공개 사이트가 「준비됨」을 알릴 때.
          받는 것 `irobit:ad-slot-preview-pick` { slotType } → `pick` · `irobit:ad-slot-preview-areas` { counts, empty } → `areas`.
        데스크톱 1280 × 800 은 칸 폭에 맞춰 줄이되 0.75 배를 넘기지 않고(넓은 칸은 가운데), 모바일 390 × 844 는 가운데에 두고 줄이기만 한다. 메시지는 공개 사이트 origin 으로만 보내고 그 origin·그 창에서 온 것만 받는다.
    -->
    <div ref="box" class="relative w-full overflow-hidden rounded-lg border border-(--line) bg-surface-100 dark:bg-surface-950" :style="{ height: `${VIEW_H * scale}px` }" data-testid="ad-slot-live-frame">
        <div v-if="!src" class="absolute inset-0 grid place-items-center px-6 text-center text-sm text-muted-color" role="status">{{ t('adSlot.previewNoOrigin') }}</div>
        <iframe
            v-else
            :key="mobile ? 'mobile' : 'desktop'"
            ref="frame"
            :src="src"
            :title="t('adSlot.previewFrameTitle')"
            class="absolute top-0 block border-0 bg-surface-0"
            :style="{ left: `${left}px`, width: `${VIEW_W}px`, height: `${VIEW_H}px`, transform: `scale(${scale})`, transformOrigin: '0 0' }"
            @load="send"
        />
        <div v-if="src && !ready" class="absolute inset-0 grid place-items-center text-sm text-muted-color" role="status">
            <span class="flex items-center gap-2"><i class="pi pi-spin pi-spinner" aria-hidden="true" />{{ t('adSlot.previewLoading') }}</span>
        </div>
    </div>
</template>

<script setup lang="ts">
import UtilAdSlot, { AD_SLOT_PREVIEW_APPLY, AD_SLOT_PREVIEW_AREAS } from '~/utils/util-ad-slot';
import UtilAd from '~/utils/util-ad';

interface Props {
    /** 띄울 공개 사이트 경로 */
    path?: string;
    /** 휴대폰 폭으로 띄우는가(390 × 844 — 가운데, 줄이기만) */
    mobile?: boolean;
    /** 종류 → 이름표 글자(「넓은 배너 · 진행 2」) */
    labels?: Record<string, string>;
    /** 드로어에 열린 종류 — 그 상자를 진하게 */
    active?: string | null;
}

const props = withDefaults(defineProps<Props>(), { path: '/', mobile: false, labels: () => ({}), active: null });
const emit = defineEmits<{ pick: [type: string]; areas: [counts: Record<string, number>, empty: Record<string, number>]; reloading: [] }>();

const { t } = useI18n();

const VIEW_W = computed(() => (props.mobile ? 390 : 1280));
const VIEW_H = computed(() => (props.mobile ? 844 : 800));

const { origin } = useUserWebOrigin();
const src = computed(() => (origin.value ? UtilAdSlot.previewSrc(origin.value, props.path) : ''));

const box = ref<HTMLElement | null>(null);
const frame = ref<HTMLIFrameElement | null>(null);
const boxW = ref(0);
/** 데스크톱 최대 배율 — 칸이 넓어도 이보다 키우지 않고 가운데에 둔다(넓은 모니터에서 실제보다 크게 보이던 것, 2026-11-23 사용자) */
const DESKTOP_MAX_SCALE = 0.75;
const scale = computed(() => (boxW.value > 0 ? Math.min(props.mobile ? 1 : DESKTOP_MAX_SCALE, boxW.value / VIEW_W.value) : DESKTOP_MAX_SCALE));
const left = computed(() => (boxW.value > 0 ? Math.max(0, (boxW.value - VIEW_W.value * scale.value) / 2) : 0));
const ready = ref(false);

/** 반응형 감싸기를 벗겨 보낸다 */
function send(): void {
    const target = frame.value?.contentWindow;
    if (!target || !origin.value) return;
    const message = { type: AD_SLOT_PREVIEW_APPLY, labels: { ...props.labels }, active: props.active };
    target.postMessage(JSON.parse(JSON.stringify(message)), origin.value);
}

function onMessage(event: MessageEvent): void {
    if (!origin.value || event.origin !== origin.value || event.source !== frame.value?.contentWindow) return;
    const data = event.data as Record<string, unknown> | null;
    if (data?.type === 'irobit:admin-preview-ready') {
        ready.value = true;
        send();
        return;
    }
    const picked = UtilAdSlot.readPick(data, UtilAd.TYPES);
    if (picked) emit('pick', picked);
    else if (data?.type === AD_SLOT_PREVIEW_AREAS) emit('areas', UtilAdSlot.readCounts(data.counts), UtilAdSlot.readCounts(data.empty));
}

watch(() => [props.labels, props.active], send, { deep: true });
// 다른 화면·기기를 띄우면 「준비됨」과 찾은 자리 수를 다시 기다린다
watch(src, () => {
    ready.value = false;
    emit('reloading');
});
watch(
    () => props.mobile,
    () => {
        ready.value = false;
        emit('reloading');
    },
);

let observer: ResizeObserver | null = null;
onMounted(() => {
    window.addEventListener('message', onMessage);
    observer = new ResizeObserver(() => {
        const width = box.value?.clientWidth ?? 0;
        if (width > 0) boxW.value = width;
    });
    if (box.value) observer.observe(box.value);
});
onBeforeUnmount(() => {
    window.removeEventListener('message', onMessage);
    observer?.disconnect();
});
</script>
