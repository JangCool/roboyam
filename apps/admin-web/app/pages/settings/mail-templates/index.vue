<template>
    <!--
        메일 템플릿(목록, `/settings/mail-templates`, 설정 > 메일) — 모든 메일 종류의 템플릿 표. 행을 누르면 편집 화면(`/settings/mail-templates/[id]`)으로 간다. 계약 `docs/features/message-template-contract.md` 「개정」 3.
        표: 이름(쓰는 중 딱지) · 종류(보안 메일 자물쇠) · 언어 · 레이아웃 · 기본/사용자 · 마지막 수정 · 관리(복사 · 삭제).
        위쪽 거르기: 종류 · 언어 · 기본/사용자 · 레이아웃 · 검색(이름·종류). 종류·레이아웃 거르기는 주소 `?kind=`·`?layout=` 에 남는다(메일 종류 상세 · 레이아웃 편집의 「템플릿 보기」가 이 주소로 들어온다).
        새로 만들기 → `/settings/mail-templates/new`(종류를 거른 중이면 `?kind=`), 복사 → `new?from=<id>`(바로 만들지 않고 편집 화면에서 [저장] 때 만든다). 기본 템플릿은 지울 수 없다.
        삭제는 확인 후 바로 — 쓰는 중이면 단추를 잠그고 까닭을 보이며, 서버도 409 `TEMPLATE_IN_USE` 로 막는다(토스트 8초). 종류당 사용자 템플릿 20개(가정)가 차면 복사를 잠근다.
        권한: 화면 `template.read`(메뉴가 거른다) · 만들기·복사·삭제 `template.manage` — 없으면 단추가 숨는다.
        ⚠ MOCK: API 는 메모리 목업이다(`useMailTemplateApi`).
    -->
    <div class="flex min-w-0 flex-col gap-4">
        <PageTitle :title="t('mailTemplate.templatesTitle')" :subtitle="t('mailTemplate.templatesSubtitle')">
            <template v-if="canManage && !denied" #actions>
                <Button :label="t('mailTemplate.newTemplate')" icon="pi pi-plus" size="small" @click="router.push({ path: '/settings/mail-templates/new', query: kindFilter ? { kind: kindFilter } : {} })" />
            </template>
        </PageTitle>

        <Message v-if="denied" severity="info" :closable="false" size="small">{{ t('mailTemplate.denied') }}</Message>

        <template v-else>
            <AppGadget v-if="loading && !templates" :title="t('mailTemplate.templatesTitle')">
                <div class="flex flex-col gap-2" role="status" :aria-label="t('common.loading')">
                    <Skeleton v-for="n in 8" :key="n" height="2.75rem" />
                </div>
            </AppGadget>

            <AppGadget v-else-if="!templates" :title="t('mailTemplate.templatesTitle')">
                <ErrorState :title="t('mailTemplate.templatesLoadFailed')" :description="loadError" retry :retrying="loading" @retry="load" />
            </AppGadget>

            <AppGadget v-else-if="!templates.length" :title="t('mailTemplate.templatesTitle')">
                <EmptyState icon="pi pi-envelope" :title="t('mailTemplate.kindsEmptyTitle')" :description="t('mailTemplate.kindsEmptyBody')" />
            </AppGadget>

            <template v-else>
                <!-- 조건 -->
                <div class="flex flex-wrap items-center gap-x-4 gap-y-2">
                    <IconField class="w-full sm:w-56">
                        <InputIcon class="pi pi-search" />
                        <InputText v-model="keyword" size="small" fluid :placeholder="t('mailTemplate.searchPlaceholder')" :aria-label="t('mailTemplate.searchPlaceholder')" autocomplete="off" />
                    </IconField>
                    <Select v-model="kindFilter" :options="kindOptions" option-label="label" option-value="value" :placeholder="t('mailTemplate.filterKindAll')" show-clear size="small" class="w-full sm:w-56" :aria-label="t('mailTemplate.filterKind')" />
                    <SelectButton v-model="localeFilter" :options="localeOptions" option-label="label" option-value="value" :allow-empty="false" size="small" :aria-label="t('mailTemplate.filterLocale')" />
                    <SelectButton v-model="originFilter" :options="originOptions" option-label="label" option-value="value" :allow-empty="false" size="small" :aria-label="t('mailTemplate.filterOrigin')" />
                    <Select v-model="layoutFilter" :options="layoutOptions" option-label="label" option-value="value" :placeholder="t('mailTemplate.filterLayoutAll')" show-clear size="small" class="w-full sm:w-44" :aria-label="t('mailTemplate.filterLayout')" />
                    <p class="ml-auto text-[13px] text-muted-color" aria-live="polite">{{ t('mailTemplate.listCount', { n: filtered.length, total: templates.length }) }}</p>
                </div>

                <div class="min-w-0 overflow-hidden rounded-xl border border-(--line) bg-(--p-content-background)">
                    <DataTable :value="filtered" data-key="id" row-hover scrollable :size="prefs.density === 'compact' ? 'small' : undefined" :row-class="() => 'cursor-pointer'" @row-click="onRowClick">
                        <template #empty>
                            <EmptyState icon="pi pi-filter-slash" :title="t('common.emptyFiltered')">
                                <Button type="button" :label="t('mailTemplate.clearFilter')" size="small" severity="secondary" variant="outlined" @click="clearFilter" />
                            </EmptyState>
                        </template>

                        <Column :header="t('mailTemplate.colName')" style="min-width: 14rem">
                            <template #body="{ data }">
                                <div class="flex min-w-0 flex-col gap-1">
                                    <NuxtLink :to="editPath(data)" class="text-[13px] font-medium hover:underline">{{ data.name }}</NuxtLink>
                                    <div v-if="usageOf(data).length" class="flex flex-wrap items-center gap-1">
                                        <Tag v-for="label in usageOf(data)" :key="label" :value="label" severity="info" class="mail-template-tag text-[10px]!" />
                                    </div>
                                </div>
                            </template>
                        </Column>
                        <Column :header="t('mailTemplate.colKind')" style="min-width: 12rem">
                            <template #body="{ data }">
                                <span class="inline-flex min-w-0 items-center gap-1.5 text-[12px]">
                                    <span class="truncate">{{ data.kindName }}</span>
                                    <span v-if="data.security" v-tooltip.top="t('mailTemplate.securityTip')" class="inline-flex shrink-0" role="img" :aria-label="t('mailTemplate.securityTag')">
                                        <i class="pi pi-lock text-[11px] text-amber-600 dark:text-amber-400" aria-hidden="true" />
                                    </span>
                                </span>
                            </template>
                        </Column>
                        <Column :header="t('mailTemplate.colLocale')" style="min-width: 6rem">
                            <template #body="{ data }">
                                <span class="text-[12px]">{{ UtilMailTemplateMessages.localeLabel(t, data.locale) }}</span>
                            </template>
                        </Column>
                        <Column :header="t('mailTemplate.colLayout')" style="min-width: 9rem">
                            <template #body="{ data }">
                                <span class="text-[12px]">{{ data.layoutName }}</span>
                            </template>
                        </Column>
                        <Column :header="t('mailTemplate.colOrigin')" style="min-width: 6.5rem">
                            <template #body="{ data }">
                                <Tag v-if="data.builtin" :value="t('mailTemplate.builtinTag')" icon="pi pi-lock" severity="secondary" class="mail-template-tag text-[10px]!" />
                                <span v-else class="text-[12px]">{{ t('mailTemplate.userTag') }}</span>
                            </template>
                        </Column>
                        <Column :header="t('mailTemplate.colUpdated')" style="min-width: 10rem">
                            <template #body="{ data }">
                                <span v-if="data.updatedAt" class="text-[12px]">
                                    <span class="tabular-nums">{{ time.listLabel(data.updatedAt) }}</span>
                                    <span v-if="data.updatedByName" class="block text-[11px] text-muted-color">{{ data.updatedByName }}</span>
                                </span>
                                <span v-else class="text-[12px] text-muted-color">{{ t('mailTemplate.builtinUpdated') }}</span>
                            </template>
                        </Column>
                        <Column v-if="canManage" :header="t('mailTemplate.colActions')" style="min-width: 8rem">
                            <template #body="{ data }">
                                <div class="flex items-center gap-0.5">
                                    <span v-tooltip.top="copyBlockedReason(data) || t('mailTemplate.copy')" class="inline-flex">
                                        <Button type="button" icon="pi pi-copy" size="small" severity="secondary" variant="text" rounded :disabled="Boolean(copyBlockedReason(data))" :aria-label="t('mailTemplate.copyLabel', { name: data.name })" :aria-description="copyBlockedReason(data) || undefined" @click="copy(data)" />
                                    </span>
                                    <span v-if="!data.builtin" v-tooltip.top="deleteBlockedReason(data) || t('common.delete')" class="inline-flex">
                                        <Button type="button" icon="pi pi-trash" size="small" severity="danger" variant="text" rounded :disabled="Boolean(deleteBlockedReason(data)) || deletingId === data.id" :loading="deletingId === data.id" :aria-label="t('mailTemplate.deleteLabel', { name: data.name })" :aria-description="deleteBlockedReason(data) || undefined" @click="confirmDelete(data)" />
                                    </span>
                                </div>
                            </template>
                        </Column>
                    </DataTable>
                </div>
                <p class="m-0 text-xs text-muted-color">{{ t('mailTemplate.listHint') }}</p>
            </template>
        </template>
    </div>
