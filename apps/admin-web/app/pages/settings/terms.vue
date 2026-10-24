<template>
    <!--
        약관(`/settings/terms`) — 이용약관·개인정보처리방침 본문을 초안으로 쓰고 버전으로 게시한다(`docs/features/terms-contract.md` 「화면」).
        구획: ① 링크 옵션(「가입·로그인 화면에 약관 보기 링크 표시」 스위치 — 누르면 바로 저장) ② 종류 탭(이용약관·개인정보처리방침) — 상태(지금 버전·시행일, 시행 예정),
        초안 편집기 [초안 저장]·[초안 삭제]·[게시…](시행일·변경 요지 대화상자 — `TermsPublishDialog`), 이력 표(버전·시행일·게시 시각·게시자·상태) → 본문 보기 대화상자(`TermsVersionDialog`) · [이 본문으로 초안 만들기].
        - 게시한 버전은 고치지 않는다. [게시] 는 **저장한 초안**으로 새 버전을 만들고 초안을 비운다 — 저장하지 않은 변경이 있으면 [게시] 를 막고 먼저 저장하라고 알린다.
        - 본문 편집은 `@roboyam/rich-editor` 의 간단 수준(`SIMPLE` — 굵게·기울임·밑줄·취소선·링크·목록·인용). 서버 허용 태그(`… h2 h3 hr`)보다 좁다 — 고급 수준은 서버가 버리는 코드·글자색·유튜브까지 켜므로 쓰지 않는다.
        - 입력 오류는 서버 `fields.body` 를 편집기 아래, 게시는 대화상자의 칸 아래(`fields.effectiveDate`·`changeNote`, 칸 밖 `fields.draft`)에 서버 문구 그대로.
        - 권한: 화면 `terms.read`(메뉴가 거른다) · 초안·게시·옵션 `terms.manage` — 없으면 스위치가 잠기고 편집기는 읽기 전용, 단추가 없다. 감사는 서버가 남긴다.
        - 저장하지 않은 초안 변경이 있는데 다른 탭·화면으로 가면 묻는다.
        서버 API: `useTermsApi`.
    -->
    <div class="flex flex-col gap-4">
        <PageTitle :title="t('terms.title')" :subtitle="t('terms.subtitle')" />

        <Message v-if="!canManage" severity="info" :closable="false" size="small">{{ t('terms.readOnly') }}</Message>

        <div v-if="loading && !overview" class="flex flex-col gap-4" role="status" :aria-label="t('common.loading')">
            <AppGadget v-for="n in 2" :key="n" :title="t('common.loading')">
                <div class="flex flex-col gap-3">
                    <Skeleton height="0.75rem" width="60%" />
                    <Skeleton height="2.25rem" width="min(36rem, 100%)" />
                </div>
            </AppGadget>
        </div>

        <AppGadget v-else-if="!overview" :title="t('terms.title')">
            <ErrorState :title="t('terms.loadFailed')" :description="loadError?.message" retry :retrying="loading" @retry="loadOverview()" />
        </AppGadget>

        <template v-else>
            <!-- ① 링크 옵션 — 누르면 바로 저장된다 -->
            <AppGadget :title="t('terms.linksTitle')">
                <div class="flex items-start gap-3">
                    <ToggleSwitch :model-value="overview.showLinks" :input-id="linksId" :disabled="!canManage || savingLinks" :aria-describedby="`${linksId}-help`" @update:model-value="setShowLinks" />
                    <div class="flex min-w-0 flex-col gap-0.5">
                        <label :for="linksId" class="text-sm font-medium" :class="canManage ? 'cursor-pointer' : ''">{{ t('terms.linksLabel') }}</label>
                        <p :id="`${linksId}-help`" class="m-0 text-xs leading-relaxed text-muted-color">{{ t('terms.linksHelp') }}</p>
                    </div>
                </div>
            </AppGadget>

            <!-- ② 종류 탭 -->
            <Tabs :key="tabsKey" :value="activeKind" class="min-w-0" @update:value="(v: string | number) => selectKind(String(v) as TermsKind)">
                <TabList :aria-label="t('terms.kindTabs')">
                    <Tab v-for="kind in KINDS" :key="kind" :value="kind">{{ kindLabel(kind) }}</Tab>
                </TabList>
            </Tabs>

            <!-- 상태 -->
            <AppGadget :title="t('terms.statusTitle')">
                <dl class="m-0 grid grid-cols-[auto_1fr] gap-x-4 gap-y-1.5 text-sm">
                    <dt class="text-muted-color">{{ t('terms.statusCurrent') }}</dt>
                    <dd class="m-0">
                        <template v-if="activeOverview?.current">{{ t('terms.versionLine', { version: activeOverview.current.version, date: time.serviceDate(activeOverview.current.effectiveDate) }) }}</template>
                        <span v-else class="text-muted-color">{{ t('terms.statusNone') }}</span>
                    </dd>
                    <dt class="text-muted-color">{{ t('terms.statusUpcoming') }}</dt>
                    <dd class="m-0">
                        <template v-if="activeOverview?.upcoming">{{ t('terms.versionLine', { version: activeOverview.upcoming.version, date: time.serviceDate(activeOverview.upcoming.effectiveDate) }) }}</template>
                        <span v-else class="text-muted-color">{{ t('terms.statusNoUpcoming') }}</span>
                    </dd>
                </dl>
            </AppGadget>

            <div v-if="detailLoading && !detail" class="flex flex-col gap-2" role="status" :aria-label="t('common.loading')">
                <Skeleton height="16rem" />
                <Skeleton height="8rem" />
            </div>

            <AppGadget v-else-if="!detail" :title="kindLabel(activeKind)">
                <ErrorState :title="t('terms.detailLoadFailed')" :description="detailError?.message" retry :retrying="detailLoading" @retry="loadDetail()" />
            </AppGadget>

            <template v-else>
                <!-- 초안 편집기 -->
                <AppGadget :title="t('terms.draftTitle')">
                    <template v-if="canManage" #actions>
                        <Button type="button" :label="t('terms.publishOpen')" icon="pi pi-send" size="small" :disabled="!canPublish" @click="openPublish" />
                    </template>

                    <div class="flex flex-col gap-1.5">
                        <div class="flex flex-wrap items-baseline justify-between gap-2">
                            <span :id="bodyLabelId" class="text-sm font-medium">{{ t('terms.bodyLabel', { name: kindLabel(activeKind) }) }}</span>
                            <span class="text-xs text-muted-color">{{ draftMeta }}</span>
                        </div>
                        <RichEditor
                            :key="`terms-editor-${activeKind}-${editorKey}`"
                            v-model="editorHtml"
                            level="SIMPLE"
                            :disabled="!canManage || working !== null"
                            :invalid="Boolean(bodyError)"
                            :labelledby="bodyLabelId"
                            :describedby="bodyMessageId"
                            :placeholder="t('terms.bodyPlaceholder')"
                            @ready="onEditorReady"
                        />
                        <div :id="bodyMessageId" aria-live="polite" class="text-xs leading-relaxed">
                            <p v-if="bodyError" role="alert" class="m-0 text-red-600 dark:text-red-400">{{ bodyError }}</p>
                            <p v-else-if="canManage && dirty" class="m-0 text-amber-700 dark:text-amber-400">{{ t('terms.unsavedHint') }}</p>
                            <p v-else class="m-0 text-muted-color">{{ t('terms.bodyHelp') }}</p>
                        </div>
                    </div>

                    <div v-if="canManage" class="mt-3 flex flex-wrap items-center justify-end gap-2">
                        <Message v-if="draftError" class="mr-auto" severity="error" size="small" variant="simple" :closable="false" role="alert">{{ draftError }}</Message>
                        <Button type="button" :label="t('terms.draftDelete')" icon="pi pi-trash" size="small" severity="danger" variant="text" :disabled="!detail.draft || working !== null" :loading="working === 'delete'" @click="deleteDraft" />
                        <Button type="button" :label="t('terms.draftSave')" icon="pi pi-save" size="small" :disabled="!dirty || working !== null" :loading="working === 'save'" @click="saveDraft" />
                    </div>
                </AppGadget>

                <!-- 이력 -->
                <AppGadget :title="t('terms.historyTitle')">
                    <DataTable :value="detail.versions" data-key="version" :size="prefs.density === 'compact' ? 'small' : undefined" scrollable class="min-w-0">
                        <template #empty>
                            <EmptyState icon="pi pi-file" :title="t('terms.historyEmpty')" :description="t('terms.historyEmptyHint')" />
                        </template>
                        <Column :header="t('terms.colVersion')" style="width: 5.5rem">
                            <template #body="{ data }">
                                <span class="tabular-nums">{{ t('terms.versionNo', { version: data.version }) }}</span>
                            </template>
                        </Column>
                        <Column :header="t('terms.effectiveDate')" style="width: 8rem">
                            <template #body="{ data }">
                                <span class="tabular-nums">{{ time.serviceDate(data.effectiveDate) }}</span>
                            </template>
                        </Column>
                        <Column :header="t('terms.colPublishedAt')">
                            <template #body="{ data }">
                                <span class="text-[12px] tabular-nums text-muted-color">{{ time.full(data.publishedAt) }}</span>
                            </template>
                        </Column>
                        <Column :header="t('terms.colPublishedBy')">
                            <template #body="{ data }">{{ data.publishedBy ?? t('terms.unknownOperator') }}</template>
                        </Column>
                        <Column :header="t('terms.changeNote')">
                            <template #body="{ data }">
                                <span class="line-clamp-2 break-words text-xs text-muted-color">{{ data.changeNote || '—' }}</span>
                            </template>
                        </Column>
                        <Column :header="t('terms.colStatus')" style="width: 6.5rem">
                            <template #body="{ data }">
                                <Tag class="terms-tag" :value="t(`terms.status.${data.status}`)" :severity="UtilTerms.statusSeverity(data.status)" />
                            </template>
                        </Column>
                        <Column style="width: 5rem">
                            <template #body="{ data }">
                                <Button type="button" :label="t('terms.view')" size="small" severity="secondary" variant="text" @click="openVersion(data.version)" />
                            </template>
                        </Column>
                    </DataTable>
                </AppGadget>
            </template>
        </template>

        <TermsPublishDialog v-model:visible="publishOpen" :kind-label="kindLabel(activeKind)" :min-date="minDate" :busy="publishing" :error="publishError" :field-errors="publishFieldErrors" @submit="publish" />
        <TermsVersionDialog
            v-model:visible="versionOpen"
            :kind-label="kindLabel(activeKind)"
            :detail="versionDetail"
            :loading="versionLoading"
            :error="versionError"
            :can-copy="canManage"
            @copy="copyToDraft"
            @retry="loadVersion()"
        />
    </div>
