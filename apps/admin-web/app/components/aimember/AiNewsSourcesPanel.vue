<template>
    <!--
        AI 회원 설정 「뉴스 출처」(V228 — docs/features/ai-news-reporter-contract.md 「관리자 화면」). AI 기자가 뉴스 게시판 글의 소재로 쓰는 RSS·유튜브 채널·구글 뉴스 검색어.
        한 건씩 바로 반영(더하기·고치기·켜기·지우기 — `…/news-sources` API, `ai-member.manage`). 「지금 수집」은 배치 `ai-news-collect` 와 같은 서비스를 곧바로 부른다(돌고 있으면 건너뜀).
        표: 켜짐 · 종류 · 이름 · 주소(채널 ID·검색어) · 마지막 수집(시각 · 읽은 수) · 오류(마지막 실패 사유 — 성공하면 지워진다) · 고치기·지우기.
        수집 주기는 배치 관리 화면에서 바꾼다(기본 30분마다). 본문 전체·사진은 받지 않는다는 안내를 위에 둔다.
        표 위 「관심 키워드」(V230 — `PUT …/news-sources/keywords`): RSS·유튜브 항목은 제목·요약에 낱말이 하나라도 들어간 것만 모은다(검색어 출처는 거르지 않음, 비우면 모두).
    -->
    <AppGadget :title="t('aiMember.newsSourcesTitle')">
        <template #actions>
            <Button
                v-if="canManage && list"
                type="button"
                :label="t('aiMember.newsCollectNow')"
                icon="pi pi-refresh"
                size="small"
                severity="secondary"
                variant="outlined"
                :loading="collecting"
                :disabled="Boolean(busyId) || saving"
                @click="collect"
            />
        </template>
        <div class="flex min-w-0 flex-col gap-3">
            <p class="m-0 text-xs leading-relaxed text-muted-color">{{ t('aiMember.newsSourcesHelp') }}</p>

            <div v-if="loading && !list" class="flex flex-col gap-2" role="status" :aria-label="t('common.loading')">
                <Skeleton v-for="n in 3" :key="n" height="2.25rem" />
            </div>
            <Message v-else-if="loadError && !list" severity="error" size="small" :closable="false">
                <span class="inline-flex flex-wrap items-center gap-2">
                    {{ loadError }}
                    <Button type="button" :label="t('common.retry')" size="small" severity="secondary" variant="text" :loading="loading" @click="reload" />
                </span>
            </Message>

            <template v-else-if="list">
                <p class="m-0 flex flex-wrap items-center gap-x-3 gap-y-1 text-xs tabular-nums text-muted-color">
                    <span>{{ t('aiMember.newsSourceUnused', { n: list.unusedItems }) }}</span>
                    <span>{{ t('aiMember.newsBatchHint', { name: t('aiMember.newsBatchName') }) }}</span>
                </p>

                <!-- 관심 키워드(V230) — RSS·유튜브 항목은 제목·요약에 하나라도 들어간 것만 모은다. 쉼표로 나눠 적고 따로 저장한다(출처 줄과 따로 바로 반영) -->
                <form class="flex flex-col gap-2 rounded-lg border border-(--line) px-3 py-2.5" novalidate @submit.prevent="saveKeywords">
                    <LabeledField :label="t('aiMember.newsKeywordsLabel')" :help="t('aiMember.newsKeywordsHelp')" :error="keywordsError">
                        <template #default="{ id, describedBy, invalid }">
                            <Textarea
                                :id="id"
                                v-model="keywordsText"
                                rows="2"
                                auto-resize
                                fluid
                                spellcheck="false"
                                :placeholder="t('aiMember.newsKeywordsPlaceholder')"
                                :invalid="invalid"
                                :aria-describedby="describedBy"
                                :disabled="!canManage || savingKeywords"
                            />
                        </template>
                    </LabeledField>
                    <div class="flex flex-wrap items-center gap-2">
                        <small class="text-xs tabular-nums" :class="keywordsDraft.length > list.limits.keywordsMax ? 'text-red-700 dark:text-red-400' : 'text-muted-color'">
                            {{ t('aiMember.newsKeywordsCount', { n: keywordsDraft.length, max: list.limits.keywordsMax, len: list.limits.keywordMax }) }}
                        </small>
                        <Button
                            v-if="canManage"
                            type="submit"
                            icon="pi pi-check"
                            :label="t('aiMember.newsKeywordsSave')"
                            size="small"
                            class="ml-auto"
                            :loading="savingKeywords"
                            :disabled="!keywordsDirty || Boolean(busyId) || saving"
                        />
                    </div>
                </form>

                <DataTable :value="list.items" data-key="id" size="small" scrollable class="min-w-0">
                    <template #empty>
                        <p class="m-0 py-2 text-sm text-muted-color">{{ t('aiMember.newsSourceEmpty') }}</p>
                    </template>
                    <Column :header="t('aiMember.newsSourceCol.enabled')" style="width: 4.5rem">
                        <template #body="{ data }">
                            <ToggleSwitch
                                :model-value="data.enabled"
                                :disabled="!canManage || Boolean(busyId) || saving"
                                :aria-label="t('aiMember.newsSourceEnabledLabel', { name: data.name })"
                                @update:model-value="(v: boolean) => setEnabled(data, v)"
                            />
                        </template>
                    </Column>
                    <Column :header="t('aiMember.newsSourceCol.kind')" style="width: 8rem">
                        <template #body="{ data }">
                            <Tag :value="t(`aiMember.newsSourceKind.${data.kind}`)" :severity="data.kind === 'RSS' ? 'info' : data.kind === 'YOUTUBE' ? 'danger' : 'secondary'" class="ai-news-tag text-[11px]!" />
                        </template>
                    </Column>
                    <Column :header="t('aiMember.newsSourceCol.name')" style="min-width: 8rem">
                        <template #body="{ data }">
                            <span class="text-[13px] font-medium">{{ data.name }}</span>
                        </template>
                    </Column>
                    <Column :header="t('aiMember.newsSourceCol.address')" style="min-width: 14rem">
                        <template #body="{ data }">
                            <code class="rounded bg-surface-100 px-1.5 py-0.5 text-[12px] [overflow-wrap:anywhere] dark:bg-surface-800">{{ data.address }}</code>
                        </template>
                    </Column>
                    <Column :header="t('aiMember.newsSourceCol.lastFetched')" style="min-width: 9rem">
                        <template #body="{ data }">
                            <span class="text-[12px] tabular-nums" :class="data.lastFetchedAt ? '' : 'text-muted-color'">
                                {{ data.lastFetchedAt ? t('aiMember.newsSourceFetched', { at: time.full(data.lastFetchedAt), n: data.lastItemCount ?? 0 }) : t('aiMember.newsSourceNever') }}
                            </span>
                        </template>
                    </Column>
                    <Column :header="t('aiMember.newsSourceCol.error')" style="min-width: 9rem">
                        <template #body="{ data }">
                            <span v-if="data.lastError" class="flex flex-col gap-0.5 text-[12px] text-red-700 [overflow-wrap:anywhere] dark:text-red-400">
                                <span>{{ data.lastError }}</span>
                                <small v-if="data.lastErrorAt" class="tabular-nums text-muted-color">{{ time.full(data.lastErrorAt) }}</small>
                            </span>
                            <span v-else class="text-[12px] text-muted-color">—</span>
                        </template>
                    </Column>
                    <Column v-if="canManage" :header="t('aiMember.newsSourceCol.actions')" style="width: 6rem">
                        <template #body="{ data }">
                            <span class="inline-flex">
                                <Button
                                    type="button"
                                    icon="pi pi-pencil"
                                    :aria-label="t('aiMember.newsSourceEditLabel', { name: data.name })"
                                    size="small"
                                    severity="secondary"
                                    variant="text"
                                    :disabled="Boolean(busyId) || saving"
                                    @click="startEdit(data)"
                                />
                                <Button
                                    type="button"
                                    icon="pi pi-trash"
                                    :aria-label="t('aiMember.newsSourceDeleteLabel', { name: data.name })"
                                    size="small"
                                    severity="danger"
                                    variant="text"
                                    :loading="busyId === data.id"
                                    :disabled="Boolean(busyId) || saving"
                                    @click="remove(data)"
                                />
                            </span>
                        </template>
                    </Column>
                </DataTable>

                <!-- 더하기 · 고치기(고치는 줄을 고르면 이 칸이 고치기로 바뀐다) -->
                <form v-if="canManage" class="flex flex-col gap-2 rounded-lg border border-(--line) px-3 py-2.5" novalidate @submit.prevent="submit">
                    <p class="m-0 text-sm font-medium">{{ editingId ? t('aiMember.newsSourceEditTitle', { name: editingName }) : t('aiMember.newsSourceAdd') }}</p>
                    <div class="flex flex-wrap items-start gap-2">
                        <LabeledField :label="t('aiMember.newsSourceCol.kind')" :error="fieldErrors.kind" class="w-44">
                            <template #default="{ id, describedBy, invalid }">
                                <Select v-model="form.kind" :input-id="id" :options="kindOptions" option-label="label" option-value="value" fluid :invalid="invalid" :aria-describedby="describedBy" :disabled="saving" />
                            </template>
                        </LabeledField>
                        <LabeledField :label="t('aiMember.newsSourceCol.name')" :error="fieldErrors.name" class="min-w-0 flex-1 basis-40">
                            <template #default="{ id, describedBy, invalid }">
                                <InputText :id="id" v-model="form.name" fluid :maxlength="list.limits.nameMax" :placeholder="t('aiMember.newsSourceNamePlaceholder')" :invalid="invalid" :aria-describedby="describedBy" :disabled="saving" />
                            </template>
                        </LabeledField>
                    </div>
                    <LabeledField :label="t(`aiMember.newsSourceAddressLabel.${form.kind}`)" :help="t(`aiMember.newsSourceKindHelp.${form.kind}`)" :error="fieldErrors.address">
                        <template #default="{ id, describedBy, invalid }">
                            <InputText
                                :id="id"
                                v-model="form.address"
                                fluid
                                :maxlength="list.limits.addressMax"
                                autocomplete="off"
                                spellcheck="false"
                                :placeholder="t(`aiMember.newsSourceAddressPlaceholder.${form.kind}`)"
                                :invalid="invalid"
                                :aria-describedby="describedBy"
                                :disabled="saving"
                            />
                        </template>
                    </LabeledField>
                    <div class="flex flex-wrap items-center gap-2">
                        <label class="flex items-center gap-2 text-sm">
                            <Checkbox v-model="form.enabled" binary :disabled="saving" />
                            {{ t('aiMember.newsSourceEnabled') }}
                        </label>
                        <span class="ml-auto inline-flex gap-2">
                            <Button v-if="editingId" type="button" :label="t('common.cancel')" size="small" severity="secondary" variant="text" :disabled="saving" @click="cancelEdit" />
                            <Button
                                type="submit"
                                :icon="editingId ? 'pi pi-check' : 'pi pi-plus'"
                                :label="editingId ? t('aiMember.newsSourceSave') : t('aiMember.newsSourceAddSubmit')"
                                size="small"
                                :loading="saving"
                                :disabled="!form.name.trim() || !form.address.trim() || Boolean(busyId)"
                            />
                        </span>
                    </div>
                    <p v-if="formError" class="m-0 text-xs text-red-700 dark:text-red-400" role="alert">{{ formError }}</p>
                </form>
                <p v-else class="m-0 flex items-center gap-1.5 text-xs text-muted-color"><i class="pi pi-lock" aria-hidden="true" />{{ t('aiMember.newsSourcesReadOnly') }}</p>
            </template>
        </div>
    </AppGadget>
