<template>
    <!--
        알림 템플릿(목록, `/settings/notification-templates/templates`, 설정 > 알림·이벤트 > 알림 템플릿 › 템플릿) — 모든 대상·채널의 템플릿 표. 계약 `docs/features/message-template-contract.md` 「개정 3」 메뉴.
        템플릿 하나 = 대상(이벤트 또는 필수 안내 메일) × 채널(앱 알림 · 이메일 · 채팅) × 언어. 행을 누르면 편집 화면(`templates/[id]`).
        표: 이름(쓰는 중 딱지) · 채널 · 대상(회원 이벤트 / 운영자 이벤트 · 영역 / 필수 안내 메일, 보안 메일 자물쇠) · 받는 사람 · 언어 · 레이아웃(이메일만) · 기본/사용자 · 마지막 수정 · 관리(복사 · 삭제).
        위쪽 거르기: 검색(이름·대상) · 채널 · 받는 사람 · 대상 종류(이벤트/필수 안내 메일) · 대상 · 언어 · 기본/사용자 · 레이아웃(이메일을 볼 때만).
        채널·받는 사람·대상 종류·대상·레이아웃은 주소 `?channel=`·`?audience=`·`?targetType=`·`?target=`·`?layout=` 에 남는다(이벤트 상세 · 필수 안내 메일 상세 · 레이아웃 편집의 「템플릿 보기」가 이 주소로 들어온다).
        새로 만들기 → `templates/new`(대상을 거른 중이면 `?target=`, 채널을 거른 중이면 `?channel=`), 복사 → `new?from=<id>`(편집 화면에서 [저장] 때 만든다). 기본 템플릿은 지울 수 없다.
        삭제는 확인 후 바로 — 쓰는 중이면 단추를 잠그고, 서버도 409 `TEMPLATE_IN_USE`(토스트 8초). 대상·채널당 사용자 템플릿 20개(가정)가 차면 복사를 잠근다.
        권한: 화면 `template.read`(메뉴가 거른다) · 만들기·복사·삭제 `template.manage` — 없으면 단추가 숨는다.
        ⚠ MOCK: API 는 메모리 목업이다(`useNotificationTemplateApi`).
    -->
    <div class="flex min-w-0 flex-col gap-4">
        <PageTitle :title="t('notificationTemplate.templatesTitle')" :subtitle="t('notificationTemplate.templatesSubtitle')">
            <template v-if="canManage && !denied" #actions>
                <Button :label="t('notificationTemplate.newTemplate')" icon="pi pi-plus" size="small" @click="router.push({ path: '/settings/notification-templates/templates/new', query: newQuery })" />
            </template>
        </PageTitle>

        <Message v-if="denied" severity="info" :closable="false" size="small">{{ t('notificationTemplate.denied') }}</Message>

        <template v-else>
            <AppGadget v-if="loading && !templates" :title="t('notificationTemplate.templatesTitle')">
                <div class="flex flex-col gap-2" role="status" :aria-label="t('common.loading')">
                    <Skeleton v-for="n in 8" :key="n" height="2.75rem" />
                </div>
            </AppGadget>

            <AppGadget v-else-if="!templates" :title="t('notificationTemplate.templatesTitle')">
                <ErrorState :title="t('notificationTemplate.templatesLoadFailed')" :description="loadError" retry :retrying="loading" @retry="load" />
            </AppGadget>

            <AppGadget v-else-if="!templates.length" :title="t('notificationTemplate.templatesTitle')">
                <EmptyState icon="pi pi-file" :title="t('notificationTemplate.templatesEmptyTitle')" :description="t('notificationTemplate.templatesEmptyBody')" />
            </AppGadget>

            <template v-else>
                <!-- 조건 -->
                <div class="flex flex-wrap items-center gap-x-4 gap-y-2">
                    <IconField class="w-full sm:w-56">
                        <InputIcon class="pi pi-search" />
                        <InputText v-model="keyword" size="small" fluid :placeholder="t('notificationTemplate.searchPlaceholder')" :aria-label="t('notificationTemplate.searchPlaceholder')" autocomplete="off" />
                    </IconField>
                    <SelectButton v-model="channelFilter" :options="channelOptions" option-label="label" option-value="value" :allow-empty="false" size="small" :aria-label="t('notificationTemplate.filterChannel')" />
                    <SelectButton v-model="audienceFilter" :options="audienceOptions" option-label="label" option-value="value" :allow-empty="false" size="small" :aria-label="t('notificationTemplate.filterAudience')" />
                    <SelectButton v-model="targetTypeFilter" :options="targetTypeOptions" option-label="label" option-value="value" :allow-empty="false" size="small" :aria-label="t('notificationTemplate.filterTargetType')" />
                    <Select v-model="targetFilter" :options="targetOptions" option-label="label" option-value="value" option-group-label="label" option-group-children="items" :placeholder="t('notificationTemplate.filterTargetAll')" show-clear filter size="small" class="w-full sm:w-60" :aria-label="t('notificationTemplate.filterTarget')" />
                    <SelectButton v-model="localeFilter" :options="localeOptions" option-label="label" option-value="value" :allow-empty="false" size="small" :aria-label="t('notificationTemplate.filterLocale')" />
                    <SelectButton v-model="originFilter" :options="originOptions" option-label="label" option-value="value" :allow-empty="false" size="small" :aria-label="t('notificationTemplate.filterOrigin')" />
                    <Select v-if="channelFilter === 'ALL' || channelFilter === 'EMAIL'" v-model="layoutFilter" :options="layoutOptions" option-label="label" option-value="value" :placeholder="t('notificationTemplate.filterLayoutAll')" show-clear size="small" class="w-full sm:w-44" :aria-label="t('notificationTemplate.filterLayout')" />
                    <p class="ml-auto text-[13px] text-muted-color" aria-live="polite">{{ t('notificationTemplate.listCount', { n: filtered.length, total: templates.length }) }}</p>
                </div>

                <div class="min-w-0 overflow-hidden rounded-xl border border-(--line) bg-(--p-content-background)">
                    <DataTable :value="filtered" data-key="id" row-hover scrollable paginator :rows="50" :size="prefs.density === 'compact' ? 'small' : undefined" :row-class="() => 'cursor-pointer'" @row-click="onRowClick">
                        <template #empty>
                            <EmptyState icon="pi pi-filter-slash" :title="t('common.emptyFiltered')">
                                <Button type="button" :label="t('notificationTemplate.clearFilter')" size="small" severity="secondary" variant="outlined" @click="clearFilter" />
                            </EmptyState>
                        </template>

                        <Column :header="t('notificationTemplate.colName')" style="min-width: 13rem">
                            <template #body="{ data }">
                                <div class="flex min-w-0 flex-col gap-1">
                                    <NuxtLink :to="editPath(data)" class="text-[13px] font-medium hover:underline">{{ data.name }}</NuxtLink>
                                    <div v-if="usageOf(data).length" class="flex flex-wrap items-center gap-1">
                                        <Tag v-for="label in usageOf(data)" :key="label" :value="label" severity="info" class="mail-template-tag text-[10px]!" />
                                    </div>
                                </div>
                            </template>
                        </Column>
                        <Column :header="t('notificationTemplate.colChannel')" style="min-width: 6.5rem">
                            <template #body="{ data }">
                                <span class="inline-flex items-center gap-1.5 text-[12px]">
                                    <i :class="UtilNotificationTemplateMessages.channelIcon(data.channel)" class="text-[11px] text-muted-color" aria-hidden="true" />
                                    {{ UtilNotificationTemplateMessages.channelLabel(t, data.channel) }}
                                </span>
                            </template>
                        </Column>
                        <Column :header="t('notificationTemplate.colTarget')" style="min-width: 13rem">
                            <template #body="{ data }">
                                <div class="flex min-w-0 flex-col gap-0.5">
                                    <span class="inline-flex min-w-0 items-center gap-1.5 text-[12px]">
                                        <NuxtLink :to="UtilNotificationTemplateMessages.targetPath(data.target)" class="truncate hover:underline">{{ data.targetName }}</NuxtLink>
                                        <span v-if="data.security" v-tooltip.top="t('notificationTemplate.securityTip')" class="inline-flex shrink-0" role="img" :aria-label="t('notificationTemplate.securityTag')">
                                            <i class="pi pi-lock text-[11px] text-amber-600 dark:text-amber-400" aria-hidden="true" />
                                        </span>
                                    </span>
                                    <span class="text-[11px] text-muted-color">{{ UtilNotificationTemplateMessages.targetGroupLabel(t, data.target.type, data.target.audience, data.targetArea) }}</span>
                                </div>
                            </template>
                        </Column>
                        <Column :header="t('notificationTemplate.colAudience')" style="min-width: 6rem">
                            <template #body="{ data }">
                                <span class="inline-flex items-center gap-1.5 text-[12px]">
                                    <i :class="UtilNotificationTemplateMessages.audienceIcon(data.target.audience)" class="text-[11px] text-muted-color" aria-hidden="true" />
                                    {{ UtilNotificationTemplateMessages.audienceLabel(t, data.target.audience) }}
                                </span>
                            </template>
                        </Column>
                        <Column :header="t('notificationTemplate.colLocale')" style="min-width: 5.5rem">
                            <template #body="{ data }">
                                <span class="text-[12px]">{{ UtilNotificationTemplateMessages.localeLabel(t, data.locale) }}</span>
                            </template>
                        </Column>
                        <Column :header="t('notificationTemplate.colLayout')" style="min-width: 8rem">
                            <template #body="{ data }">
                                <span v-if="data.layoutName" class="text-[12px]">{{ data.layoutName }}</span>
                                <span v-else class="text-[12px] text-muted-color" aria-hidden="true">—</span>
                            </template>
                        </Column>
                        <Column :header="t('notificationTemplate.colOrigin')" style="min-width: 6rem">
                            <template #body="{ data }">
                                <Tag v-if="data.builtin" :value="t('notificationTemplate.builtinTag')" icon="pi pi-lock" severity="secondary" class="mail-template-tag text-[10px]!" />
                                <span v-else class="text-[12px]">{{ t('notificationTemplate.userTag') }}</span>
                            </template>
                        </Column>
                        <Column :header="t('notificationTemplate.colUpdated')" style="min-width: 9rem">
                            <template #body="{ data }">
                                <span v-if="data.updatedAt" class="text-[12px]">
                                    <span class="tabular-nums">{{ time.listLabel(data.updatedAt) }}</span>
                                    <span v-if="data.updatedByName" class="block text-[11px] text-muted-color">{{ data.updatedByName }}</span>
                                </span>
                                <span v-else class="text-[12px] text-muted-color">{{ t('notificationTemplate.builtinUpdated') }}</span>
                            </template>
                        </Column>
                        <Column v-if="canManage" :header="t('notificationTemplate.colActions')" style="min-width: 7rem">
                            <template #body="{ data }">
                                <div class="flex items-center gap-0.5">
                                    <span v-tooltip.top="copyBlockedReason(data) || t('notificationTemplate.copy')" class="inline-flex">
                                        <Button type="button" icon="pi pi-copy" size="small" severity="secondary" variant="text" rounded :disabled="Boolean(copyBlockedReason(data))" :aria-label="t('notificationTemplate.copyLabel', { name: data.name })" :aria-description="copyBlockedReason(data) || undefined" @click="copy(data)" />
                                    </span>
                                    <span v-if="!data.builtin" v-tooltip.top="deleteBlockedReason(data) || t('common.delete')" class="inline-flex">
                                        <Button type="button" icon="pi pi-trash" size="small" severity="danger" variant="text" rounded :disabled="Boolean(deleteBlockedReason(data)) || deletingId === data.id" :loading="deletingId === data.id" :aria-label="t('notificationTemplate.deleteLabel', { name: data.name })" :aria-description="deleteBlockedReason(data) || undefined" @click="confirmDelete(data)" />
                                    </span>
                                </div>
                            </template>
                        </Column>
                    </DataTable>
                </div>
                <p class="m-0 text-xs text-muted-color">{{ t('notificationTemplate.listHint') }}</p>
            </template>
        </template>
    </div>