</template>

<script setup lang="ts">
import type { ApiError } from '~/composables/common/useApiClient';
import type { TermsKind, TermsKindDetail, TermsOverview, TermsPublishRequest, TermsVersionDetail } from '~/composables/terms/api/useTermsApi.types';
import UtilRichText from '#layers/rich-editor/utils/util-rich-text';
import UtilTerms from '~/utils/util-terms';

const { t } = useI18n();
useSeoMeta({ title: () => t('terms.title') });

const api = useTermsApi();
const toast = useAppToast();
const toApiError = useApiErrorOf();
const { can } = useAdminPermission();
const { confirm } = useConfirmDialog();
const time = useDisplayTime();
// ⚠ 통째로 받아 `prefs.density` 를 쓰지 않으면 조밀 모드가 이 표에만 안 먹는다
const { prefs } = useLayoutPrefs();

/** 초안·게시·옵션에 필요한 권한 — 숨기는 것은 편의이고 막는 것은 서버다 */
const canManage = computed(() => can('terms.manage'));

const KINDS: readonly TermsKind[] = ['TERMS', 'PRIVACY'];
const linksId = useId();
const bodyLabelId = useId();
const bodyMessageId = useId();

function kindLabel(kind: TermsKind): string {
    return kind === 'TERMS' ? t('common.terms') : t('common.privacy');
}

