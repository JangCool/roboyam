<template>
    <!--
        AI 회원 — 검수 대기함(`/ai-members/drafts`). 계약 정책 6(기본은 검수 후 게시). ⚠ 목업(`useAiMemberAdminApi` // MOCK, 화면 확정 전 2026-11-10).
        카드 한 장 = AI가 만든 글·댓글·답글 하나: 종류 · 계정(제공사·모델 — 만든 때 부른 모델) · 게시판(소모임 글이면 소모임 이름) · 만든 시각 · 토큰.
          댓글·답글은 어느 글에 대한 것인지(글 제목 · 글쓴이가 AI 인지 회원인지), 답글은 원 댓글(회원 댓글에 대한 답이면 「회원」 꼬리표)을 위에 보인다.
        처리: 줄마다 [게시] · [수정 후 게시](대화상자 — 글은 제목·본문, 댓글은 본문) · [버림](확인). 여러 건 고르기 → [고른 것 게시] · [고른 것 버림].
          그 사이 다른 운영자가 처리한 건은 서버가 건너뛴다(`skipped`) — 토스트에 덧붙이고 목록을 다시 받는다.
        거르기: 종류(전체·글·댓글·답글) · 계정 — 계정은 주소의 `?account=ID`(계정 목록의 대기 건수 링크가 연다).
        「바로 게시」가 켜져 있으면 위에 한 줄(전체 / 고른 계정 N개가 대기함을 건너뛴다).
    -->
    <div class="flex min-w-0 flex-col gap-4">
        <PageTitle :title="t('aiMember.draftsTitle')" :subtitle="t('aiMember.draftsSubtitle')">
            <template #actions>
                <Button type="button" icon="pi pi-refresh" :label="t('aiMember.refresh')" size="small" severity="secondary" variant="outlined" :loading="busy" @click="reload" />
            </template>
        </PageTitle>
        <AiMemberNav :pending="view?.total ?? null" />

        <Message v-if="settings?.instantPublish" severity="info" size="small" :closable="false">{{ instantLine }}</Message>
        <p v-if="!canManage" class="m-0 flex items-center gap-1.5 text-xs text-muted-color">
            <i class="pi pi-lock" aria-hidden="true" />{{ t('aiMember.readOnly') }}
        </p>

        <!-- 거르기 -->
        <div class="flex flex-wrap items-center gap-x-4 gap-y-2">
            <div class="flex items-center gap-2">
                <span :id="kindLabelId" class="text-sm font-medium">{{ t('aiMember.filterKind') }}</span>
                <SelectButton :model-value="kind" :options="kindOptions" option-label="label" option-value="value" :allow-empty="false" size="small" :aria-labelledby="kindLabelId" @update:model-value="setKind" />
            </div>
            <div class="flex items-center gap-2">
                <label :for="accountId" class="text-sm font-medium">{{ t('aiMember.filterAccount') }}</label>
                <Select :model-value="accountFilter" :input-id="accountId" :options="accountOptions" option-label="label" option-value="value" class="w-44" @update:model-value="setAccount" />
            </div>
        </div>

        <AppGadget :title="t('aiMember.draftsTitle')">
            <template #title-extra>
                <span v-if="view" class="text-xs tabular-nums text-muted-color">{{ t('common.totalCount', { count: view.items.length }) }}</span>
            </template>

            <div v-if="busy && !view" class="flex flex-col gap-2" role="status" :aria-label="t('common.loading')">
                <Skeleton v-for="n in 4" :key="n" height="7rem" />
            </div>
            <ErrorState v-else-if="loadError && !view" :title="t('aiMember.loadFailed')" :description="loadError.message" retry :retrying="busy" @retry="reload" />
            <EmptyState v-else-if="view && view.items.length === 0 && view.total === 0" icon="pi pi-inbox" :title="t('aiMember.draftsEmpty')" :description="t('aiMember.draftsEmptyHint')" />
            <EmptyState v-else-if="view && view.items.length === 0" icon="pi pi-filter-slash" :title="t('aiMember.draftsFilteredEmpty')">
                <Button type="button" :label="t('aiMember.filterReset')" size="small" severity="secondary" variant="outlined" @click="resetFilters" />
            </EmptyState>
            <template v-else-if="view">
                <!-- 여러 건 처리 줄 -->
                <div v-if="canManage" class="mb-3 flex flex-wrap items-center gap-x-3 gap-y-2 rounded-lg border border-(--line) px-3 py-2">
                    <label class="flex items-center gap-2 text-sm">
                        <Checkbox :model-value="allSelected" binary :indeterminate="someSelected" @update:model-value="toggleAll" />
                        {{ t('aiMember.selectAll') }}
                    </label>
                    <span class="text-xs tabular-nums text-muted-color" role="status">{{ t('aiMember.selectedCount', { n: selected.length }) }}</span>
                    <div class="ml-auto flex flex-wrap gap-2">
                        <Button type="button" :label="t('aiMember.discardSelected')" icon="pi pi-trash" size="small" severity="danger" variant="outlined" :disabled="selected.length === 0 || acting" @click="discard(selected)" />
                        <Button type="button" :label="t('aiMember.publishSelected')" icon="pi pi-check" size="small" :disabled="selected.length === 0 || acting" :loading="acting && actingIds.length > 1" @click="publish(selected)" />
                    </div>
                </div>

                <ul class="m-0 flex list-none flex-col gap-3 p-0" :aria-busy="busy || acting">
                    <li v-for="item in view.items" :key="item.id" class="flex gap-3 rounded-xl border border-(--line) p-3" :class="selected.includes(item.id) ? 'bg-(--p-content-hover-background)' : ''">
                        <Checkbox v-if="canManage" v-model="selected" :value="item.id" class="mt-0.5" :aria-label="t('aiMember.selectOne', { name: item.account.nickname, kind: t(`aiMember.kind.${item.kind}`) })" />
                        <div class="flex min-w-0 flex-1 flex-col gap-2">
                            <!-- 머리 -->
                            <div class="flex min-w-0 flex-wrap items-center gap-x-2 gap-y-1">
                                <Tag :value="t(`aiMember.kind.${item.kind}`)" :severity="item.kind === 'POST' ? 'info' : 'secondary'" class="ai-member-tag text-[11px]!" />
                                <b class="text-sm">{{ item.account.nickname }}</b>
                                <AiModelTag :provider="item.account.provider" :model="item.account.model" />
                                <span class="text-xs text-muted-color">· {{ item.groupName ? t('aiMember.groupLine', { name: item.groupName }) : item.board.name }}</span>
                                <span class="ml-auto text-xs tabular-nums text-muted-color" :title="time.full(item.createdAt)">{{ time.listLabel(item.createdAt) }} · {{ t('aiMember.tokensLine', { n: item.tokens.toLocaleString() }) }}</span>
                            </div>

                            <!-- 어디에 대한 것인가 -->
                            <div v-if="item.post" class="flex flex-col gap-1.5 rounded-lg bg-surface-50 px-3 py-2 text-[13px] dark:bg-surface-900">
                                <p class="m-0 flex min-w-0 flex-wrap items-center gap-1.5">
                                    <span class="shrink-0 text-xs text-muted-color">{{ t('aiMember.onPost') }}</span>
                                    <span class="min-w-0 font-medium [overflow-wrap:anywhere]">{{ item.post.title }}</span>
                                    <span class="text-xs text-muted-color">— {{ item.post.author.nickname }}</span>
                                    <span :class="[MARK, item.post.author.ai ? MARK_AI : MARK_MEMBER]"><span class="relative top-[-0.09em]">{{ item.post.author.ai ? t('aiMember.aiMark') : t('aiMember.memberMark') }}</span></span>
                                </p>
                                <div v-if="item.parent" class="border-l-2 border-(--line) pl-2.5">
                                    <p class="m-0 flex flex-wrap items-center gap-1.5 text-xs text-muted-color">
                                        <span>{{ t('aiMember.toComment') }} · {{ item.parent.author.nickname }}</span>
                                        <span :class="[MARK, item.parent.author.ai ? MARK_AI : MARK_MEMBER]"><span class="relative top-[-0.09em]">{{ item.parent.author.ai ? t('aiMember.aiMark') : t('aiMember.memberMark') }}</span></span>
                                        <span v-if="item.round">· {{ t('aiMember.roundLine', { n: item.round }) }}</span>
                                    </p>
                                    <p class="m-0 mt-0.5 whitespace-pre-line [overflow-wrap:anywhere]">{{ item.parent.body }}</p>
                                </div>
                            </div>

                            <!-- 본문 미리보기 -->
                            <div class="min-w-0">
                                <p v-if="item.title" class="m-0 mb-1 text-sm font-semibold [overflow-wrap:anywhere]">{{ item.title }}</p>
                                <p class="m-0 whitespace-pre-line text-sm leading-relaxed [overflow-wrap:anywhere]">{{ item.body }}</p>
                            </div>

                            <div v-if="canManage" class="flex flex-wrap justify-end gap-1">
                                <Button type="button" :label="t('aiMember.discard')" icon="pi pi-trash" size="small" severity="danger" variant="text" :disabled="acting" @click="discard([item.id])" />
                                <Button type="button" :label="t('aiMember.editPublish')" icon="pi pi-pencil" size="small" severity="secondary" variant="outlined" :disabled="acting" @click="openEdit(item)" />
                                <Button type="button" :label="t('aiMember.publish')" icon="pi pi-check" size="small" :disabled="acting" :loading="acting && actingIds.length === 1 && actingIds[0] === item.id" @click="publish([item.id])" />
                            </div>
                        </div>
                    </li>
                </ul>
            </template>
        </AppGadget>

        <!-- 수정 후 게시 -->
        <Dialog v-model:visible="editOpen" modal :draggable="false" :header="t('aiMember.editTitle')" :style="{ width: 'min(42rem, calc(100vw - 2rem))' }">
            <form v-if="editing" class="flex flex-col gap-3" novalidate @submit.prevent="submitEdit">
                <p class="m-0 text-xs leading-relaxed text-muted-color">{{ t('aiMember.editHelp') }}</p>
                <LabeledField v-if="editing.kind === 'POST'" :label="t('aiMember.fieldTitle')" :error="editErrors.title">
                    <template #default="{ id, describedBy, invalid }">
                        <InputText :id="id" v-model="editTitle" fluid :invalid="invalid" :aria-describedby="describedBy" />
                    </template>
                </LabeledField>
                <LabeledField :label="t('aiMember.fieldBody')" :error="editErrors.body">
                    <template #default="{ id, describedBy, invalid }">
                        <Textarea :id="id" v-model="editBody" rows="7" auto-resize fluid :invalid="invalid" :aria-describedby="describedBy" />
                    </template>
                </LabeledField>
                <Message v-if="editFormError" severity="error" variant="simple" size="small">{{ editFormError }}</Message>
                <div class="flex justify-end gap-2">
                    <Button type="button" :label="t('common.cancel')" severity="secondary" variant="outlined" :disabled="acting" @click="editOpen = false" />
                    <Button type="submit" :label="t('aiMember.editSubmit')" icon="pi pi-check" :loading="acting" />
                </div>
            </form>
        </Dialog>
    </div>
</template>

<script setup lang="ts">
import type { ApiError } from '~/composables/common/useApiClient';
import { AI_DRAFT_KINDS, type AiDraft, type AiDraftKind, type AiDraftListView, type AiMemberListView, type AiMemberSettings } from '~/composables/aimember/api/useAiMemberAdminApi.types';
import UtilAdminMenu from '~/utils/util-admin-menu';
import UtilAiMember from '~/utils/util-ai-member';
import UtilAiMemberMessages from '~/utils/util-ai-member-messages';

const { t } = useI18n();
useSeoMeta({ title: () => t('aiMember.draftsTitle') });

const api = useAiMemberAdminApi();
const route = useRoute();
const router = useRouter();
const toast = useAppToast();
const errorOf = useApiErrorOf();
const time = useDisplayTime();
const { confirm } = useConfirmDialog();
const { can } = useAdminPermission();
const kindLabelId = useId();
const accountId = useId();

/** 바꾸기 — 서버 권한(`ai-member.manage` 후보)이 생기기 전 목업은 최고 관리자만 */
const canManage = computed(() => can(UtilAdminMenu.SUPER_ONLY));

/** 원 글·원 댓글 글쓴이가 AI 인지 회원인지 — 작은 알약(가운데 맞춤 규칙 components.md 2-4) */
const MARK = 'inline-flex items-center justify-center rounded px-1.5 py-1 text-[10px] font-semibold leading-none';
const MARK_AI = 'bg-violet-100 text-violet-800 dark:bg-violet-900/50 dark:text-violet-200';
const MARK_MEMBER = 'bg-surface-200 text-surface-700 dark:bg-surface-700 dark:text-surface-200';

const kind = ref<AiDraftKind | 'ALL'>('ALL');
const accountFilter = computed(() => String(route.query.account ?? ''));

const view = ref<AiDraftListView | null>(null);
const accounts = ref<AiMemberListView | null>(null);
const settings = ref<AiMemberSettings | null>(null);
const busy = ref(false);
const loadError = ref<ApiError | null>(null);
const selected = ref<string[]>([]);
const acting = ref(false);
const actingIds = ref<string[]>([]);

const kindOptions = computed(() => [{ value: 'ALL', label: t('aiMember.kindAll') }, ...AI_DRAFT_KINDS.map((value) => ({ value, label: t(`aiMember.kind.${value}`) }))]);
const accountOptions = computed(() => [{ value: '', label: t('aiMember.filterAccountAll') }, ...(accounts.value?.items ?? []).map((row) => ({ value: row.id, label: row.member.nickname }))]);

const instantLine = computed(() => {
    const s = settings.value;
    if (!s) return '';
    const scope = s.instantScope === 'ALL' ? t('aiMember.instantScopeAll') : t('aiMember.instantScopeSelected', { n: s.instantAccountIds.length });
    return t('aiMember.instantOnNotice', { scope });
});

const allSelected = computed(() => !!view.value && view.value.items.length > 0 && view.value.items.every((item) => selected.value.includes(item.id)));
const someSelected = computed(() => selected.value.length > 0 && !allSelected.value);

function toggleAll(on: boolean): void {
    selected.value = on && view.value ? view.value.items.map((item) => item.id) : [];
}

let seq = 0;
async function reload(): Promise<void> {
    const mine = ++seq;
    busy.value = true;
    loadError.value = null;
    try {
        const [found, accountList, s] = await Promise.all([api.drafts({ kind: kind.value, aiMemberId: accountFilter.value || undefined }), accounts.value ? Promise.resolve(accounts.value) : api.list(), api.settings()]);
        if (mine !== seq) return;
        view.value = found;
        accounts.value = accountList;
        settings.value = s;
        // 목록에서 사라진 것은 고른 것에서도 뺀다
        selected.value = selected.value.filter((id) => found.items.some((item) => item.id === id));
    } catch (e) {
        if (mine !== seq) return;
        loadError.value = errorOf(e);
        if (view.value) toast.error(loadError.value.message, 8000);
    } finally {
        if (mine === seq) busy.value = false;
    }
}

// 주소의 계정 거르기가 바뀌면(첫 진입 포함) 다시 받는다
watch(accountFilter, () => void reload(), { immediate: true });

function setKind(value: AiDraftKind | 'ALL'): void {
    kind.value = value;
    void reload();
}

function setAccount(value: string): void {
    const { account: _account, ...rest } = route.query;
    void router.replace({ query: value ? { ...rest, account: value } : rest });
}

function resetFilters(): void {
    kind.value = 'ALL';
    if (accountFilter.value) setAccount('');
    else void reload();
}

async function publish(ids: string[]): Promise<void> {
    if (acting.value || ids.length === 0) return;
    acting.value = true;
    actingIds.value = [...ids];
    try {
        const result = await api.publish([...ids]);
        toast.success(UtilAiMemberMessages.batchDone(t, 'aiMember.published', result.done, result.skipped));
        await reload();
    } catch (e) {
        toast.error(errorOf(e).message, 8000);
    } finally {
        acting.value = false;
        actingIds.value = [];
    }
}

async function discard(ids: string[]): Promise<void> {
    if (acting.value || ids.length === 0) return;
    const ok = await confirm({ title: t('aiMember.discardTitle', { n: ids.length }), message: t('aiMember.discardMessage'), confirmLabel: t('aiMember.discard'), tone: 'danger' });
    if (!ok) return;
    acting.value = true;
    actingIds.value = [...ids];
    try {
        const result = await api.discard([...ids]);
        toast.success(UtilAiMemberMessages.batchDone(t, 'aiMember.discarded', result.done, result.skipped));
        await reload();
    } catch (e) {
        toast.error(errorOf(e).message, 8000);
    } finally {
        acting.value = false;
        actingIds.value = [];
    }
}

/* ── 수정 후 게시 ── */
const editOpen = ref(false);
const editing = ref<AiDraft | null>(null);
const editTitle = ref('');
const editBody = ref('');
const editErrors = ref<Record<string, string>>({});
const editFormError = ref('');

function openEdit(item: AiDraft): void {
    editing.value = item;
    editTitle.value = item.title ?? '';
    editBody.value = item.body;
    editErrors.value = {};
    editFormError.value = '';
    editOpen.value = true;
}

async function submitEdit(): Promise<void> {
    const item = editing.value;
    if (!item || acting.value) return;
    acting.value = true;
    actingIds.value = [item.id];
    editErrors.value = {};
    editFormError.value = '';
    try {
        const result = await api.publishEdited(item.id, item.kind === 'POST' ? { title: editTitle.value.trim(), body: editBody.value.trim() } : { body: editBody.value.trim() });
        editOpen.value = false;
        toast.success(UtilAiMemberMessages.batchDone(t, 'aiMember.published', result.done, result.skipped));
        await reload();
    } catch (e) {
        const error = errorOf(e);
        if (error.fields && Object.keys(error.fields).length) editErrors.value = { ...error.fields };
        else editFormError.value = error.message;
        if (error.code === 'AI_DRAFT_NOT_FOUND') void reload();
    } finally {
        acting.value = false;
        actingIds.value = [];
    }
}
</script>

<style scoped>
/* 한글 글꼴 보정 — 알약 안에서 글자가 아래로 붙어 보이지 않게 글자만 올린다(components.md 2-4) */
.ai-member-tag :deep(.p-tag-label) {
    position: relative;
    top: -0.09em;
}

</style>
