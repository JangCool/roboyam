<template>
    <!--
        핫딜 › 링크 규칙(`/hotdeals/domains`) — 핫딜 상품 링크의 도메인 규칙(차단 · 제휴 · 대형몰). 03-hotdeal 7-1, 계약 「링크 판정」.
        **목업**(`useHotdealDomainApi` → `hotdeal/api/mock`) — 스위치 `?mock=empty|error|slow|forbidden|conflict`. 시드: 단축 주소 BLOCK 9개(메모 「단축 주소」) · 대형몰 7개 · 제휴 예시 `link.coupang.com`.
        - 맨 위 「어떤 링크에 어떤 규칙이 걸리나」 시험 칸: 링크를 넣으면 정규화한 호스트와 걸리는 규칙(접미사 일치 · 더 구체적인 규칙 우선 — `UtilHotdeal.match`, 서버 판정과 같은 규칙)을 바로 보인다.
        - 표(작은 표라 한 번에 다 받고 화면에서 거른다): 도메인 · 규칙 Tag(차단/제휴/—) · 대형몰 · 메모 · 수정자 · 수정 시각 · [고치기]·[지우기](useConfirmDialog). 위에 검색(도메인·메모)·규칙 거르기.
        - [규칙 추가]/[고치기] → `HotdealDomainDialog`(정규화 미리보기 · 규칙 · 대형몰(규칙 없음이면 필수) · 메모 ≤100) → 저장 → 토스트. 중복은 도메인 칸 오류(409 `fields.domain`).
        - 빈: 「규칙이 없으면 모든 쇼핑몰을 허용해요」 · 오류: ErrorState 다시 시도 · 대기: Skeleton.
        - 권한: 보기 `hotdeal.read`(메뉴), 추가·고치기·지우기 `hotdeal.domain.manage`(없으면 단추 없이 읽기 전용 안내).
    -->
    <div class="flex min-w-0 flex-col gap-4">
        <PageTitle :title="t('hotdeal.domain.title')" :subtitle="t('hotdeal.domain.subtitle')">
            <template #actions>
                <Button v-if="canDomain" type="button" icon="pi pi-plus" :label="t('hotdeal.domain.add')" size="small" :disabled="!loaded" @click="openEdit(null)" />
            </template>
        </PageTitle>

        <!-- 시험 칸 -->
        <AppGadget :title="t('hotdeal.domain.testTitle')">
            <div class="flex flex-col gap-2">
                <label :for="`${uid}-test`" class="text-xs text-muted-color">{{ t('hotdeal.domain.testLead') }}</label>
                <InputText :id="`${uid}-test`" v-model="testUrl" type="url" :placeholder="t('hotdeal.domain.testPlaceholder')" autocomplete="off" spellcheck="false" class="w-full max-w-xl" />
                <p v-if="testUrl.trim()" class="m-0 flex flex-wrap items-center gap-2 text-[13px]" role="status" aria-live="polite">
                    <Tag v-if="testResult.match" :value="ruleLabel(testResult.match.rule)" :severity="UtilHotdeal.ruleSeverity(testResult.match.rule)" class="text-[11px]!" />
                    <Tag v-if="testResult.match?.common" :value="t('hotdeal.domain.commonTag')" severity="secondary" class="text-[11px]!" />
                    <span>{{ UtilHotdealMessages.matchText(t, testResult) }}</span>
                </p>
            </div>
        </AppGadget>

        <div class="flex flex-wrap items-center gap-x-4 gap-y-2">
            <InputText v-model="search" type="search" :placeholder="t('hotdeal.domain.searchPlaceholder')" :aria-label="t('hotdeal.domain.searchPlaceholder')" class="w-64 max-w-full" />
            <SelectButton v-model="ruleFilter" :options="ruleFilterOptions" option-label="label" option-value="value" :allow-empty="false" size="small" :aria-label="t('hotdeal.domain.ruleFilter')" />
            <p v-if="!canDomain" class="flex items-center gap-1.5 text-xs text-muted-color"><i class="pi pi-lock" aria-hidden="true" />{{ t('hotdeal.domain.readOnly') }}</p>
        </div>

        <div class="min-w-0 overflow-hidden rounded-xl border border-(--line) bg-(--p-content-background)">
            <div v-if="loading && !loaded" class="flex flex-col gap-3 p-3" role="status" :aria-label="t('common.loading')" aria-busy="true">
                <Skeleton v-for="n in 5" :key="n" height="2.25rem" />
            </div>
            <ErrorState v-else-if="loadError" :title="t('hotdeal.domain.loadFailed')" :description="loadError.message" retry :retrying="loading" @retry="load" />
            <DataTable
                v-else
                :value="shown"
                data-key="id"
                paginator
                :rows="20"
                :always-show-paginator="false"
                :size="prefs.density === 'compact' ? 'small' : undefined"
                row-hover
                scrollable
                sort-field="domain"
                :sort-order="1"
                class="min-w-0"
            >
                <template #empty>
                    <EmptyState v-if="items.length === 0" icon="pi pi-globe" :title="t('hotdeal.domain.empty')" :description="t('hotdeal.domain.emptyHint')">
                        <Button v-if="canDomain" type="button" icon="pi pi-plus" :label="t('hotdeal.domain.add')" size="small" @click="openEdit(null)" />
                    </EmptyState>
                    <EmptyState v-else icon="pi pi-filter-slash" :title="t('hotdeal.domain.emptyFiltered')" />
                </template>
                <Column field="domain" sortable :header="t('hotdeal.domain.col.domain')" style="min-width: 12rem">
                    <template #body="{ data }">
                        <span class="font-mono text-[13px] break-all">{{ data.domain }}</span>
                    </template>
                </Column>
                <Column :header="t('hotdeal.domain.col.rule')" style="min-width: 6rem">
                    <template #body="{ data }">
                        <Tag v-if="data.rule" :value="ruleLabel(data.rule)" :severity="UtilHotdeal.ruleSeverity(data.rule)" class="text-[11px]!" />
                        <span v-else class="text-[12px] text-muted-color">—</span>
                    </template>
                </Column>
                <Column :header="t('hotdeal.domain.col.common')" style="min-width: 5rem">
                    <template #body="{ data }">
                        <i v-if="data.common" class="pi pi-check text-primary" :aria-label="t('hotdeal.domain.commonTag')" role="img" />
                        <span v-else class="text-[12px] text-muted-color">—</span>
                    </template>
                </Column>
                <Column :header="t('hotdeal.domain.col.note')" style="min-width: 10rem">
                    <template #body="{ data }">
                        <span class="text-[13px] break-words">{{ data.note ?? '—' }}</span>
                    </template>
                </Column>
                <Column :header="t('hotdeal.domain.col.updatedBy')" style="min-width: 7rem">
                    <template #body="{ data }">
                        <span class="text-[13px]" :class="data.updatedBy ? '' : 'text-muted-color'">{{ data.updatedBy ?? t('hotdeal.domain.seed') }}</span>
                    </template>
                </Column>
                <Column :header="t('hotdeal.domain.col.updatedAt')" style="min-width: 7rem">
                    <template #body="{ data }">
                        <time :datetime="data.updatedAt" :title="time.full(data.updatedAt)" class="text-[12px] tabular-nums">{{ time.listLabel(data.updatedAt) }}</time>
                    </template>
                </Column>
                <Column v-if="canDomain" style="width: 6rem">
                    <template #header><span class="sr-only">{{ t('hotdeal.domain.col.actions') }}</span></template>
                    <template #body="{ data }">
                        <span class="flex justify-end gap-0.5">
                            <Button v-tooltip.top="t('hotdeal.domain.edit')" type="button" icon="pi pi-pencil" size="small" severity="secondary" variant="text" rounded :aria-label="t('hotdeal.domain.editLabel', { domain: data.domain })" :disabled="acting" @click="openEdit(data)" />
                            <Button v-tooltip.top="t('hotdeal.domain.remove')" type="button" icon="pi pi-trash" size="small" severity="danger" variant="text" rounded :aria-label="t('hotdeal.domain.removeLabel', { domain: data.domain })" :disabled="acting" @click="remove(data)" />
                        </span>
                    </template>
                </Column>
            </DataTable>
        </div>

        <HotdealDomainDialog v-model:visible="editOpen" :item="editTarget" :busy="acting" :error="editError" :field-errors="editFieldErrors" @submit="save" @clear-field="clearField" />
    </div>
