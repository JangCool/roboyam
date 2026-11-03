<template>
    <!--
        대시보드 — 운영자마다 따로 꾸미는 가젯 화면(구성 = `useDashboardLayoutApi` — `GET`·`PUT /api/admin/me/dashboard`, 서버가 세션의 운영자로 고른다).
        · 저장 실패의 칸 오류(`400 fields` — `gadgets[i].<칸>` · `version` · `columns` · `gadgets`)는 편집 도구 줄 바로 아래에 줄마다(가젯 이름을 붙여) 보인다. 다른 실패는 알림(토스트).
        · 저장한 적 없으면 그 운영자 권한으로 볼 수 있는 가젯으로 기본 구성(처리할 일·커머스 맨 위 전체 폭 → 나머지 3분의 1 폭, `UtilDashboardLayout.defaults`). 저장 전까지는 기본 구성 그대로다.
        · 보기 모드가 기본. [화면 편집] → 가젯 끌어 옮기기 · 모서리로 크기 · ⚙ 설정 · ✕ 빼기, 도구 줄 [가젯 추가] [기본 구성으로] [취소] [저장]. 저장 전 변경이 있으면 나갈 때 묻는다.
          좁은 화면(lg 미만)은 한 열로 쌓고 편집할 수 없다(단추를 막고 안내).
        · 가젯 종류·권한·크기·서버 부담 원칙은 `utils/util-dashboard-layout.ts`, 격자·끌기·키보드는 `DashboardGrid`, 종류별 내용은 `components/dashboard/gadget/`.
        처리할 일(`TODO`)·커머스(`COMMERCE_TODO`) 가젯은 원래 이 화면의 두 칸을 옮긴 것이다(부르는 API·규칙 그대로).
    -->
    <section class="flex flex-col gap-4">
        <PageTitle :title="t('dashboard.title')" :subtitle="editing ? t('dashboard.editLead') : ''">
            <template #actions>
                <template v-if="editing">
                    <Button type="button" icon="pi pi-plus" :label="t('dashboard.addGadget')" size="small" severity="secondary" variant="outlined" :disabled="saving" @click="addOpen = true" />
                    <Button type="button" icon="pi pi-replay" :label="t('dashboard.resetDefaults')" size="small" severity="secondary" variant="outlined" :disabled="saving" @click="resetDefaults" />
                    <Button type="button" :label="t('common.cancel')" size="small" severity="secondary" variant="text" :disabled="saving" @click="cancelEdit" />
                    <Button type="button" icon="pi pi-check" :label="t('common.save')" size="small" :loading="saving" :disabled="!dirty" @click="save" />
                </template>
                <template v-else-if="gadgets">
                    <span v-if="!wide" class="text-[12px] text-muted-color">{{ t('dashboard.editWideOnly') }}</span>
                    <Button type="button" icon="pi pi-th-large" :label="t('dashboard.edit')" size="small" severity="secondary" variant="outlined" :disabled="!wide" @click="startEdit" />
                </template>
            </template>
        </PageTitle>

        <Message v-if="editing && saveProblems.length" severity="error" :closable="false">
            <span class="flex flex-col gap-1">
                <span class="font-medium">{{ t('dashboard.saveProblemsTitle') }}</span>
                <ul class="m-0 flex list-disc flex-col gap-0.5 pl-5 text-[13px]">
                    <li v-for="line in saveProblems" :key="line">{{ line }}</li>
                </ul>
            </span>
        </Message>
        <Message v-if="layoutError" severity="warn" :closable="false">
            <span class="flex flex-wrap items-center gap-x-3 gap-y-1">
                {{ t('dashboard.layoutLoadFailed', { reason: layoutError.message }) }}
                <Button type="button" :label="t('common.retry')" size="small" variant="text" :loading="layoutLoading" @click="loadLayout" />
            </span>
        </Message>
        <Message v-if="editing && !wide" severity="info" :closable="false">{{ t('dashboard.editNarrow') }}</Message>

        <div v-if="!gadgets" class="grid grid-cols-1 gap-4 lg:grid-cols-3" role="status" :aria-label="t('common.loading')">
            <Skeleton class="lg:col-span-3" height="20rem" border-radius="0.75rem" />
            <Skeleton v-for="n in 3" :key="n" height="16rem" border-radius="0.75rem" />
        </div>
        <EmptyState v-else-if="!shownCount" icon="pi pi-th-large" :title="t('dashboard.noGadgetsTitle')" :description="t('dashboard.noGadgetsBody')">
            <Button v-if="!editing && wide" type="button" icon="pi pi-th-large" :label="t('dashboard.edit')" size="small" @click="startEdit" />
            <Button v-else-if="editing" type="button" icon="pi pi-plus" :label="t('dashboard.addGadget')" size="small" @click="addOpen = true" />
        </EmptyState>
        <DashboardGrid v-else :gadgets="editing ? draft : gadgets" :editing="editing" @update:gadgets="(v) => (draft = v)" @settings="openSettings" @remove="removeGadget" />

        <DashboardAddDialog v-model:visible="addOpen" :gadgets="draft" @add="addGadget" />
        <DashboardGadgetSettingsDialog v-model:visible="settingsOpen" :gadget="settingsGadget" @apply="applySettings" />
    </section>
