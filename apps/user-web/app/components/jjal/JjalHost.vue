<template>
    <!--
        짤 화면 자리 — 레이아웃(`layouts/default.vue`)에 한 번, 처음 열릴 때 코드를 받는다(`opened.jjal` — `useState('jjal-ui')`). 계약 docs/features/my-jjal-contract.md.
        - 짤 메뉴: 글·댓글의 짤을 누르면(휴대폰은 길게, `useJjalPress`) 「내 짤에 담기」 · 「크게 보기」 · 「짤 신고」. 담기 없는 짤(19금 게시판 · 서버 `collectable` false)은 둘만.
          데스크톱은 누른 짤 옆 팝업 메뉴, 휴대폰은 아래 시트.
        - 처음 안내 창: 처음 「담기」나 「올리기」 때 한 번 — 네 줄 + 「확인했어요」 체크 → [계속하기](계정에 기록 `intro-ack` 뒤 하려던 일) · [나중에](닫기만 — 다음에 또 나온다).
        - 올리기 창(`JjalUploadDialog` — 줄이기 라이브러리와 함께 처음 열 때 받는다) · 크게 보기 · 짤 신고(글·댓글 신고 창 `BoardReportDialog` 를 대상 종류 짤로).
        - 비회원: 담기·신고는 로그인 안내(올리기는 고르기 창에 탭이 없어 오지 않는다).
    -->
    <Menu v-if="desktop" ref="menu" :model="menuItems" popup data-testid="jjal-menu" />
    <AppSheet v-else v-model:visible="sheetOpen" :title="t('jjal.menuTitle')">
        <ul class="m-0 flex list-none flex-col p-0" data-testid="jjal-menu-sheet">
            <li v-for="item in menuItems" :key="item.key">
                <button
                    type="button"
                    class="flex w-full items-center gap-3 rounded-lg px-3 py-3 text-left text-sm hover:bg-surface-100 focus-visible:outline-2 focus-visible:outline-primary dark:hover:bg-surface-800"
                    :class="item.key === 'report' ? 'text-red-600 dark:text-red-400' : ''"
                    :data-testid="`jjal-menu-${item.key}`"
                    @click="item.command()"
                >
                    <i :class="item.icon" class="text-sm" aria-hidden="true" />{{ item.label }}
                </button>
            </li>
        </ul>
    </AppSheet>

    <!-- 크게 보기 — 저장본(긴 변 300px)을 두 배까지 키워 보인다 -->
    <Dialog v-model:visible="viewerOpen" modal dismissable-mask :header="t('jjal.viewTitle')" :draggable="false" :style="{ width: 'min(40rem, calc(100vw - 2rem))' }" data-testid="jjal-viewer">
        <div v-if="viewing" class="grid place-items-center">
            <img
                :src="viewing.url ?? undefined"
                alt=""
                :width="viewing.width || undefined"
                :height="viewing.height || undefined"
                class="h-auto max-h-[70vh] w-full object-contain"
                :style="{ maxWidth: `${Math.max(1, viewing.width) * 2}px` }"
            />
        </div>
    </Dialog>

    <!-- 처음 안내 창 -->
    <Dialog v-model:visible="introOpen" modal :header="t('jjal.introTitle')" :draggable="false" :style="{ width: 'min(26rem, calc(100vw - 2rem))' }" data-testid="jjal-intro">
        <ul class="m-0 flex list-none flex-col gap-2.5 p-0 text-sm leading-relaxed">
            <li v-for="line in INTRO_LINES" :key="line.key" class="flex items-start gap-2.5">
                <i :class="line.icon" class="mt-1 shrink-0 text-xs text-primary" aria-hidden="true" />
                <span>{{ t(line.key) }}</span>
            </li>
        </ul>
        <label :for="introCheckId" class="mt-4 flex cursor-pointer items-center gap-2 text-sm font-medium">
            <Checkbox v-model="introChecked" binary :input-id="introCheckId" data-testid="jjal-intro-check" />
            {{ t('jjal.introCheck') }}
        </label>
        <Message v-if="introError" severity="error" variant="simple" size="small" class="mt-2" role="alert">{{ introError }}</Message>
        <template #footer>
            <Button type="button" :label="t('jjal.introLater')" severity="secondary" variant="text" data-testid="jjal-intro-later" @click="introOpen = false" />
            <Button type="button" :label="t('jjal.introContinue')" :disabled="!introChecked" :loading="introBusy" data-testid="jjal-intro-continue" @click="confirmIntro" />
        </template>
    </Dialog>

    <LazyJjalUploadDialog v-if="uploadMounted" v-model:visible="uploadOpen" />

    <BoardReportDialog v-model:visible="reportOpen" target="JJAL" :loading="reporting" :error="reportError" :field-error="reportFieldError" @submit="submitReport" />