</template>

<script setup lang="ts">
import type { DataTableRowClickEvent } from 'primevue/datatable';
import { mockTemplateCan } from '~/composables/template/api/mock/notification-template-mock';
import type { TemplateAudience, TemplateChannel, TemplateListItemView, TemplateLocale, TemplateTargetType } from '~/composables/template/api/useNotificationTemplateApi.types';
import { TEMPLATE_AUDIENCES, TEMPLATE_CHANNELS, TEMPLATE_LIMIT, TEMPLATE_TARGET_TYPES } from '~/composables/template/api/useNotificationTemplateApi.types';
import type { TemplateListFilter } from '~/utils/util-notification-template';
import UtilNotificationTemplate from '~/utils/util-notification-template';
import UtilNotificationTemplateMessages from '~/utils/util-notification-template-messages';

const { t } = useI18n();
useSeoMeta({ title: () => t('notificationTemplate.templatesTitle') });

const api = useNotificationTemplateApi();
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

const templates = ref<TemplateListItemView[] | null>(null);
const loading = ref(false);
const loadError = ref('');
const denied = ref(false);

/* ── 거르기 ── */

const queryText = (value: unknown) => (typeof value === 'string' && value ? value : null);
const oneOf = <T extends string>(value: unknown, allowed: readonly T[]): T | 'ALL' => (allowed.includes(value as T) ? (value as T) : 'ALL');
const keyword = ref('');
const channelFilter = ref<TemplateChannel | 'ALL'>(oneOf(route.query.channel, TEMPLATE_CHANNELS));
const audienceFilter = ref<TemplateAudience | 'ALL'>(oneOf(route.query.audience, TEMPLATE_AUDIENCES));
const targetTypeFilter = ref<TemplateTargetType | 'ALL'>(oneOf(route.query.targetType, TEMPLATE_TARGET_TYPES));
const targetFilter = ref<string | null>(queryText(route.query.target));
const layoutFilter = ref<string | null>(queryText(route.query.layout));
const localeFilter = ref<TemplateLocale | 'ALL'>('ALL');
const originFilter = ref<TemplateListFilter['origin']>('ALL');