</template>

<script setup lang="ts">
import type { ApiError } from '~/composables/common/useApiClient';
import type { DashboardGadgetConfig, DashboardGadgetItem, DashboardGadgetType, DashboardLayoutBody } from '~/composables/dashboard/api/useDashboardLayoutApi.types';
import UtilDashboardLayout from '~/utils/util-dashboard-layout';
import UtilDashboardMessages from '~/utils/util-dashboard-messages';

const { t } = useI18n();
useSeoMeta({ title: () => t('dashboard.title') });

const api = useDashboardLayoutApi();
const toApiError = useApiErrorOf();
const toast = useAppToast();
const { confirm } = useConfirmDialog();
const { can } = useAdminPermission();
const wide = useDashboardWide();

/** 화면에 쓰는 구성(저장한 것 또는 기본 구성) — 받기 전 null */
const gadgets = ref<DashboardGadgetItem[] | null>(null);
const layoutLoading = ref(false);
const layoutError = shallowRef<ApiError | null>(null);

const editing = ref(false);
/** 편집 중인 구성 */
const draft = ref<DashboardGadgetItem[]>([]);
const saving = ref(false);
/** 마지막 저장의 칸 오류(400 `fields`)와 그때 보낸 본문 — `gadgets[i]` 를 가젯 이름으로 옮기려고 본문을 함께 둔다 */
const saveFailure = shallowRef<{ fields: Record<string, string>; body: DashboardLayoutBody } | null>(null);
const saveProblems = computed(() => (saveFailure.value ? UtilDashboardMessages.saveProblems(t, saveFailure.value.fields, saveFailure.value.body) : []));

const dirty = computed(() => editing.value && gadgets.value !== null && !UtilDashboardLayout.same(draft.value, gadgets.value));

/** 그릴 가젯 수 — 보기는 권한 있는 것만, 편집은 전부 */
const shownCount = computed(() => {
    if (editing.value) return draft.value.length;
    return (gadgets.value ?? []).filter((g) => UtilDashboardLayout.allowed(g.type, can)).length;
});

/** 같은 구성 안에서 겹치지 않는 가젯 ID */
function newId(type: DashboardGadgetType): string {
    return `${type.toLowerCase()}-${Date.now().toString(36)}${Math.random().toString(36).slice(2, 6)}`;
}

