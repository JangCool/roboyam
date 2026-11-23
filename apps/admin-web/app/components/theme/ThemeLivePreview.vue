<template>
    <!--
        실제 화면 — 공개 사이트(user-web)를 iframe 으로 띄운다(사용자 결정 2026-10-28: 미리보기는 실제 공개 화면과 똑같아야 한다 — 관리자가 따로 그리지 않는다).
        주소 `{공개 사이트 origin}/?adminPreview=1` — origin 은 `useUserWebOrigin`(개발 서버는 `runtimeConfig.public.userWebOrigin`, 빌드한 화면은 테마 응답의 사이트 공개 주소). 공개 사이트가 미리보기 모드로 켜져(`plugins/03.admin-preview.client.ts`) 이 화면이 보내는 값을 그대로 그린다:
          고른 테마 · 메뉴 설정(저장 전 값 포함) · 홈 구성(편집기의 저장 전 값). 값이 바뀔 때마다 다시 보내고, 공개 사이트가 「준비됨」을 알리면 그때도 보낸다.
        창 크기는 공개 사이트 데스크톱 폭 1280 × 높이 800 으로 띄우고 이 칸 폭에 맞춰 줄여 보인다(`transform: scale`, 0.85 배를 넘기지 않고 넓은 칸은 가운데) — 안에서 스크롤하므로 메뉴 붙이기 같은 스크롤 동작도 그대로다.
        `mobile` 이면 휴대폰 폭 390 × 844 로 띄워 가운데에 둔다(줄이기만 하고 키우지 않는다 — 편집기의 「모바일 순서」를 한 열로 바로 본다, home-news-contract 「모바일 순서」).
        미리보기 안에서는 다른 화면으로 가지 않고, 윗줄·메뉴·사이드 메뉴를 누르면 `edit-menu`(화면이 메뉴 설정 drawer 를 연다).
        편집 중(`editing`)이면 공개 홈 격자가 가젯 위에 손잡이를 얹고, 끌어 옮기기·크기·빼기·⚙ 설정 열기를 알려 온다 → `layout-op`(화면이 홈 구성 편집 도구 `applyOp` 에 넘겨 계산하고, 바뀐 배치를 다시 보낸다).
        보내는 홈 구성의 가젯은 제목·노출·꾸미기(`style`)·내용 설정(`config`)을 싣는다(2026-10-30 — 설정 창은 관리자 응답 값으로 채운다).
        가젯 페이지(`path` = `/p/{key}`, 2026-10-29)면 그 페이지를 띄우고 홈 구성 자리에 그 페이지의 배치를, `page` 에 페이지 머리(이름·설명·글쓰기 단추 — 저장 전 값)를 보낸다. 주소가 바뀌면 다시 띄운다.
        게시판 레이아웃(`path` = `/boards/{slug}` · `/posts/{id}`, 2026-10-30)이면 홈 구성 자리(`home`)에 게시판 레이아웃(본문 칸 `BOARD_BODY` 포함)을, `board` 에 적용 화면을 보낸다 — 손잡이·편집 알림은 홈과 같은 `home`/`layout` 메시지다.
        메시지는 공개 사이트 origin 으로만 보내고 그 origin 에서 온 것만 받는다. 운영에서는 공개 사이트 서버(api)가 `?adminPreview=1` 화면 응답에만
        `frame-ancestors 'self' <관리자 주소(ADMIN_URL)>` 를 붙여야 뜬다(site-theme-layout-contract 「운영 배포」). origin 을 모르면(공개 주소 미설정) 띄우지 않고 안내만 보인다.
    -->
    <div ref="box" class="relative w-full overflow-hidden rounded-lg border border-(--line) bg-surface-0 dark:bg-surface-900" :style="{ height: `${VIEW_H * scale}px` }" data-testid="theme-live-preview">
        <div v-if="!src" class="absolute inset-0 grid place-items-center px-6 text-center text-sm text-muted-color" role="status">{{ t('site.livePreviewNoOrigin') }}</div>
        <iframe
            v-else
            ref="frame"
            :src="src"
            :title="t('site.livePreviewTitle')"
            class="absolute top-0 block border-0"
            :style="{ left: `${left}px`, width: `${VIEW_W}px`, height: `${VIEW_H}px`, transform: `scale(${scale})`, transformOrigin: '0 0' }"
            @load="send"
        />
        <div v-if="src && !ready" class="absolute inset-0 grid place-items-center text-sm text-muted-color" role="status">
            <span class="flex items-center gap-2"><i class="pi pi-spin pi-spinner" aria-hidden="true" />{{ t('site.livePreviewLoading') }}</span>
        </div>
    </div>
</template>