// 채널·받는 사람·대상 종류·대상·레이아웃 거르기는 주소에 남긴다(뒤로 가기·링크로 다시 들어와도 같은 목록)
watch([channelFilter, audienceFilter, targetTypeFilter, targetFilter, layoutFilter], ([channel, audience, targetType, target, layout]) => {
    const query = { ...route.query };
    const put = (key: string, value: string | null) => {
        if (value && value !== 'ALL') query[key] = value;
        else delete query[key];
    };
    put('channel', channel);
    put('audience', audience);
    put('targetType', targetType);
    put('target', target);
    put('layout', layout);
    void router.replace({ query });
});

// 받는 사람·대상 종류를 바꾸면 맞지 않는 대상 거르기는 푼다, 이메일이 아닌 채널을 보면 레이아웃 거르기는 푼다
watch([audienceFilter, targetTypeFilter], ([audience, type]) => {
    if (!targetFilter.value) return;
    const item = (templates.value ?? []).find((other) => other.target.code === targetFilter.value);
    if (item && ((audience !== 'ALL' && item.target.audience !== audience) || (type !== 'ALL' && item.target.type !== type))) targetFilter.value = null;
});
watch(channelFilter, (channel) => {
    if (channel !== 'ALL' && channel !== 'EMAIL') layoutFilter.value = null;
});