</template>

<script setup lang="ts">
import { AI_NEWS_SOURCE_KINDS, type AiNewsSource, type AiNewsSourceKind, type AiNewsSourceList, type AiNewsSourceSaveRequest } from '~/composables/aimember/api/useAiMemberAdminApi.types';
import UtilAiMemberMessages from '~/utils/util-ai-member-messages';

const props = defineProps<{
    /** 바꾸기 — 서버 `ai-member.manage` */
    canManage: boolean;
}>();

const { t } = useI18n();
const api = useAiMemberAdminApi();
const toast = useAppToast();
const errorOf = useApiErrorOf();
const time = useDisplayTime();
const { confirm } = useConfirmDialog();

const list = ref<AiNewsSourceList | null>(null);
const loading = ref(false);
const loadError = ref('');
const busyId = ref<string | null>(null);
const saving = ref(false);
const collecting = ref(false);
const editingId = ref<string | null>(null);
const editingName = ref('');
const form = ref<AiNewsSourceSaveRequest>(blankForm());
const fieldErrors = ref<Record<string, string>>({});
const formError = ref('');

/** 관심 키워드 입력(쉼표로 나눔) — 불러오거나 저장하면 서버 값으로 다시 맞춘다 */
const keywordsText = ref('');
const keywordsError = ref('');
const savingKeywords = ref(false);