</template>

<script setup lang="ts">
import type { ApiError } from '~/composables/common/useApiClient';
import type { DomainRule } from '~/composables/hotdeal/api/useHotdealApi.types';
import type { DealDomain, DealDomainRequest, DomainRuleFilter } from '~/composables/hotdeal/api/useHotdealDomainApi.types';
import UtilHotdeal from '~/utils/util-hotdeal';
import UtilHotdealMessages from '~/utils/util-hotdeal-messages';

const { t } = useI18n();
useSeoMeta({ title: () => t('hotdeal.domain.title') });

const api = useHotdealDomainApi();
const toast = useAppToast();
const errorOf = useApiErrorOf();
const time = useDisplayTime();
const { confirm } = useConfirmDialog();
const { prefs } = useLayoutPrefs();
const perm = useHotdealPermission();
const canDomain = perm.canDomain;
const uid = useId();

const items = ref<DealDomain[]>([]);
const loaded = ref(false);
const loading = ref(false);
const loadError = ref<ApiError | null>(null);

/** 작은 표라 한 번에 다 받는다(시험 칸도 전체 규칙으로 판정한다) */
async function load(): Promise<void> {
    loading.value = true;
    loadError.value = null;
    try {
        items.value = (await api.list('ALL', '')).items;
        loaded.value = true;
    } catch (e) {
        loadError.value = errorOf(e);
    } finally {
        loading.value = false;
    }
}
onMounted(() => void load());