const allOption = computed(() => ({ value: 'ALL', label: t('common.all') }));
const channelOptions = computed(() => [allOption.value, ...TEMPLATE_CHANNELS.map((value) => ({ value, label: UtilNotificationTemplateMessages.channelLabel(t, value) }))]);
const audienceOptions = computed(() => [allOption.value, ...TEMPLATE_AUDIENCES.map((value) => ({ value, label: UtilNotificationTemplateMessages.audienceLabel(t, value) }))]);
const targetTypeOptions = computed(() => [allOption.value, { value: 'EVENT', label: t('notificationTemplate.targetEvent') }, { value: 'REQUIRED_MAIL', label: t('notificationTemplate.targetRequiredMail') }]);

/** 대상 고르기 — 목록에 나온 대상을 「회원 이벤트 / 운영자 이벤트 / 필수 안내 메일」 묶음으로(받은 차례). 받는 사람·대상 종류를 거르면 그것만 */
const targetOptions = computed(() => {
    const groups = new Map<string, { label: string; items: { value: string; label: string }[] }>();
    const seen = new Set<string>();
    for (const item of templates.value ?? []) {
        if (seen.has(item.target.code)) continue;
        if (audienceFilter.value !== 'ALL' && item.target.audience !== audienceFilter.value) continue;
        if (targetTypeFilter.value !== 'ALL' && item.target.type !== targetTypeFilter.value) continue;
        seen.add(item.target.code);
        const label = UtilNotificationTemplateMessages.targetGroupLabel(t, item.target.type, item.target.audience);
        const group = groups.get(label) ?? { label, items: [] };
        group.items.push({ value: item.target.code, label: item.targetName });
        groups.set(label, group);
    }
    return [...groups.values()];
});