/** 입력을 서버와 같은 규칙으로 정리(앞뒤 공백 · 빈 낱말 · 중복(대소문자 무시)) — 개수 표시·바뀜 판단용, 최종 검사는 서버가 한다 */
const keywordsDraft = computed(() => {
    const seen = new Set<string>();
    const out: string[] = [];
    for (const part of keywordsText.value.split(/[,\n]/)) {
        const k = part.replace(/\s+/g, ' ').trim();
        if (k && !seen.has(k.toLowerCase())) {
            seen.add(k.toLowerCase());
            out.push(k);
        }
    }
    return out;
});
const keywordsDirty = computed(() => (list.value?.keywords ?? []).join(',') !== keywordsDraft.value.join(','));

// 서버 값이 실제로 바뀔 때만 입력을 다시 맞춘다(출처 저장 등으로 목록만 새로 와도 고치던 입력은 그대로)
watch(
    () => list.value?.keywords?.join(','),
    () => {
        keywordsText.value = (list.value?.keywords ?? []).join(', ');
        keywordsError.value = '';
    },
);

const kindOptions = computed(() => AI_NEWS_SOURCE_KINDS.map((value) => ({ value, label: t(`aiMember.newsSourceKind.${value}`) })));
const canManage = computed(() => props.canManage);