const search = ref('');
const ruleFilter = ref<DomainRuleFilter>('ALL');
const shown = computed(() => UtilHotdeal.filterDomains(items.value, ruleFilter.value, search.value));
const ruleFilterOptions = computed(() => UtilHotdeal.RULE_FILTERS.map((value) => ({ value, label: t(`hotdeal.domain.filter.${value}`) })));

function ruleLabel(rule: DomainRule): string {
    return t(`hotdeal.domain.rule.${UtilHotdeal.choiceOf(rule)}`);
}

/* ── 시험 칸 ── */

const testUrl = ref('');
const testResult = computed(() => UtilHotdeal.match(testUrl.value, items.value));

/* ── 추가·고치기·지우기 ── */

const acting = ref(false);
const editOpen = ref(false);
const editTarget = ref<DealDomain | null>(null);
const editError = ref('');
const editFieldErrors = ref<Record<string, string>>({});
const FIELD_KEYS = ['domain', 'common', 'note'];

function openEdit(item: DealDomain | null): void {
    if (acting.value || !canDomain.value) return;
    editTarget.value = item;
    editError.value = '';
    editFieldErrors.value = {};
    editOpen.value = true;
}

function clearField(field: string): void {
    const next = { ...editFieldErrors.value };
    delete next[field];
    editFieldErrors.value = next;
}

async function save(body: DealDomainRequest): Promise<void> {
    if (acting.value) return;
    acting.value = true;
    editError.value = '';
    editFieldErrors.value = {};
    const target = editTarget.value;
    try {
        const saved = target ? await api.update(target.id, body) : await api.create(body);
        editOpen.value = false;
        toast.success(t(target ? 'hotdeal.domain.updated' : 'hotdeal.domain.created', { domain: saved.domain }));
        await load();
    } catch (e) {
        const error = errorOf(e);
        const fields = error.fields ?? {};
        const known = Object.fromEntries(Object.entries(fields).filter(([k]) => FIELD_KEYS.includes(k)));
        editFieldErrors.value = known;
        const other = Object.entries(fields)
            .filter(([k]) => !FIELD_KEYS.includes(k))
            .map(([, v]) => v);
        // 칸에 붙은 오류만 있으면 창 안 한 줄은 비운다(중복 409 는 도메인 칸 아래)
        editError.value = Object.keys(known).length && !other.length ? '' : [error.message, ...other].join(' ');
    } finally {
        acting.value = false;
    }
}

async function remove(item: DealDomain): Promise<void> {
    if (acting.value) return;
    const ok = await confirm({
        title: t('hotdeal.domain.removeTitle', { domain: item.domain }),
        message: t(item.rule === 'BLOCK' ? 'hotdeal.domain.removeBlockMessage' : 'hotdeal.domain.removeMessage'),
        confirmLabel: t('hotdeal.domain.remove'),
        tone: 'danger',
    });
    if (!ok) return;
    acting.value = true;
    try {
        await api.remove(item.id);
        toast.success(t('hotdeal.domain.removed', { domain: item.domain }));
        await load();
    } catch (e) {
        toast.error(t('hotdeal.domain.removeFailed', { reason: errorOf(e).message }), 8000);
        await load();
    } finally {
        acting.value = false;
    }
}
</script>