/* ── 개요(옵션·종류별 상태) ── */

const overview = ref<TermsOverview | null>(null);
const loading = ref(false);
const loadError = ref<ApiError | null>(null);

async function loadOverview(): Promise<void> {
    loading.value = true;
    loadError.value = null;
    try {
        overview.value = await api.overview();
    } catch (e) {
        loadError.value = toApiError(e);
    } finally {
        loading.value = false;
    }
}

const activeKind = ref<TermsKind>('TERMS');
const activeOverview = computed(() => overview.value?.kinds.find((k) => k.kind === activeKind.value) ?? null);

/* ── 링크 옵션 ── */

const savingLinks = ref(false);

async function setShowLinks(value: boolean): Promise<void> {
    if (!overview.value || savingLinks.value) return;
    const before = overview.value.showLinks;
    overview.value = { ...overview.value, showLinks: value };
    savingLinks.value = true;
    try {
        const saved = await api.saveSettings({ showLinks: value });
        if (overview.value) overview.value = { ...overview.value, showLinks: saved.showLinks };
        toast.success(t(saved.showLinks ? 'terms.linksOn' : 'terms.linksOff'));
    } catch (e) {
        if (overview.value) overview.value = { ...overview.value, showLinks: before };
        toast.error(t('terms.linksFailed', { reason: toApiError(e).message }), 8000);
    } finally {
        savingLinks.value = false;
    }
}