function blankForm(kind: AiNewsSourceKind = 'RSS'): AiNewsSourceSaveRequest {
    return { kind, name: '', address: '', enabled: true };
}

async function reload(): Promise<void> {
    loading.value = true;
    loadError.value = '';
    try {
        list.value = await api.newsSources();
    } catch (e) {
        loadError.value = UtilAiMemberMessages.error(t, errorOf(e));
    } finally {
        loading.value = false;
    }
}

onMounted(() => void reload());

function startEdit(row: AiNewsSource): void {
    editingId.value = row.id;
    editingName.value = row.name;
    form.value = { kind: row.kind, name: row.name, address: row.address, enabled: row.enabled };
    fieldErrors.value = {};
    formError.value = '';
}

function cancelEdit(): void {
    editingId.value = null;
    editingName.value = '';
    form.value = blankForm();
    fieldErrors.value = {};
    formError.value = '';
}

/** 서버 칸 오류는 칸 아래에, 칸이 없는 오류(개수 등)는 아래 한 줄로 */
function showError(e: unknown): void {
    const error = errorOf(e);
    if (error.fields && Object.keys(error.fields).length) {
        fieldErrors.value = { ...error.fields };
        formError.value = Object.entries(error.fields)
            .filter(([key]) => !['kind', 'name', 'address'].includes(key))
            .map(([, message]) => message)
            .join(' ');
    } else {
        formError.value = UtilAiMemberMessages.error(t, error);
    }
}