</template>

<script setup lang="ts">
import type { DataTableRowClickEvent } from 'primevue/datatable';
import { mockTemplateCan } from '~/composables/template/api/mock/mail-template-mock';
import type { MailLocale, MailTemplateListItemView } from '~/composables/template/api/useMailTemplateApi.types';
import { MAIL_TEMPLATE_LIMIT } from '~/composables/template/api/useMailTemplateApi.types';
import type { MailTemplateFilter } from '~/utils/util-mail-template';
import UtilMailTemplate from '~/utils/util-mail-template';
import UtilMailTemplateMessages from '~/utils/util-mail-template-messages';

const { t } = useI18n();
useSeoMeta({ title: () => t('mailTemplate.templatesTitle') });

const api = useMailTemplateApi();
const toast = useAppToast();
const toApiError = useApiErrorOf();
const time = useDisplayTime();
const route = useRoute();
const router = useRouter();
const { confirm } = useConfirmDialog();
// ⚠ 통째로 받아 `prefs.density` 를 쓰지 않으면 조밀 모드가 이 표에만 안 먹는다
const { prefs } = useLayoutPrefs();
// MOCK: 목업 스위치(`?mock=template-readonly|template-denied`)로 권한을 흉내 낸다 — 서버가 붙으면 아래 REAL 줄로 바꾼다
const can = mockTemplateCan(useAdminPermission().can);
// REAL: const { can } = useAdminPermission();