<script setup lang="ts">
import type { SiteThemeMenuFields } from '~/composables/site/api/useSiteSettingsApi.types';
import type { HomeLayoutBody } from '~/composables/home/api/useHomeLayoutApi.types';
import type { GadgetLayoutOp } from '~/components/theme/GadgetLayoutEditor.vue';
import UtilGadgetLayoutEditor from '~/utils/util-gadget-layout-editor';

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
    /** 게시판 레이아웃의 적용 화면(저장 전 값) — 게시판 레이아웃일 때만(`home` 자리에 그 배치를 싣는다) */
    board?: { screens: { list: boolean; post: boolean } } | null;
    /** 휴대폰 폭으로 띄우는가(390 × 844 — 가운데, 줄이기만) */
    mobile?: boolean;
}
const props = withDefaults(defineProps<Props>(), { editing: false, path: '/', page: null, board: null, mobile: false });
const emit = defineEmits<{ (e: 'edit-menu'): void; (e: 'layout-op', value: GadgetLayoutOp): void }>();

const { t } = useI18n();

/** 공개 사이트를 띄울 창 크기 — 데스크톱 폭(본문 1200 + 여백) 1280 × 800 · 휴대폰 폭 390 × 844. 칸 폭에 맞춰 줄인다 */
const VIEW_W = computed(() => (props.mobile ? 390 : 1280));
const VIEW_H = computed(() => (props.mobile ? 844 : 800));

const { origin } = useUserWebOrigin();
const src = computed(() => (origin.value ? `${origin.value}${props.path.startsWith('/') ? props.path : `/${props.path}`}?adminPreview=1` : ''));

const box = ref<HTMLElement | null>(null);
const frame = ref<HTMLIFrameElement | null>(null);
/** 이 칸의 폭(px) — 아직 못 쟀으면 0 */
const boxW = ref(0);
/** 데스크톱 최대 배율 — 칸이 넓어도 이보다 키우지 않고 가운데에 둔다(넓은 모니터에서 실제보다 크게 보이던 것, 2026-11-23 사용자 — 광고 자리·캐시·레이아웃 미리보기 같은 값) */
const DESKTOP_MAX_SCALE = 0.85;
/** 줄인 비율 — 데스크톱은 칸 폭에 맞추되 최대 배율까지, 휴대폰 폭은 줄이기만 한다 */
const scale = computed(() => (boxW.value > 0 ? Math.min(props.mobile ? 1 : DESKTOP_MAX_SCALE, boxW.value / VIEW_W.value) : DESKTOP_MAX_SCALE));
/** 창 왼쪽 끝(px) — 남는 폭이 있으면 가운데 */
const left = computed(() => (boxW.value > 0 ? Math.max(0, (boxW.value - VIEW_W.value * scale.value) / 2) : 0));
const ready = ref(false);

/** 반응형 감싸기를 벗겨(JSON) 보낸다 — 가젯마다 `minH` 를 실어 크기 꼬리표가 편집기가 막는 높이 아래로 권하지 않게 */
function send(): void {
    const target = frame.value?.contentWindow;
    if (!target || !origin.value) return;
    const home = props.home ? { ...props.home, gadgets: props.home.gadgets.map((g) => ({ ...g, minH: UtilGadgetLayoutEditor.spec(g.type).minH })) } : null;
    const message = JSON.parse(JSON.stringify({ type: 'irobit:admin-preview', theme: props.theme, menu: props.menu, home, editing: props.editing, page: props.page, board: props.board }));
    target.postMessage(message, origin.value);
}

function onMessage(event: MessageEvent): void {
    if (!origin.value || event.origin !== origin.value || event.source !== frame.value?.contentWindow) return;
    const type = (event.data as { type?: unknown } | null)?.type;
    if (type === 'irobit:admin-preview-ready') {
        ready.value = true;
        send();
    } else if (type === 'irobit:admin-preview-edit-menu') {
        emit('edit-menu');
    } else if (type === 'irobit:admin-preview-layout') {
        const op = readOp(event.data);
        if (op) emit('layout-op', op);
    }
}

/** 홈 격자 편집 알림 읽기 — 모양이 아니면 null */
function readOp(data: unknown): GadgetLayoutOp | null {
    const d = data as Record<string, unknown>;
    if (typeof d.id !== 'string') return null;
    if (d.op === 'remove') return { op: 'remove', id: d.id };
    if (d.op === 'settings') return { op: 'settings', id: d.id };
    if (d.op === 'order' && (d.before === null || typeof d.before === 'string')) return { op: 'order', id: d.id, before: d.before as string | null };
    if (d.op === 'place' && Number.isFinite(d.x) && Number.isFinite(d.y)) return { op: 'place', id: d.id, x: Number(d.x), y: Number(d.y) };
    if (d.op === 'size' && Number.isFinite(d.w) && Number.isFinite(d.h)) return { op: 'size', id: d.id, w: Number(d.w), h: Number(d.h) };
    return null;
}

watch(() => [props.theme, props.menu, props.home, props.editing, props.page, props.board], send, { deep: true });
// 다른 페이지를 띄우면 「준비됨」을 다시 기다린다
watch(src, () => (ready.value = false));

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