async function loadLayout(): Promise<void> {
    layoutLoading.value = true;
    layoutError.value = null;
    try {
        const body = await api.get();
        gadgets.value = UtilDashboardLayout.read(body) ?? UtilDashboardLayout.defaults(can, newId);
    } catch (e) {
        // 읽지 못하면 기본 구성으로 보이고 다시 시도를 준다(저장하면 덮어쓴다)
        layoutError.value = toApiError(e);
        gadgets.value ??= UtilDashboardLayout.defaults(can, newId);
    } finally {
        layoutLoading.value = false;
    }
}

function startEdit(): void {
    if (!gadgets.value || !wide.value) return;
    draft.value = gadgets.value.map((g) => ({ ...g, config: { ...g.config } }));
    saveFailure.value = null;
    editing.value = true;
}

async function cancelEdit(): Promise<void> {
    if (dirty.value && !(await confirm({ title: t('dashboard.discardTitle'), message: t('dashboard.discardMessage'), confirmLabel: t('dashboard.discardConfirm'), tone: 'danger' }))) return;
    saveFailure.value = null;
    editing.value = false;
}

async function save(): Promise<void> {
    if (!dirty.value) return;
    saving.value = true;
    saveFailure.value = null;
    const body = UtilDashboardLayout.toBody(draft.value);
    try {
        const saved = await api.put(body);
        gadgets.value = UtilDashboardLayout.read(saved) ?? UtilDashboardLayout.read(body) ?? [];
        layoutError.value = null;
        editing.value = false;
        toast.success(t('dashboard.saved'));
    } catch (e) {
        const failure = toApiError(e);
        // 칸 오류는 도구 줄 아래에 남기고(고칠 때까지 보이게), 나머지는 알림
        if (failure.status === 400 && failure.fields && Object.keys(failure.fields).length) saveFailure.value = { fields: failure.fields, body };
        else toast.error(t('dashboard.saveFailed', { reason: failure.message }));
    } finally {
        saving.value = false;
    }
}

/** 기본 구성으로 다시 만든다 — 저장해야 적용된다 */
async function resetDefaults(): Promise<void> {
    if (!(await confirm({ title: t('dashboard.resetTitle'), message: t('dashboard.resetMessage'), confirmLabel: t('dashboard.resetConfirm') }))) return;
    draft.value = UtilDashboardLayout.defaults(can, newId);
}

/* ── 가젯 추가 · 빼기 · 설정 ── */

const addOpen = ref(false);

function addGadget(type: DashboardGadgetType): void {
    if (!UtilDashboardLayout.addable(draft.value, type)) return;
    draft.value = UtilDashboardLayout.add(draft.value, newId(type), type);
    addOpen.value = false;
    toast.show(t('dashboard.addedToast', { title: t(`dashboard.kind.${type}`) }));
}

function removeGadget(id: string): void {
    draft.value = UtilDashboardLayout.remove(draft.value, id);
}

const settingsOpen = ref(false);
const settingsId = ref<string | null>(null);
const settingsGadget = computed(() => draft.value.find((g) => g.id === settingsId.value) ?? null);

function openSettings(id: string): void {
    settingsId.value = id;
    settingsOpen.value = true;
}

function applySettings(config: DashboardGadgetConfig): void {
    const id = settingsId.value;
    draft.value = draft.value.map((g) => (g.id === id ? { ...g, config } : g));
    settingsOpen.value = false;
}

/* ── 저장하지 않은 변경 ── */

onBeforeRouteLeave(async () => {
    if (!dirty.value) return true;
    return confirm({ title: t('dashboard.leaveTitle'), message: t('dashboard.leaveMessage'), confirmLabel: t('dashboard.leaveConfirm'), cancelLabel: t('dashboard.leaveCancel'), tone: 'danger' });
});

function onBeforeUnload(event: BeforeUnloadEvent): void {
    if (!dirty.value) return;
    event.preventDefault();
}

onMounted(() => {
    void loadLayout();
    window.addEventListener('beforeunload', onBeforeUnload);
});
onBeforeUnmount(() => window.removeEventListener('beforeunload', onBeforeUnload));
</script>