</template>

<script setup lang="ts">
import type { ReportReason } from '~/composables/report/api/useReportApi.types';
import type { JjalMenuTarget, JjalUiRequest } from '~/composables/jjal/useJjalUi';
import type { JjalCollectRequest, JjalView } from '~/composables/jjal/api/useJjalApi.types';
import UtilJjalMessages from '~/utils/util-jjal-messages';
import { ApiError } from '~/composables/common/useApiClient';

/** 메뉴 한 줄(데스크톱 `Menu` 의 `model` · 휴대폰 시트 같이) */
interface JjalMenuItem {
    key: 'collect' | 'view' | 'report';
    label: string;
    icon: string;
    command: () => void;
}

/** 처음 안내 다음에 할 일 */
type IntroNext = { kind: 'collect'; request: JjalCollectRequest } | { kind: 'upload' };

const INTRO_LINES = [
    { key: 'jjal.introPrivate', icon: 'pi pi-lock' },
    { key: 'jjal.introShare', icon: 'pi pi-users' },
    { key: 'jjal.introReport', icon: 'pi pi-flag' },
    { key: 'jjal.introKeep', icon: 'pi pi-bookmark' },
] as const;

const { t } = useI18n();
const desktop = useIsDesktop();
const ui = useJjalUi();
const mine = useMyJjals();
const catalog = useJjalCatalog();
const actions = useJjalActions();
const toast = useAppToast();
const { user } = useAuth();
const { prompt } = useLoginPrompt();
const introCheckId = useId();

/* ── 짤 메뉴 ── */
const menu = ref<{ show: (event: Event, target?: Element | null) => void; hide: () => void } | null>(null);
const sheetOpen = ref(false);
const target = ref<JjalMenuTarget | null>(null);

const menuItems = computed<JjalMenuItem[]>(() => {
    const current = target.value;
    if (!current) return [];
    const items: JjalMenuItem[] = [];
    const postId = current.postId;
    if (current.collectable && postId) {
        const request: JjalCollectRequest = { jjalId: current.id, postId, ...(current.commentId ? { commentId: current.commentId } : {}) };
        items.push({ key: 'collect', label: t('jjal.menuCollect'), icon: 'pi pi-bookmark', command: () => run(() => void collectFlow(request)) });
    }
    items.push({ key: 'view', label: t('jjal.menuView'), icon: 'pi pi-search-plus', command: () => run(() => openViewer(current.id)) });
    items.push({ key: 'report', label: t('jjal.menuReport'), icon: 'pi pi-flag', command: () => run(() => openReport(current.id)) });
    return items;
});

/** 메뉴를 닫고 고른 일을 한다 */
function run(task: () => void): void {
    sheetOpen.value = false;
    menu.value?.hide();
    task();
}

async function openMenu(next: JjalMenuTarget): Promise<void> {
    target.value = next;
    if (!desktop.value) {
        sheetOpen.value = true;
        return;
    }
    // 처음 열 때는 메뉴 부품이 아직 그려지지 않았다 — 한 박자 기다린다. 다른 짤을 눌렀으면 닫았다가 새 기준에 다시 연다
    await nextTick();
    const anchor = ui.menuAnchor();
    menu.value?.hide();
    await nextTick();
    menu.value?.show(anchor.event ?? new Event('click'), anchor.el);
}

