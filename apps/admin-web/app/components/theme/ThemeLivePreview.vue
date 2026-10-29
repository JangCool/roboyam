<template>
    <!--
        실제 화면 — 공개 사이트(user-web)를 iframe 으로 띄운다(사용자 결정 2026-10-28: 미리보기는 실제 공개 화면과 똑같아야 한다 — 관리자가 따로 그리지 않는다).
        주소 `{runtimeConfig.public.userWebOrigin}/?adminPreview=1` — 공개 사이트가 미리보기 모드로 켜져(`plugins/03.admin-preview.client.ts`) 이 화면이 보내는 값을 그대로 그린다:
          고른 테마 · 메뉴 설정(저장 전 값 포함) · 홈 구성(편집기의 저장 전 값). 값이 바뀔 때마다 다시 보내고, 공개 사이트가 「준비됨」을 알리면 그때도 보낸다.
        창 크기는 공개 사이트 데스크톱 폭 1280 × 높이 800 으로 띄우고 이 칸 폭에 맞춰 줄여 보인다(`transform: scale`) — 안에서 스크롤하므로 메뉴 붙이기 같은 스크롤 동작도 그대로다.
        미리보기 안에서는 다른 화면으로 가지 않고, 윗줄·메뉴·사이드 메뉴를 누르면 `edit-menu`(화면이 메뉴 설정 drawer 를 연다).
        편집 중(`editing`)이면 공개 홈 격자가 가젯 위에 손잡이를 얹고, 끌어 옮기기·크기·빼기·⚙ 설정 열기를 알려 온다 → `layout-op`(화면이 홈 구성 편집 도구 `applyOp` 에 넘겨 계산하고, 바뀐 배치를 다시 보낸다).
        가젯 페이지(`path` = `/p/{key}`, 2026-10-29)면 그 페이지를 띄우고 홈 구성 자리에 그 페이지의 배치를, `page` 에 페이지 머리(이름·설명·글쓰기 단추 — 저장 전 값)를 보낸다. 주소가 바뀌면 다시 띄운다.
        메시지는 공개 사이트 주소(`userWebOrigin`)로만 보내고 그 주소에서 온 것만 받는다. 운영에서는 공개 사이트의 frame-ancestors 에 관리자 주소를 허용해야 뜬다(미확정 — 배포 설정).
    -->
    <div ref="box" class="relative w-full overflow-hidden rounded-lg border border-(--line) bg-surface-0 dark:bg-surface-900" :style="{ height: `${VIEW_H * scale}px` }" data-testid="theme-live-preview">
        <iframe
            ref="frame"
            :src="src"
            :title="t('site.livePreviewTitle')"
            class="absolute left-0 top-0 block border-0"
            :style="{ width: `${VIEW_W}px`, height: `${VIEW_H}px`, transform: `scale(${scale})`, transformOrigin: '0 0' }"
            @load="send"
        />
        <div v-if="!ready" class="absolute inset-0 grid place-items-center text-sm text-muted-color" role="status">
            <span class="flex items-center gap-2"><i class="pi pi-spin pi-spinner" aria-hidden="true" />{{ t('site.livePreviewLoading') }}</span>
        </div>
    </div>
</template>

<script setup lang="ts">
import type { SiteThemeMenuFields } from '~/composables/site/api/useSiteSettingsApi.types';
import type { HomeLayoutBody } from '~/composables/home/api/useHomeLayoutApi.types';
import type { HomeLayoutOp } from '~/components/theme/HomeLayoutEditor.vue';

interface Props {
    /** 고른 테마 id */
    theme: string;
    /** 메뉴 설정(운영자 설정 모양 — 저장 전 값 포함). 없으면 공개 사이트의 저장 값 */
    menu: SiteThemeMenuFields | null;
    /** 홈 구성(편집기의 저장 전 값). 없으면 공개 사이트의 홈 그대로 */
    home: HomeLayoutBody | null;
    /** 홈 구성 편집 중인가 — 공개 홈 격자가 가젯 위에 끌기·크기·빼기 손잡이를 얹는다 */
    editing?: boolean;
    /** 띄울 공개 사이트 경로 — 홈 `/` · 가젯 페이지 `/p/{key}` */
    path?: string;
    /** 가젯 페이지 머리(저장 전 값) — 가젯 페이지일 때만 */
    page?: { name: string; description: string; writeButton: boolean } | null;
}
const props = withDefaults(defineProps<Props>(), { editing: false, path: '/', page: null });
const emit = defineEmits<{ (e: 'edit-menu'): void; (e: 'layout-op', value: HomeLayoutOp): void }>();

const { t } = useI18n();

/** 공개 사이트를 띄울 창 크기 — 데스크톱 폭(본문 1168 + 여백). 칸 폭에 맞춰 줄인다 */
const VIEW_W = 1280;
const VIEW_H = 800;

const origin = String(useRuntimeConfig().public.userWebOrigin ?? '').replace(/\/+$/, '');
const src = computed(() => `${origin}${props.path.startsWith("/") ? props.path : `/${props.path}`}?adminPreview=1`);

const box = ref<HTMLElement | null>(null);
const frame = ref<HTMLIFrameElement | null>(null);
const scale = ref(0.8);
const ready = ref(false);

/** 지금 값을 공개 사이트로 보낸다 — 반응형 감싸기를 벗겨(JSON) 보낸다 */
function send(): void {
    const target = frame.value?.contentWindow;
    if (!target || !origin) return;
    const message = JSON.parse(JSON.stringify({ type: 'roboyam:admin-preview', theme: props.theme, menu: props.menu, home: props.home, editing: props.editing, page: props.page }));
    target.postMessage(message, origin);
}

function onMessage(event: MessageEvent): void {
    if (event.origin !== origin || event.source !== frame.value?.contentWindow) return;
    const type = (event.data as { type?: unknown } | null)?.type;
    if (type === 'roboyam:admin-preview-ready') {
        ready.value = true;
        send();
    } else if (type === 'roboyam:admin-preview-edit-menu') {
        emit('edit-menu');
    } else if (type === 'roboyam:admin-preview-layout') {
        const op = readOp(event.data);
        if (op) emit('layout-op', op);
    }
}

/** 홈 격자 편집 알림 읽기 — 모양이 아니면 null */
function readOp(data: unknown): HomeLayoutOp | null {
    const d = data as Record<string, unknown>;
    if (typeof d.id !== 'string') return null;
    if (d.op === 'remove') return { op: 'remove', id: d.id };
    if (d.op === 'settings') return { op: 'settings', id: d.id };
    if (d.op === 'place' && Number.isFinite(d.x) && Number.isFinite(d.y)) return { op: 'place', id: d.id, x: Number(d.x), y: Number(d.y) };
    if (d.op === 'size' && Number.isFinite(d.w) && Number.isFinite(d.h)) return { op: 'size', id: d.id, w: Number(d.w), h: Number(d.h) };
    return null;
}

watch(() => [props.theme, props.menu, props.home, props.editing, props.page], send, { deep: true });
// 다른 페이지를 띄우면 「준비됨」을 다시 기다린다
watch(src, () => (ready.value = false));

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