const canManage = computed(() => can('template.manage'));

const templates = ref<MailTemplateListItemView[] | null>(null);
const loading = ref(false);
const loadError = ref('');
const denied = ref(false);

/* ── 거르기 ── */

const queryText = (value: unknown) => (typeof value === 'string' && value ? value : null);
const keyword = ref('');
const kindFilter = ref<string | null>(queryText(route.query.kind));
const layoutFilter = ref<string | null>(queryText(route.query.layout));
const localeFilter = ref<MailLocale | 'ALL'>('ALL');
const originFilter = ref<MailTemplateFilter['origin']>('ALL');

// 종류·레이아웃 거르기는 주소에 남긴다(뒤로 가기·링크로 다시 들어와도 같은 목록)
watch([kindFilter, layoutFilter], ([kind, layout]) => {
    const query = { ...route.query };
    if (kind) query.kind = kind;
    else delete query.kind;
    if (layout) query.layout = layout;
    else delete query.layout;
    void router.replace({ query });
});

/** 종류 고르기 — 목록에 나온 종류(받은 차례) */
const kindOptions = computed(() => {
    const seen = new Map<string, string>();
    for (const item of templates.value ?? []) if (!seen.has(item.kind)) seen.set(item.kind, item.kindName);
    return [...seen].map(([value, label]) => ({ value, label }));
});
/** 레이아웃 고르기 — 목록에 나온 레이아웃(쓰는 템플릿이 없는 레이아웃은 나오지 않는다) */
const layoutOptions = computed(() => {
    const seen = new Map<string, string>();
    for (const item of templates.value ?? []) if (!seen.has(item.layoutId)) seen.set(item.layoutId, item.layoutName);
    // 주소로 들어온 레이아웃이 아직 아무도 안 쓰는 것이면 고르기에서 보이도록 남긴다
    if (layoutFilter.value && !seen.has(layoutFilter.value)) seen.set(layoutFilter.value, t('mailTemplate.filterLayoutUnknown'));
    return [...seen].map(([value, label]) => ({ value, label }));
});
const localeOptions = computed(() => [
    { value: 'ALL', label: t('common.all') },
    { value: 'ko', label: UtilMailTemplateMessages.localeLabel(t, 'ko') },
    { value: 'en', label: UtilMailTemplateMessages.localeLabel(t, 'en') },
]);
const originOptions = computed(() => [
    { value: 'ALL', label: t('common.all') },
    { value: 'BUILTIN', label: t('mailTemplate.builtinTag') },
    { value: 'USER', label: t('mailTemplate.userTag') },
]);

