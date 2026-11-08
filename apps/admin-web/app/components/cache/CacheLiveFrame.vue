<template>
    <!--
        캐시 적용 영역의 실제 화면 — 공개 사이트(user-web)를 iframe 으로 띄운다(사용자 결정 2026-10-28: 미리보기는 실제 공개 화면과 똑같아야 한다 — 테마 화면 `ThemeLivePreview` 와 같은 방식).
        주소 `{공개 사이트 origin}{path}?adminPreview=1&cachePreview=1` — origin 은 `useUserWebOrigin`(개발 서버는 `runtimeConfig.public.userWebOrigin`, 빌드한 화면은 서버가 준 사이트 공개 주소).
        공개 사이트는 `cachePreview=1` 이면 테마 값을 받지 않고 저장된 화면 그대로 그린 뒤, `data-cache-area` 요소 위에 상자·이름표를 겹친다(user-web `CacheAreaOverlay`).
          보내는 것 `roboyam:cache-preview` { keys(강조할 캐시 키), showAll(모든 영역), labels(키 → 이름표) } — 값이 바뀔 때와 공개 사이트가 「준비됨」을 알릴 때.
          받는 것 `roboyam:cache-preview-pick` { key }(상자를 눌렀다) → `pick` · `roboyam:cache-preview-areas` { counts, loggedIn }(찾은 영역 수 · 그 창의 로그인 여부) → `areas`. 경로에 쿼리가 있으면(`/write?board=hotdeal`) 그 뒤에 미리보기 표시를 잇는다.
        창 크기는 데스크톱 1280 × 800 으로 띄우고 이 칸 폭에 맞춰 줄여 보인다. 메시지는 공개 사이트 origin 으로만 보내고 그 origin·그 창에서 온 것만 받는다. origin 을 모르면 띄우지 않고 안내만.
    -->
    <div ref="box" class="relative w-full overflow-hidden rounded-lg border border-(--line) bg-surface-0 dark:bg-surface-900" :style="{ height: `${VIEW_H * scale}px` }" data-testid="cache-live-frame">
        <div v-if="!src" class="absolute inset-0 grid place-items-center px-6 text-center text-sm text-muted-color" role="status">{{ t('cache.previewNoOrigin') }}</div>
        <iframe
            v-else
            ref="frame"
            :src="src"
            :title="t('cache.previewFrameTitle')"
            class="absolute left-0 top-0 block border-0"
            :style="{ width: `${VIEW_W}px`, height: `${VIEW_H}px`, transform: `scale(${scale})`, transformOrigin: '0 0' }"
            @load="send"
        />
        <div v-if="src && !ready" class="absolute inset-0 grid place-items-center text-sm text-muted-color" role="status">
            <span class="flex items-center gap-2"><i class="pi pi-spin pi-spinner" aria-hidden="true" />{{ t('cache.previewLoading') }}</span>
        </div>
    </div>
</template>

<script setup lang="ts">
import UtilCache from '~/utils/util-cache';

interface Props {
    /** 띄울 공개 사이트 경로(`/` · `/latest` · `/boards/{slug}` · `/p/{key}`) */
    path?: string;
    /** 강조할 캐시 키(공개 사이트 영역 키 — `nocache:<종류>` 포함) */
    keys?: string[];
    /** 모든 영역을 이름표와 함께 */
    showAll?: boolean;
    /** 영역 키 → 이름표 글자 */
    labels?: Record<string, string>;
    /** 꺼진 캐시 키 — 공개 사이트가 회색 점선으로 「꺼짐」을 구분해 그린다 */
    offKeys?: string[];
}

const props = withDefaults(defineProps<Props>(), { path: '/', keys: () => [], showAll: false, labels: () => ({}), offKeys: () => [] });
const emit = defineEmits<{ pick: [key: string]; areas: [counts: Record<string, number>, loggedIn: boolean | null]; reloading: [] }>();

const { t } = useI18n();

/** 공개 사이트를 띄울 창 크기 — 데스크톱 폭. 칸 폭에 맞춰 줄인다(테마 미리보기와 같은 값) */
const VIEW_W = 1280;
const VIEW_H = 800;

const { origin } = useUserWebOrigin();
const src = computed(() => (origin.value ? UtilCache.previewSrc(origin.value, props.path) : ''));

const box = ref<HTMLElement | null>(null);
const frame = ref<HTMLIFrameElement | null>(null);
const scale = ref(0.8);
const ready = ref(false);

/** 반응형 감싸기를 벗겨 보낸다 */
function send(): void {
    const target = frame.value?.contentWindow;
    if (!target || !origin.value) return;
    const message = { type: 'roboyam:cache-preview', keys: [...props.keys], showAll: props.showAll, labels: { ...props.labels }, off: [...props.offKeys] };
    target.postMessage(JSON.parse(JSON.stringify(message)), origin.value);
}

function onMessage(event: MessageEvent): void {
    if (!origin.value || event.origin !== origin.value || event.source !== frame.value?.contentWindow) return;
    const data = event.data as Record<string, unknown> | null;
    const type = data?.type;
    if (type === 'roboyam:admin-preview-ready') {
        ready.value = true;
        send();
    } else if (type === 'roboyam:cache-preview-pick' && typeof data?.key === 'string') {
        emit('pick', data.key);
    } else if (type === 'roboyam:cache-preview-areas' && data?.counts && typeof data.counts === 'object') {
        const counts: Record<string, number> = {};
        for (const [k, v] of Object.entries(data.counts as Record<string, unknown>)) if (typeof v === 'number' && Number.isFinite(v)) counts[k] = v;
        emit('areas', counts, typeof data.loggedIn === 'boolean' ? data.loggedIn : null);
    }
}

watch(() => [props.keys, props.showAll, props.labels, props.offKeys], send, { deep: true });
// 다른 쪽을 띄우면 「준비됨」과 찾은 영역 수를 다시 기다린다
watch(src, () => {
    ready.value = false;
    emit('reloading');
});

let observer: ResizeObserver | null = null;
onMounted(() => {
    window.addEventListener('message', onMessage);
    observer = new ResizeObserver(() => {
        const width = box.value?.clientWidth ?? 0;
        if (width > 0) scale.value = width / VIEW_W;
    });
    if (box.value) observer.observe(box.value);
});
onBeforeUnmount(() => {
    window.removeEventListener('message', onMessage);
    observer?.disconnect();
});
</script>
