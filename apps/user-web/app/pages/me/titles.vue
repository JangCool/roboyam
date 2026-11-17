<template>
    <div class="content-card">
        <header class="mb-3 flex flex-wrap items-center gap-x-3 gap-y-2">
            <h1 class="m-0 min-w-0 flex-1 text-lg font-bold">{{ t('memberTitle.myTitle') }}</h1>
            <Button as="router-link" :to="UtilMemberTitle.HALL_PATH" :label="t('memberTitle.hallOfFame')" icon="pi pi-trophy" size="small" severity="secondary" variant="outlined" data-testid="my-titles-hall" />
        </header>

        <div v-if="state === 'loading'" class="space-y-3" role="status" :aria-label="t('memberTitle.loading')" data-testid="my-titles-loading">
            <div v-for="n in 3" :key="n" class="h-24 animate-pulse rounded-xl bg-surface-200 dark:bg-surface-800" />
        </div>

        <ErrorState v-else-if="state === 'error'" :title="t('memberTitle.loadFailed')" :description="errorMessage" retry data-testid="my-titles-error" @retry="load" />

        <template v-else-if="mine">
            <!-- 대표 칭호 — 작성자 줄에 보일 1개. 「기본」 = 가진 작가 칭호 중 가장 높은 것(서버가 고른다) · 「안 보이기」. 고르면 바로 저장 -->
            <section class="panel-card p-4" :aria-labelledby="repHeadingId" data-testid="my-titles-representative">
                <h2 :id="repHeadingId" class="m-0 text-sm font-semibold">{{ t('memberTitle.repHeading') }}</h2>
                <p class="m-0 mt-0.5 text-xs text-muted-color">{{ t('memberTitle.repHint') }}</p>
                <div role="radiogroup" :aria-labelledby="repHeadingId" class="mt-3 flex flex-col gap-1">
                    <label
                        v-for="option in repOptions"
                        :key="option.value"
                        class="flex min-h-11 cursor-pointer items-center gap-2.5 rounded-lg px-2 hover:bg-surface-100 dark:hover:bg-surface-800"
                        :class="savingRep ? 'cursor-wait opacity-70' : ''"
                        :data-testid="`my-titles-rep-${option.value}`"
                    >
                        <RadioButton :model-value="repValue" :value="option.value" :name="repName" :disabled="savingRep" @update:model-value="pickRepresentative(option.value)" />
                        <TitleChip v-if="option.title" :title="option.title" :scale="2" />
                        <span v-else class="text-sm">{{ option.label }}</span>
                        <span v-if="option.hint" class="text-xs text-muted-color">{{ option.hint }}</span>
                    </label>
                </div>
            </section>

            <!-- 가진 칭호 — 같은 칭호 여러 번은 「×N」과 받은 기간 -->
            <section class="mt-4 panel-card p-4" :aria-labelledby="ownedHeadingId" data-testid="my-titles-owned">
                <h2 :id="ownedHeadingId" class="m-0 text-sm font-semibold">{{ t('memberTitle.ownedHeading', { count: mine.owned.length }) }}</h2>
                <EmptyState v-if="mine.owned.length === 0" :title="t('memberTitle.ownedEmpty')" :description="t('memberTitle.ownedEmptyHint')" icon="pi pi-trophy" class="!py-4" />
                <ul v-else class="m-0 mt-3 flex list-none flex-col gap-2 p-0">
                    <li v-for="title in mine.owned" :key="title.key" class="flex flex-wrap items-center gap-x-2 gap-y-1">
                        <TitleChip :title="title" :scale="2" :count="title.count" />
                        <span v-if="title.periods.length" class="text-xs text-muted-color tabular-nums">{{ periodsText(title.periods) }}</span>
                    </li>
                </ul>
            </section>

            <!-- 다음 칭호까지 — 자동 칭호의 진행(서버 `progress`) -->
            <section v-if="progressRows.length" class="mt-4 panel-card p-4" :aria-labelledby="progressHeadingId" data-testid="my-titles-progress">
                <h2 :id="progressHeadingId" class="m-0 text-sm font-semibold">{{ t('memberTitle.progressHeading') }}</h2>
                <div v-for="row in progressRows" :key="`${row.key}-${row.tier ?? ''}`" class="mt-3">
                    <div class="flex items-center justify-between gap-2 text-xs">
                        <span class="inline-flex min-w-0 items-center gap-1.5">
                            <TitleIcon :icon="row.icon" :tier="row.tier" :scale="2" />
                            <span class="truncate font-medium">{{ row.name }}</span>
                        </span>
                        <span class="shrink-0 tabular-nums" :class="row.percent >= 100 ? 'font-semibold text-green-700 dark:text-green-400' : ''">{{ UtilMemberTitleMessages.progress(t, row.current, row.target) }}</span>
                    </div>
                    <div class="mt-1 h-1.5 overflow-hidden rounded-full bg-surface-200 dark:bg-surface-700" role="progressbar" :aria-label="row.name" aria-valuemin="0" :aria-valuemax="row.target" :aria-valuenow="Math.min(row.current, row.target)">
                        <div class="h-full rounded-full" :class="row.percent >= 100 ? 'bg-green-500' : 'bg-primary'" :style="{ width: `${row.percent}%` }" />
                    </div>
                </div>
            </section>

            <!-- 신청형 칭호 — 조건 충족 표시 · [신청] · 거절 뒤 다시 신청할 수 있는 날 -->
            <section v-if="mine.applicable.length" class="mt-4 panel-card p-4" :aria-labelledby="applyHeadingId" data-testid="my-titles-applicable">
                <h2 :id="applyHeadingId" class="m-0 text-sm font-semibold">{{ t('memberTitle.applyHeading') }}</h2>
                <ul class="m-0 mt-2 list-none p-0">
                    <li v-for="item in mine.applicable" :key="item.key" class="border-b border-surface py-3 last:border-b-0" :data-testid="`my-titles-applicable-${item.key}`">
                        <div class="flex flex-wrap items-center gap-2">
                            <TitleIcon :icon="item.icon" :scale="2" />
                            <b class="min-w-0 flex-1 text-sm font-semibold">{{ localizedName(item.label, item.labelEn ?? null) }}</b>
                            <span v-if="item.pending || pendingOf(item.key)" :class="TAG" class="bg-amber-100 text-amber-900 dark:bg-amber-900/40 dark:text-amber-200">
                                <span class="relative top-[-0.09em]">{{ UtilMemberTitleMessages.status(t, 'PENDING') }}</span>
                            </span>
                            <Button
                                v-else
                                type="button"
                                :label="t('memberTitle.applyAction')"
                                size="small"
                                :disabled="!item.canApply || applying !== null"
                                :loading="applying === item.key"
                                :data-testid="`my-titles-apply-${item.key}`"
                                @click="apply(item.key)"
                            />
                        </div>
                        <ul class="m-0 mt-2 flex list-none flex-col gap-1 p-0 text-xs">
                            <li v-for="(condition, i) in item.conditions" :key="i" class="flex items-center gap-1.5" :class="condition.met ? '' : 'text-muted-color'">
                                <i :class="condition.met ? 'pi pi-check-circle text-green-600 dark:text-green-400' : 'pi pi-circle'" class="text-xs" aria-hidden="true" />
                                <span>{{ condition.label }}</span>
                                <span v-if="condition.current !== null && condition.target !== null" class="tabular-nums text-muted-color">({{ condition.current }}/{{ condition.target }})</span>
                                <span class="sr-only">{{ condition.met ? t('memberTitle.conditionMet') : t('memberTitle.conditionNotMet') }}</span>
                            </li>
                        </ul>
                        <p v-if="item.reapplyAt && !item.canApply && !item.pending" class="m-0 mt-1.5 text-xs text-muted-color">{{ t('memberTitle.reapplyAt', { date: time.date(item.reapplyAt) }) }}</p>
                    </li>
                </ul>
            </section>

            <!-- 신청 상태 — 승인·거절 사유·다시 신청할 수 있는 날 -->
            <section v-if="mine.applications.length" class="mt-4 panel-card p-4" :aria-labelledby="historyHeadingId" data-testid="my-titles-applications">
                <h2 :id="historyHeadingId" class="m-0 text-sm font-semibold">{{ t('memberTitle.applicationsHeading') }}</h2>
                <ul class="m-0 mt-2 list-none p-0">
                    <li v-for="row in mine.applications" :key="row.id" class="border-b border-surface py-2.5 last:border-b-0">
                        <p class="m-0 flex flex-wrap items-center gap-2 text-sm">
                            <span :class="[TAG, statusClass(row.status)]"><span class="relative top-[-0.09em]">{{ UtilMemberTitleMessages.status(t, row.status) }}</span></span>
                            <b class="font-semibold">{{ row.label }}</b>
                            <span class="ml-auto text-xs tabular-nums text-muted-color">{{ time.date(row.createdAt) }}</span>
                        </p>
                        <p v-if="row.reason" class="m-0 mt-1 text-xs leading-snug">{{ t('memberTitle.rejectReasonLine', { reason: row.reason }) }}</p>
                        <p v-if="row.status === 'REJECTED' && row.reapplyAt" class="m-0 mt-0.5 text-xs text-muted-color">{{ t('memberTitle.reapplyAt', { date: time.date(row.reapplyAt) }) }}</p>
                    </li>
                </ul>
            </section>
        </template>
    </div>
