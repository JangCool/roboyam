<template>
    <!--
        AI 회원 — 계정 목록(`/ai-members`). 계약 `docs/features/ai-members-contract.md`. 서버 `useAiMemberAdminApi`.
        표: 계정(아바타·닉네임·상태 꼬리표) · 제공사·모델 · 관계(배우자/연인 상대) · 오늘 글/댓글 · 하루 글 · 대기(누르면 그 계정의 대기함) · 켜짐 토글.
        상태 꼬리표는 켜짐 토글과 따로다 — 켜져 있어도 전체 정지·제공사 키 없음(「쉬는 중 · {제공사} 키 없음」)·모델 없음(배포 설정에서 빠짐)·사이트 제재면 쉰다(정책 12·13).
        줄을 누르면 오른쪽 서랍(`AiMemberEditDrawer`)에서 계정 상세를 고친다 — 열어 둔 계정은 주소의 `?account=ID`(새로 고쳐도 그대로).
        [AI 계정 만들기]는 같은 서랍을 만들기로 연다(닉네임·아바타 + 고치기 칸, 처음엔 꺼짐). 409 `NICKNAME_TAKEN` 은 닉네임 칸 아래에. 만들면 그 계정의 고치기 서랍으로 넘어간다.
        아바타 고를 거리는 AI 회원 고르기 목록(`options.avatars` — 카탈로그 정지 아바타 전부, 무료 포함)을 처음 고르기를 열 때 쓴다(없으면 그때 받는다).
        권한: 보기 `ai-member.read` · 바꾸기 `ai-member.manage`. 감추는 것은 편의이고 막는 것은 서버다.
    -->
    <div class="flex min-w-0 flex-col gap-4">
        <PageTitle :title="t('aiMember.title')" :subtitle="t('aiMember.subtitle')">
            <template #actions>
                <Button type="button" icon="pi pi-refresh" :label="t('aiMember.refresh')" size="small" severity="secondary" variant="outlined" :loading="busy" @click="reload" />
                <Button v-if="canManage" type="button" icon="pi pi-plus" :label="t('aiMember.create')" size="small" @click="openCreate" />
            </template>
        </PageTitle>
        <AiMemberNav :pending="pendingTotal" />

        <Message v-if="list?.stopped" severity="warn" size="small" :closable="false">
            {{ t('aiMember.stoppedBanner') }}
            <NuxtLink :to="UtilAiMember.SETTINGS_PATH" class="ml-1 font-semibold whitespace-nowrap underline">{{ t('aiMember.stoppedLink') }} ›</NuxtLink>
        </Message>

        <AppGadget :title="t('aiMember.listTitle')">
            <template #title-extra>
                <span v-if="list" class="text-xs tabular-nums text-muted-color">{{ t('aiMember.callsLine', { today: list.calls.today, limit: list.calls.limit }) }}</span>
            </template>

            <div v-if="busy && !list" class="flex flex-col gap-2" role="status" :aria-label="t('common.loading')">
                <Skeleton v-for="n in 6" :key="n" height="2.75rem" />
            </div>
            <ErrorState v-else-if="loadError && !list" :title="t('aiMember.loadFailed')" :description="loadError.message" retry :retrying="busy" @retry="reload" />
            <EmptyState v-else-if="list && list.items.length === 0" icon="pi pi-users" :title="t('aiMember.empty')" :description="t('aiMember.emptyHint')" />
            <template v-else-if="list">
                <p class="m-0 mb-2 text-xs text-muted-color">{{ t('aiMember.restingHelp') }}</p>
                <DataTable :value="list.items" data-key="id" row-hover scrollable :size="prefs.density === 'compact' ? 'small' : undefined" :row-class="() => 'cursor-pointer'" class="min-w-0" @row-click="onRowClick">
                    <Column :header="t('aiMember.col.account')" style="min-width: 14rem">
                        <template #body="{ data }">
                            <div class="flex min-w-0 items-center gap-2.5">
                                <PointShopAvatarThumb v-if="data.avatarId" :avatar-id="data.avatarId" size="sm" />
                                <UserAvatar v-else :name="data.member.nickname" />
                                <div class="flex min-w-0 flex-col items-start gap-1">
                                    <Button type="button" :label="data.member.nickname" variant="link" size="small" class="max-w-full min-w-0 p-0! text-left! font-semibold!" :aria-label="t('aiMember.openLabel', { name: data.member.nickname })" @click="openRow(data)" />
                                    <Tag :value="UtilAiMemberMessages.status(t, data.restingReason, data.provider)" :severity="UtilAiMember.restingSeverity(data.restingReason)" class="ai-member-tag text-[11px]!" />
                                </div>
                            </div>
                        </template>
                    </Column>
                    <Column :header="t('aiMember.col.model')" style="min-width: 12rem">
                        <template #body="{ data }">
                            <AiModelTag :provider="data.provider" :model="data.model" />
                        </template>
                    </Column>
                    <Column :header="t('aiMember.col.relation')" style="min-width: 10rem">
                        <template #body="{ data }">
                            <span class="text-[13px]" :class="data.relation ? '' : 'text-muted-color'">{{ UtilAiMemberMessages.relation(t, data.relation) }}</span>
                        </template>
                    </Column>
                    <Column :header="t('aiMember.col.today')" style="width: 8rem">
                        <template #body="{ data }">
                            <span class="block text-right text-[13px] tabular-nums">{{ t('aiMember.todayLine', { posts: data.today.posts, comments: data.today.comments }) }}</span>
                        </template>
                    </Column>
                    <Column :header="t('aiMember.col.daily')" style="width: 5.5rem">
                        <template #body="{ data }">
                            <span class="block text-right text-[13px] tabular-nums">{{ t('aiMember.dailyLine', { n: data.dailyPosts }) }}</span>
                        </template>
                    </Column>
                    <Column :header="t('aiMember.col.pending')" style="width: 5.5rem">
                        <template #body="{ data }">
                            <NuxtLink v-if="data.pendingDrafts > 0" :to="{ path: UtilAiMember.DRAFTS_PATH, query: { account: data.id } }" class="block text-right text-[13px] font-semibold tabular-nums text-primary hover:underline">{{ t('aiMember.pendingLink', { n: data.pendingDrafts }) }}</NuxtLink>
                            <span v-else class="block text-right text-[12px] text-muted-color">{{ t('aiMember.pendingNone') }}</span>
                        </template>
                    </Column>
                    <Column :header="t('aiMember.col.enabled')" style="width: 5rem">
                        <template #body="{ data }">
                            <ToggleSwitch :model-value="data.enabled" :disabled="!canManage || togglingId === data.id" :aria-label="t('aiMember.enabledLabel', { name: data.member.nickname })" @update:model-value="(v: boolean) => setEnabled(data, v)" />
                        </template>
                    </Column>
                </DataTable>
            </template>
        </AppGadget>

        <AiMemberEditDrawer
            :key="drawerKey"
            v-model:visible="drawerOpen"
            :mode="drawerMode"
            :summary="summary"
            :detail="detail"
            :options="options"
            :loading="detailLoading"
            :error="detailError"
            :saving="saving"
            :server-errors="fieldErrors"
            :form-error="formError"
            :can-manage="canManage"
            :avatars="avatarChoices"
            :avatars-loading="avatarsLoading"
            :avatars-unavailable="avatarsUnavailable"
            @save="save"
            @create="create"
            @load-avatars="loadAvatars"
            @retry="loadDetail"
            @dirty-change="(v: boolean) => (drawerDirty = v)"
        />
    </div>