const filtered = computed(() =>
    UtilMailTemplate.filterTemplates(templates.value ?? [], {
        kind: kindFilter.value,
        locale: localeFilter.value === 'ALL' ? null : localeFilter.value,
        origin: originFilter.value,
        layoutId: layoutFilter.value,
        keyword: keyword.value,
    }),
);

function clearFilter(): void {
    keyword.value = '';
    kindFilter.value = null;
    layoutFilter.value = null;
    localeFilter.value = 'ALL';
    originFilter.value = 'ALL';
}

/* ── 읽기 ── */

async function load(): Promise<void> {
    loading.value = true;
    loadError.value = '';
    try {
        templates.value = await api.templates();
    } catch (e) {
        const error = toApiError(e);
        if (error.status === 403) denied.value = true;
        else loadError.value = error.message;
    } finally {
        loading.value = false;
    }
}

const editPath = (item: MailTemplateListItemView) => `/settings/mail-templates/${encodeURIComponent(item.id)}`;

/** 행을 누르면 편집 화면으로 — 줄 안의 단추·링크를 누른 것은 그 동작만 한다 */
function onRowClick(event: DataTableRowClickEvent): void {
    const target = event.originalEvent.target as HTMLElement | null;
    if (target?.closest('button, a, input')) return;
    void router.push(editPath(event.data as MailTemplateListItemView));
}

/** 저장된 상태에서 이 템플릿을 쓰는 곳(「한국어 기본」 · 「규칙 2개」) */
function usageOf(item: MailTemplateListItemView): string[] {
    const out = item.defaultFor.map((locale) => t('mailTemplate.usedAsDefault', { locale: UtilMailTemplateMessages.localeLabel(t, locale) }));
    if (item.ruleCount > 0) out.push(t('mailTemplate.usedByRules', { count: item.ruleCount }));
    return out;
}

/* ── 복사 · 삭제 ── */

function copyBlockedReason(item: MailTemplateListItemView): string {
    const count = (templates.value ?? []).filter((other) => other.kind === item.kind && !other.builtin).length;
    return count >= MAIL_TEMPLATE_LIMIT ? t('mailTemplate.limitReached', { max: MAIL_TEMPLATE_LIMIT }) : '';
}

function copy(item: MailTemplateListItemView): void {
    if (copyBlockedReason(item)) return;
    void router.push({ path: '/settings/mail-templates/new', query: { from: item.id } });
}

function deleteBlockedReason(item: MailTemplateListItemView): string {
    return usageOf(item).length ? t('mailTemplate.deleteInUse') : '';
}

const deletingId = ref<string | null>(null);

async function confirmDelete(item: MailTemplateListItemView): Promise<void> {
    if (deletingId.value) return;
    const ok = await confirm({ title: t('mailTemplate.deleteTitle', { name: item.name }), message: t('mailTemplate.deleteMessage'), confirmLabel: t('common.delete'), tone: 'danger' });
    if (!ok) return;
    deletingId.value = item.id;
    try {
        await api.remove(item.id);
        toast.success(t('mailTemplate.deleted'));
        templates.value = (templates.value ?? []).filter((other) => other.id !== item.id);
    } catch (e) {
        // 목록 한 줄 조치의 실패는 토스트(사유 포함 8초 — forms.md 2-3), 서버 문구 그대로
        toast.error(toApiError(e).message, 8000);
    } finally {
        deletingId.value = null;
    }
}

onMounted(load);
</script>

<style scoped>
/* 뱃지 글자 상하 중앙 + 한글 글꼴 보정(components.md 2-4) */
.mail-template-tag {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    line-height: 1;
}
.mail-template-tag :deep(.p-tag-label) {
    position: relative;
    top: -0.09em;
    white-space: nowrap;
}
</style>