async function submit(): Promise<void> {
    const f = form.value;
    if (saving.value || !f.name.trim() || !f.address.trim()) return;
    saving.value = true;
    fieldErrors.value = {};
    formError.value = '';
    // 서버에 넘기는 본문은 평범한 객체로(_frontend-core 2-3)
    const body: AiNewsSourceSaveRequest = { kind: f.kind, name: f.name.trim(), address: f.address.trim(), enabled: f.enabled };
    try {
        list.value = editingId.value ? await api.saveNewsSource(editingId.value, body) : await api.createNewsSource(body);
        toast.success(t('aiMember.newsSourceSaved'));
        cancelEdit();
    } catch (e) {
        showError(e);
    } finally {
        saving.value = false;
    }
}

async function setEnabled(row: AiNewsSource, enabled: boolean): Promise<void> {
    if (busyId.value || row.enabled === enabled) return;
    busyId.value = row.id;
    try {
        list.value = await api.saveNewsSource(row.id, { kind: row.kind, name: row.name, address: row.address, enabled });
        toast.success(t('aiMember.newsSourceSaved'));
    } catch (e) {
        toast.error(UtilAiMemberMessages.error(t, errorOf(e)), 8000);
    } finally {
        busyId.value = null;
    }
}

async function remove(row: AiNewsSource): Promise<void> {
    if (busyId.value) return;
    const ok = await confirm({
        title: t('aiMember.newsSourceDeleteTitle', { name: row.name }),
        message: t('aiMember.newsSourceDeleteMessage'),
        confirmLabel: t('aiMember.newsSourceDelete'),
        tone: 'danger',
    });
    if (!ok) return;
    busyId.value = row.id;
    try {
        list.value = await api.deleteNewsSource(row.id);
        if (editingId.value === row.id) cancelEdit();
        toast.success(t('aiMember.newsSourceDeleted', { name: row.name }));
    } catch (e) {
        toast.error(UtilAiMemberMessages.error(t, errorOf(e)), 8000);
        if (errorOf(e).code === 'AI_NEWS_SOURCE_NOT_FOUND') void reload();
    } finally {
        busyId.value = null;
    }
}

async function saveKeywords(): Promise<void> {
    if (savingKeywords.value || !keywordsDirty.value) return;
    savingKeywords.value = true;
    keywordsError.value = '';
    try {
        list.value = await api.saveNewsKeywords({ keywords: [...keywordsDraft.value] });
        toast.success(t('aiMember.newsKeywordsSaved'));
    } catch (e) {
        const error = errorOf(e);
        keywordsError.value = error.fields?.keywords ?? UtilAiMemberMessages.error(t, error);
    } finally {
        savingKeywords.value = false;
    }
}

async function collect(): Promise<void> {
    if (collecting.value) return;
    collecting.value = true;
    try {
        const r = await api.collectNews();
        list.value = r.list;
        if (r.busy) toast.show(t('aiMember.newsCollectBusy'));
        else if (r.failed > 0) toast.show(t('aiMember.newsCollectedWithFailures', { sources: r.sources, added: r.added, failed: r.failed }), { life: 8000 });
        else toast.success(t('aiMember.newsCollected', { sources: r.sources, added: r.added }));
    } catch (e) {
        toast.error(UtilAiMemberMessages.error(t, errorOf(e)), 8000);
    } finally {
        collecting.value = false;
    }
}
</script>

<style scoped>
/* 한글 글꼴 보정 — 알약 안에서 글자가 아래로 붙어 보이지 않게(components.md 2-4, 다른 AI 회원 화면과 같다) */
.ai-news-tag :deep(.p-tag-label) {
    position: relative;
    top: -0.5px;
}
</style>