/* ── 종류 상세(초안·이력) ── */

const detail = ref<TermsKindDetail | null>(null);
const detailLoading = ref(false);
const detailError = ref<ApiError | null>(null);
/** 늦게 온 앞 탭의 응답이 지금 탭을 덮지 않게 */
let detailSeq = 0;

/** 편집기 값과 「고쳤는가」의 기준 — 기준은 편집기가 정규화한 값으로 바꿔 둔다(`onEditorReady`) */
const editorHtml = ref('');
const baseline = ref('');
const editorKey = ref(0);
const dirty = computed(() => editorHtml.value !== baseline.value);

/** 편집기를 서버 본문으로 새로 만든다 */
function resetEditor(body: string): void {
    editorHtml.value = body;
    baseline.value = body;
    editorKey.value += 1;
}

function onEditorReady(html: string): void {
    if (editorHtml.value === baseline.value) {
        baseline.value = html;
        editorHtml.value = html;
    }
}

async function loadDetail(): Promise<void> {
    const seq = ++detailSeq;
    const kind = activeKind.value;
    detailLoading.value = true;
    detailError.value = null;
    try {
        const res = await api.detail(kind);
        if (seq !== detailSeq) return;
        detail.value = res;
        resetEditor(res.draft?.body ?? '');
    } catch (e) {
        if (seq !== detailSeq) return;
        detail.value = null;
        detailError.value = toApiError(e);
    } finally {
        if (seq === detailSeq) detailLoading.value = false;
    }
}

/** 탭을 바꾸려다 취소하면 탭 줄을 지금 종류로 다시 그린다(PrimeVue Tabs 가 누른 탭을 먼저 켜 둔다) */
const tabsKey = ref(0);

async function selectKind(kind: TermsKind): Promise<void> {
    if (kind === activeKind.value) return;
    if (dirty.value && !(await confirmDiscard())) {
        tabsKey.value += 1;
        return;
    }
    activeKind.value = kind;
    detail.value = null;
    clearDraftErrors();
    void loadDetail();
}

const draftMeta = computed(() => {
    const draft = detail.value?.draft;
    if (!draft) return t('terms.draftNone');
    return t('terms.draftSavedAt', { time: time.value.full(draft.updatedAt), name: draft.updatedBy ?? t('terms.unknownOperator') });
});

/* ── 초안 저장·삭제 ── */

/** 지금 하는 초안 작업 — 하나만(중복 클릭 방지) */
const working = ref<'save' | 'delete' | null>(null);
const bodyError = ref('');
const draftError = ref('');

function clearDraftErrors(): void {
    bodyError.value = '';
    draftError.value = '';
}

/** 개요의 초안 시각을 바꾼다(다시 받지 않는다) */
function patchOverviewDraft(kind: TermsKind, draftUpdatedAt: string | null): void {
    if (!overview.value) return;
    overview.value = { ...overview.value, kinds: overview.value.kinds.map((k) => (k.kind === kind ? { ...k, draftUpdatedAt } : k)) };
}

async function saveDraft(): Promise<void> {
    if (working.value || !detail.value) return;
    clearDraftErrors();
    if (!UtilRichText.hasContent(editorHtml.value)) {
        bodyError.value = t('terms.bodyRequired');
        return;
    }
    const kind = activeKind.value;
    working.value = 'save';
    try {
        const saved = await api.saveDraft(kind, { body: editorHtml.value });
        if (kind !== activeKind.value || !detail.value) return;
        detail.value = { ...detail.value, draft: saved };
        // 서버가 정화한 본문을 기준으로 — 편집기 내용과 다르면 편집기가 그 본문으로 바뀐다
        editorHtml.value = saved.body;
        baseline.value = saved.body;
        patchOverviewDraft(kind, saved.updatedAt);
        toast.success(t('terms.draftSaved'));
    } catch (e) {
        const error = toApiError(e);
        const field = error.field('body');
        if (field) bodyError.value = field;
        else draftError.value = error.message;
    } finally {
        working.value = null;
    }
}

async function deleteDraft(): Promise<void> {
    if (working.value || !detail.value?.draft) return;
    const ok = await confirm({ title: t('terms.draftDeleteTitle'), message: t('terms.draftDeleteMessage', { name: kindLabel(activeKind.value) }), confirmLabel: t('terms.draftDelete'), tone: 'danger' });
    if (!ok || working.value) return;
    const kind = activeKind.value;
    working.value = 'delete';
    clearDraftErrors();
    try {
        await api.deleteDraft(kind);
        if (kind === activeKind.value && detail.value) {
            detail.value = { ...detail.value, draft: null };
            resetEditor('');
        }
        patchOverviewDraft(kind, null);
        toast.success(t('terms.draftDeleted'));
    } catch (e) {
        draftError.value = toApiError(e).message;
    } finally {
        working.value = null;
    }
}