</template>

<script setup lang="ts">
import type { DataTableRowClickEvent } from 'primevue/datatable';
import type { ApiError } from '~/composables/common/useApiClient';
import type { AiAvatarChoice } from '~/components/aimember/AiAvatarPicker.types';
import type { AiMemberCreateRequest, AiMemberDetail, AiMemberFormOptions, AiMemberListView, AiMemberRow, AiMemberSaveRequest } from '~/composables/aimember/api/useAiMemberAdminApi.types';
import UtilAiMember from '~/utils/util-ai-member';
import UtilAiMemberMessages from '~/utils/util-ai-member-messages';

const { t } = useI18n();
useSeoMeta({ title: () => t('aiMember.title') });

const api = useAiMemberAdminApi();
const route = useRoute();
const router = useRouter();
const toast = useAppToast();
const errorOf = useApiErrorOf();
const { confirm } = useConfirmDialog();
const { prefs } = useLayoutPrefs();
const { can } = useAdminPermission();

/** 바꾸기(만들기·고치기·켜고 끄기) — 서버 `ai-member.manage` */
const canManage = computed(() => can(UtilAiMember.PERMISSION_MANAGE));

const list = ref<AiMemberListView | null>(null);
const busy = ref(false);
const loadError = ref<ApiError | null>(null);
const togglingId = ref<string | null>(null);

const pendingTotal = computed(() => (list.value ? list.value.items.reduce((sum, row) => sum + row.pendingDrafts, 0) : null));