</template>

<script setup lang="ts">
import type { MyTitleApplication, MyTitles, ProfileTitle, TitleApplicationStatus } from '~/composables/title/api/useTitleApi.types';
import UtilBoardMessages from '~/utils/util-board-messages';
import UtilMemberTitle from '~/utils/util-member-title';
import UtilMemberTitleMessages from '~/utils/util-member-title-messages';

/**
 * 내 정보 › 칭호 — `/me/titles`(member-title-contract 「화면」). 대표 칭호 고르기(「기본」·「안 보이기」 포함) · 가진 칭호 · 다음 칭호 진행 막대 · 신청형 칭호 신청 · 신청 상태.
 * `GET /api/me/titles` 한 번으로 그린다(진행·신청 줄에 이름·그림이 같이 온다). 대표 칭호를 고르면 `PUT` 이 바뀐 내 칭호를 돌려준다.
 * 한 화면의 값이라 페이지 `ref` 로 둔다. 서버는 `useTitleApi`.
 */
definePageMeta({ middleware: 'auth', meMenu: true });

/** 고르개 값 — 「기본」·「안 보이기」는 칭호 키와 겹치지 않는 글자 */
const REP_DEFAULT = '__default__';
const REP_HIDDEN = '__hidden__';
const TAG = 'inline-flex items-center justify-center rounded px-1.5 py-1 text-[0.6875rem] font-semibold leading-none';