/* ── 게시 ── */

/** 저장한 초안이 있고 저장하지 않은 변경이 없을 때만 */
const canPublish = computed(() => canManage.value && Boolean(detail.value?.draft) && !dirty.value && working.value === null);

/** 오늘(표시 시간대의 달력 날짜) — 시행일의 하한. 판정은 서버(사이트 시간대)다 */
function today(): string {
    const parts = time.value.parts(new Date().toISOString());
    return parts ? UtilTerms.ymd(parts.y, parts.mo, parts.d) : new Date().toISOString().slice(0, 10);
}

const minDate = ref(today());
const publishOpen = ref(false);
const publishing = ref(false);
const publishError = ref('');
const publishFieldErrors = ref<Record<string, string>>({});

function openPublish(): void {
    if (!canPublish.value) return;
    minDate.value = UtilTerms.minEffectiveDate(today(), detail.value?.versions[0]?.effectiveDate);
    publishError.value = '';
    publishFieldErrors.value = {};
    publishOpen.value = true;
}

async function publish(body: TermsPublishRequest): Promise<void> {
    if (publishing.value) return;
    const kind = activeKind.value;
    publishing.value = true;
    publishError.value = '';
    publishFieldErrors.value = {};
    try {
        const created = await api.publish(kind, body);
        publishOpen.value = false;
        toast.success(t('terms.published', { name: kindLabel(kind), version: created.version, date: time.value.serviceDate(created.effectiveDate) }), 5000);
        await Promise.all([loadOverview(), loadDetail()]);
    } catch (e) {
        const error = toApiError(e);
        if (error.fields && Object.keys(error.fields).length > 0) publishFieldErrors.value = error.fields;
        else publishError.value = error.message;
    } finally {
        publishing.value = false;
    }
}

/* ── 게시본 보기 · 이 본문으로 초안 만들기 ── */

const versionOpen = ref(false);
const versionTarget = ref<number | null>(null);
const versionDetail = ref<TermsVersionDetail | null>(null);
const versionLoading = ref(false);
const versionError = ref('');
let versionSeq = 0;

async function loadVersion(): Promise<void> {
    if (versionTarget.value === null) return;
    const seq = ++versionSeq;
    versionLoading.value = true;
    versionError.value = '';
    versionDetail.value = null;
    try {
        const res = await api.version(activeKind.value, versionTarget.value);
        if (seq === versionSeq) versionDetail.value = res;
    } catch (e) {
        if (seq === versionSeq) versionError.value = toApiError(e).message;
    } finally {
        if (seq === versionSeq) versionLoading.value = false;
    }
}

function openVersion(version: number): void {
    versionTarget.value = version;
    versionOpen.value = true;
    void loadVersion();
}

/** 게시본 본문을 초안 편집기에 넣는다 — 저장은 [초안 저장] */
async function copyToDraft(source: TermsVersionDetail): Promise<void> {
    if (!canManage.value || working.value) return;
    const hasDraft = Boolean(detail.value?.draft) || UtilRichText.hasContent(editorHtml.value);
    if (hasDraft) {
        const ok = await confirm({ title: t('terms.copyTitle'), message: t('terms.copyMessage', { version: source.version }), confirmLabel: t('terms.copyConfirm'), tone: 'danger' });
        if (!ok) return;
    }
    versionOpen.value = false;
    clearDraftErrors();
    editorHtml.value = source.body;
    toast.success(t('terms.copied', { version: source.version }), 5000);
}

/* ── 떠날 때 ── */

function confirmDiscard(): Promise<boolean> {
    return confirm({ title: t('terms.leaveTitle'), message: t('terms.leaveMessage'), confirmLabel: t('terms.leaveConfirm'), tone: 'danger' });
}

onBeforeRouteLeave(async () => {
    if (!dirty.value) return true;
    return confirmDiscard();
});

onMounted(() => {
    void loadOverview();
    void loadDetail();
});
</script>

<style scoped>
/* 뱃지 글자 상하 중앙(`components.md` 2-4) */
.terms-tag {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    line-height: 1;
}
.terms-tag :deep(.p-tag-label) {
    position: relative;
    top: -0.09em;
}
</style>