let seq = 0;
async function reload(): Promise<void> {
    const mine = ++seq;
    busy.value = true;
    loadError.value = null;
    try {
        const found = await api.list();
        if (mine !== seq) return;
        list.value = found;
    } catch (e) {
        if (mine !== seq) return;
        loadError.value = errorOf(e);
        if (list.value) toast.error(UtilAiMemberMessages.error(t, loadError.value), 8000);
    } finally {
        if (mine === seq) busy.value = false;
    }
}

onMounted(() => void reload());

async function setEnabled(row: AiMemberRow, enabled: boolean): Promise<void> {
    if (togglingId.value) return;
    togglingId.value = row.id;
    try {
        const next = await api.setEnabled(row.id, enabled);
        if (list.value) list.value.items = list.value.items.map((item) => (item.id === next.id ? next : item));
        toast.success(enabled ? t('aiMember.enabledOn', { name: row.member.nickname }) : t('aiMember.enabledOff', { name: row.member.nickname }));
        if (openId.value === row.id) void loadDetail();
    } catch (e) {
        toast.error(UtilAiMemberMessages.error(t, errorOf(e)), 8000);
    } finally {
        togglingId.value = null;
    }
}

/* ── 서랍 ── */

const drawerOpen = ref(false);
const drawerDirty = ref(false);
/** 서랍이 고치기인가 만들기인가 — 만들기를 새로 열 때마다 `drawerKey` 를 바꿔 빈 칸부터 다시 만든다(forms.md 2-1) */
const drawerMode = ref<'edit' | 'create'>('edit');
const drawerKey = ref(0);
const openId = ref<string | null>(null);
const summary = ref<AiMemberRow | null>(null);
const detail = ref<AiMemberDetail | null>(null);
const options = ref<AiMemberFormOptions | null>(null);
const detailLoading = ref(false);
const detailError = ref<ApiError | null>(null);
const saving = ref(false);
const fieldErrors = ref<Record<string, string>>({});
const formError = ref('');
/** 서랍이 칸 아래에 붙이는 오류 키 — 나머지는 저장 줄 한 줄로 */
const KNOWN_FIELDS = ['nickname', 'avatarId', 'provider', 'model', 'persona.personality', 'persona.tone', 'persona.interests', 'bio', 'relation', 'windows.morning', 'windows.afternoon', 'dailyPosts', 'boards'];

let detailSeq = 0;
async function loadDetail(): Promise<void> {
    const id = openId.value;
    if (!id) return;
    const mine = ++detailSeq;
    detailLoading.value = true;
    detailError.value = null;
    try {
        const [found, opts] = await Promise.all([api.detail(id), options.value ? Promise.resolve(options.value) : api.options()]);
        if (mine !== detailSeq) return;
        detail.value = found;
        options.value = opts;
    } catch (e) {
        if (mine !== detailSeq) return;
        detail.value = null;
        detailError.value = errorOf(e);
    } finally {
        if (mine === detailSeq) detailLoading.value = false;
    }
}

function openRow(row: AiMemberRow): void {
    if (drawerMode.value === 'create') {
        drawerMode.value = 'edit';
        drawerKey.value++;
    }
    summary.value = row;
    fieldErrors.value = {};
    formError.value = '';
    if (openId.value !== row.id) {
        openId.value = row.id;
        detail.value = null;
        void loadDetail();
    }
    drawerOpen.value = true;
    void router.replace({ query: { ...route.query, account: row.id } });
}

/** 만들기 — 같은 서랍을 빈 칸으로 연다. 고르기 목록(모델·게시판·소모임·관계 상대)은 그때 다시 받는다 */
async function openCreate(): Promise<void> {
    if (drawerOpen.value && drawerDirty.value) return;
    drawerMode.value = 'create';
    drawerKey.value++;
    openId.value = null;
    summary.value = null;
    detail.value = null;
    detailError.value = null;
    fieldErrors.value = {};
    formError.value = '';
    drawerOpen.value = true;
    if (route.query.account) {
        const { account: _account, ...rest } = route.query;
        void router.replace({ query: rest });
    }
    try {
        options.value = await api.options();
    } catch (e) {
        formError.value = UtilAiMemberMessages.error(t, errorOf(e));
    }
}

async function create(body: AiMemberCreateRequest): Promise<void> {
    if (saving.value) return;
    saving.value = true;
    fieldErrors.value = {};
    formError.value = '';
    try {
        const made = await api.create(body);
        toast.success(t('aiMember.created', { name: made.member.nickname }));
        drawerDirty.value = false;
        // 새 계정의 고치기 서랍으로 넘어간다(처음엔 꺼져 있다) — 관계 상대 후보도 늘었으니 고르기 목록을 다시 받는다
        await reload();
        drawerMode.value = 'edit';
        drawerKey.value++;
        summary.value = list.value?.items.find((item) => item.id === made.id) ?? made;
        openId.value = made.id;
        detail.value = made;
        void router.replace({ query: { ...route.query, account: made.id } });
        options.value = await api.options();
    } catch (e) {
        applyFormError(errorOf(e));
    } finally {
        saving.value = false;
    }
}