/* ── 크게 보기 ── */
const viewerOpen = ref(false);
const viewing = ref<JjalView | null>(null);

function openViewer(id: string): void {
    viewing.value = catalog.resolve(id);
    if (viewing.value && !viewing.value.blocked) viewerOpen.value = true;
}

/* ── 처음 안내 ── */
const introOpen = ref(false);
const introChecked = ref(false);
const introBusy = ref(false);
const introError = ref('');
let introNext: IntroNext | null = null;

function openIntro(next: IntroNext): void {
    introNext = next;
    introChecked.value = false;
    introError.value = '';
    introOpen.value = true;
}

/** [계속하기] — 계정에 확인을 기록하고 하려던 일을 한다 */
async function confirmIntro(): Promise<void> {
    if (!introChecked.value || introBusy.value) return;
    introBusy.value = true;
    introError.value = '';
    try {
        await mine.acknowledgeIntro();
        introOpen.value = false;
        const next = introNext;
        introNext = null;
        if (next?.kind === 'collect') await actions.collect(next.request);
        else if (next?.kind === 'upload') openUploadDialog();
    } catch (e) {
        introError.value = UtilJjalMessages.error(t, e);
    } finally {
        introBusy.value = false;
    }
}

/** 처음 안내를 확인했는가 — 목록을 받아 본다(못 받으면 알리고 멈춘다) */
async function introReady(): Promise<boolean | null> {
    await mine.ensureLoaded();
    const list = mine.list.value;
    if (!list) {
        toast.error(t('jjal.loadFailed'));
        return null;
    }
    return list.introAcknowledged;
}

/* ── 담기 ── */
async function collectFlow(request: JjalCollectRequest): Promise<void> {
    if (!user.value) {
        prompt('scrap', undefined, { title: t('jjal.loginTitle'), description: t('jjal.loginText') });
        return;
    }
    const ready = await introReady();
    if (ready === null) return;
    if (!ready) openIntro({ kind: 'collect', request });
    else await actions.collect(request);
}

/* ── 올리기 ── */
const uploadMounted = ref(false);
const uploadOpen = ref(false);

function openUploadDialog(): void {
    const list = mine.list.value;
    if (list && list.count >= list.limit) {
        actions.showFull(list.limit);
        return;
    }
    uploadMounted.value = true;
    uploadOpen.value = true;
}

async function uploadFlow(): Promise<void> {
    if (!user.value) return;
    const ready = await introReady();
    if (ready === null) return;
    if (!ready) openIntro({ kind: 'upload' });
    else openUploadDialog();
}

/* ── 신고 ── */
const reportOpen = ref(false);
const reporting = ref(false);
const reportError = ref('');
const reportFieldError = ref('');
const reportId = ref<string | null>(null);

function openReport(id: string): void {
    if (!user.value) {
        prompt('report');
        return;
    }
    reportId.value = id;
    reportError.value = '';
    reportFieldError.value = '';
    reportOpen.value = true;
}

async function submitReport(value: { reason: ReportReason; detail: string | null }): Promise<void> {
    const id = reportId.value;
    if (!id || reporting.value) return;
    reporting.value = true;
    reportError.value = '';
    reportFieldError.value = '';
    try {
        await actions.report(id, value.reason, value.detail);
        reportOpen.value = false;
    } catch (e) {
        if (e instanceof ApiError && e.fields?.detail) reportFieldError.value = e.fields.detail;
        else reportError.value = UtilJjalMessages.error(t, e);
    } finally {
        reporting.value = false;
    }
}

/* ── 요청 받기 — 처음 그려질 때 이미 와 있는 요청도 처리한다(immediate) ── */
function handle(request: JjalUiRequest | null): void {
    if (!request) return;
    if (request.kind === 'menu') void openMenu(request.target);
    else if (request.kind === 'upload') void uploadFlow();
}

watch(
    () => ui.state.value.seq,
    () => handle(ui.state.value.request),
    { immediate: true },
);
</script>