/** 새 템플릿 — 거른 대상·채널로 시작한다 */
const newQuery = computed(() => ({ ...(targetFilter.value ? { target: targetFilter.value } : {}), ...(channelFilter.value !== 'ALL' ? { channel: channelFilter.value } : {}) }));
/** 레이아웃 고르기 — 목록에 나온 레이아웃(쓰는 템플릿이 없는 레이아웃은 나오지 않는다) */
const layoutOptions = computed(() => {
    const seen = new Map<string, string>();
    for (const item of templates.value ?? []) if (item.layoutId && !seen.has(item.layoutId)) seen.set(item.layoutId, item.layoutName ?? item.layoutId);
    // 주소로 들어온 레이아웃이 아직 아무도 안 쓰는 것이면 고르기에서 보이도록 남긴다
    if (layoutFilter.value && !seen.has(layoutFilter.value)) seen.set(layoutFilter.value, t('notificationTemplate.filterLayoutUnknown'));
    return [...seen].map(([value, label]) => ({ value, label }));
});
const localeOptions = computed(() => [allOption.value, { value: 'ko', label: UtilNotificationTemplateMessages.localeLabel(t, 'ko') }, { value: 'en', label: UtilNotificationTemplateMessages.localeLabel(t, 'en') }]);
const originOptions = computed(() => [allOption.value, { value: 'BUILTIN', label: t('notificationTemplate.builtinTag') }, { value: 'USER', label: t('notificationTemplate.userTag') }]);

const filtered = computed(() =>
    UtilNotificationTemplate.filterTemplates(templates.value ?? [], {
        channel: channelFilter.value === 'ALL' ? null : channelFilter.value,
        audience: audienceFilter.value === 'ALL' ? null : audienceFilter.value,
        targetType: targetTypeFilter.value === 'ALL' ? null : targetTypeFilter.value,
        target: targetFilter.value,
        locale: localeFilter.value === 'ALL' ? null : localeFilter.value,
        origin: originFilter.value,
        layoutId: layoutFilter.value,
        keyword: keyword.value,
    }),
);

function clearFilter(): void {
    keyword.value = '';
    channelFilter.value = 'ALL';
    audienceFilter.value = 'ALL';
    targetTypeFilter.value = 'ALL';
    targetFilter.value = null;
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

const editPath = (item: TemplateListItemView) => `/settings/notification-templates/templates/${encodeURIComponent(item.id)}`;

/** 행을 누르면 편집 화면으로 — 줄 안의 단추·링크를 누른 것은 그 동작만 한다 */
function onRowClick(event: DataTableRowClickEvent): void {
    const target = event.originalEvent.target as HTMLElement | null;
    if (target?.closest('button, a, input')) return;
    void router.push(editPath(event.data as TemplateListItemView));
}

/** 저장된 상태에서 이 템플릿을 쓰는 곳(「한국어로 씀」 · 「규칙 2개에서 씀」) */
function usageOf(item: TemplateListItemView): string[] {
    const out = item.defaultFor.map((locale) => t('notificationTemplate.usedAsDefault', { locale: UtilNotificationTemplateMessages.localeLabel(t, locale) }));
    if (item.ruleCount > 0) out.push(t('notificationTemplate.usedByRules', { count: item.ruleCount }));
    return out;
}

/* ── 복사 · 삭제 ── */

function copyBlockedReason(item: TemplateListItemView): string {
    const count = (templates.value ?? []).filter((other) => other.target.code === item.target.code && other.channel === item.channel && !other.builtin).length;
    return count >= TEMPLATE_LIMIT ? t('notificationTemplate.limitReached', { max: TEMPLATE_LIMIT }) : '';
}

function copy(item: TemplateListItemView): void {
    if (copyBlockedReason(item)) return;
    void router.push({ path: '/settings/notification-templates/templates/new', query: { from: item.id } });
}

function deleteBlockedReason(item: TemplateListItemView): string {
    return usageOf(item).length ? t('notificationTemplate.deleteInUse') : '';
}

const deletingId = ref<string | null>(null);

async function confirmDelete(item: TemplateListItemView): Promise<void> {
    if (deletingId.value) return;
    const ok = await confirm({ title: t('notificationTemplate.deleteTitle', { name: item.name }), message: t('notificationTemplate.deleteMessage'), confirmLabel: t('common.delete'), tone: 'danger' });
    if (!ok) return;
    deletingId.value = item.id;
    try {
        await api.remove(item.id);
        toast.success(t('notificationTemplate.deleted'));
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