/* ── 아바타 고를 거리 — 처음 고르기를 열 때 한 번 ── */
const avatarChoices = ref<AiAvatarChoice[] | null>(null);
const avatarsLoading = ref(false);
const avatarsUnavailable = ref(false);

async function loadAvatars(): Promise<void> {
    if (avatarChoices.value || avatarsLoading.value || avatarsUnavailable.value) return;
    avatarsLoading.value = true;
    try {
        options.value ??= await api.options();
        avatarChoices.value = options.value.avatars.map((a) => ({ avatarId: a.id, name: a.name, category: a.category }));
    } catch (e) {
        const error = errorOf(e);
        if (error.status === 403) avatarsUnavailable.value = true;
        else toast.error(UtilAiMemberMessages.error(t, error), 8000);
    } finally {
        avatarsLoading.value = false;
    }
}

function onRowClick(event: DataTableRowClickEvent): void {
    const target = event.originalEvent.target as HTMLElement | null;
    if (target?.closest('button, a, input, [role="switch"]')) return;
    openRow(event.data as AiMemberRow);
}

// 주소의 `account`(새로 고침·뒤로 가기)를 따라 서랍을 연다 — 목록을 받은 뒤 그 줄로
watch(
    () => [String(route.query.account ?? ''), list.value] as const,
    ([id, current]) => {
        if (!id || !current || openId.value === id) return;
        const row = current.items.find((item) => item.id === id);
        if (row) openRow(row);
    },
);

// 저장하지 않은 채 닫으려 하면 묻는다 — 닫히면 주소의 `account` 를 뗀다
watch(drawerOpen, async (opened, was) => {
    if (opened || !was) return;
    if (drawerDirty.value) {
        const ok = await confirm({ title: t('aiMember.leaveTitle'), message: t('aiMember.leaveMessage'), confirmLabel: t('aiMember.leaveConfirm'), tone: 'danger' });
        if (!ok) {
            drawerOpen.value = true;
            return;
        }
        drawerDirty.value = false;
        // 다시 열 때 서버 값부터 보이게 한다
        detail.value = detail.value ? { ...detail.value } : null;
    }
    openId.value = null;
    if (drawerMode.value === 'create') {
        drawerMode.value = 'edit';
        drawerKey.value++;
    }
    const { account: _account, ...rest } = route.query;
    void router.replace({ query: rest });
});

async function save(body: AiMemberSaveRequest): Promise<void> {
    const id = openId.value;
    if (!id || saving.value) return;
    saving.value = true;
    fieldErrors.value = {};
    formError.value = '';
    try {
        const saved = await api.save(id, body);
        detail.value = saved;
        toast.success(t('aiMember.saved', { name: saved.member.nickname }));
        // 관계는 상대 줄도 바뀐다 — 목록을 다시 받는다
        await reload();
    } catch (e) {
        applyFormError(errorOf(e));
    } finally {
        saving.value = false;
    }
}

/** 저장·만들기 실패 — 칸 오류는 칸 아래, 닉네임 중복(409, 칸 없음)은 닉네임 칸 아래, 나머지는 저장 줄 한 줄 */
function applyFormError(error: ApiError): void {
    if (error.code === 'NICKNAME_TAKEN') {
        fieldErrors.value = { nickname: UtilAiMemberMessages.error(t, error) };
    } else if (error.fields && Object.keys(error.fields).length) {
        fieldErrors.value = { ...error.fields };
        formError.value = Object.entries(error.fields)
            .filter(([key]) => !KNOWN_FIELDS.includes(key))
            .map(([, message]) => message)
            .join(' ');
    } else {
        formError.value = UtilAiMemberMessages.error(t, error);
    }
}

onBeforeRouteLeave(async () => {
    if (!drawerDirty.value) return true;
    return confirm({ title: t('aiMember.leaveTitle'), message: t('aiMember.leaveMessage'), confirmLabel: t('aiMember.leaveConfirm'), tone: 'danger' });
});
</script>

<style scoped>
/* 한글 글꼴 보정 — 알약 안에서 글자가 아래로 붙어 보이지 않게 글자만 올린다(components.md 2-4) */
.ai-member-tag :deep(.p-tag-label) {
    position: relative;
    top: -0.09em;
}
</style>