const { t } = useI18n();
const api = useTitleApi();
const toast = useAppToast();
const time = useDisplayTime();
const { localizedName } = useLocalizedName();
const repHeadingId = useId();
const repName = useId();
const ownedHeadingId = useId();
const progressHeadingId = useId();
const applyHeadingId = useId();
const historyHeadingId = useId();

const state = ref<'loading' | 'ready' | 'error'>('loading');
const errorMessage = ref('');
const mine = ref<MyTitles | null>(null);

async function load(): Promise<void> {
    state.value = 'loading';
    try {
        mine.value = await api.mine();
        state.value = 'ready';
    } catch (e) {
        errorMessage.value = UtilBoardMessages.of(t, e);
        state.value = 'error';
    }
}
void load();


/** 받은 기간들 — 「2026년 10월 · 2026년 8월」 */
function periodsText(periods: string[]): string {
    return periods.map((period) => UtilMemberTitleMessages.period(t, period)).join(' · ');
}

/* ── 대표 칭호 ── */
const repValue = computed(() => {
    const m = mine.value;
    if (!m) return REP_DEFAULT;
    if (m.hidden) return REP_HIDDEN;
    return m.representative?.key ?? REP_DEFAULT;
});
const repOptions = computed<{ value: string; label: string; hint: string; title: ProfileTitle | null }[]>(() => [
    { value: REP_DEFAULT, label: t('memberTitle.repDefault'), hint: t('memberTitle.repDefaultHint'), title: null },
    ...(mine.value?.owned ?? []).map((title) => ({ value: title.key, label: title.label, hint: '', title })),
    { value: REP_HIDDEN, label: t('memberTitle.repHidden'), hint: t('memberTitle.repHiddenHint'), title: null },
]);
const savingRep = ref(false);

async function pickRepresentative(value: string): Promise<void> {
    const m = mine.value;
    if (!m || savingRep.value || value === repValue.value) return;
    const body = value === REP_HIDDEN ? { key: null, hidden: true } : { key: value === REP_DEFAULT ? null : value, hidden: false };
    savingRep.value = true;
    try {
        mine.value = await api.setRepresentative(body);
        toast.success(t('memberTitle.repSaved'));
    } catch (e) {
        toast.error(UtilBoardMessages.of(t, e), 8000);
    } finally {
        savingRep.value = false;
    }
}

/* ── 진행 ── */
const progressRows = computed(() =>
    (mine.value?.progress ?? []).map((p) => {
        const label = localizedName(p.label, p.labelEn);
        const tierName = UtilMemberTitleMessages.tier(t, p.tier);
        return {
            ...p,
            name: tierName ? t('memberTitle.progressTier', { label, tier: tierName }) : label,
            percent: UtilMemberTitle.progressPercent(p),
        };
    }),
);

/* ── 신청 ── */
const applying = ref<string | null>(null);
function pendingOf(key: string): MyTitleApplication | undefined {
    return mine.value?.applications.find((a) => a.key === key && a.status === 'PENDING');
}

async function apply(key: string): Promise<void> {
    if (applying.value) return;
    applying.value = key;
    try {
        await api.apply(key);
        toast.success(t('memberTitle.applied'));
        mine.value = await api.mine();
    } catch (e) {
        toast.error(UtilBoardMessages.of(t, e), 8000);
    } finally {
        applying.value = null;
    }
}

function statusClass(status: TitleApplicationStatus): string {
    if (status === 'APPROVED') return 'bg-green-100 text-green-900 dark:bg-green-900/40 dark:text-green-200';
    if (status === 'REJECTED') return 'bg-red-100 text-red-900 dark:bg-red-900/40 dark:text-red-200';
    return 'bg-amber-100 text-amber-900 dark:bg-amber-900/40 dark:text-amber-200';
}

useSeoMeta({ title: () => t('memberTitle.myTitle'), robots: 'noindex' });
</script>
