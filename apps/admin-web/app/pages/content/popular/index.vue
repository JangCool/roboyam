<template>
    <!--
        인기글 관리 — 기간(실시간 · 어제 · 이번 주)마다 공개 인기글과 같은 차례·같은 글의 순위를 보고, 부적절한 글을 인기글에서 빼거나 다시 넣는다(계약 `docs/features/popular-highlight-contract.md`, community `api-content.md` 1-2절).
        빼면 세 기간·채우기 모두에서 빠지고 글 자체(게시판·검색·최신)는 그대로다. 사유는 코드 하나 + 메모(`OTHER` 는 메모 필수, 100자) — 글쓴이에게 알리지 않는다.
        순위는 DB 배치가 계산하므로 빼기·다시 넣기 뒤 응답은 아직 옛 순위일 수 있다: 계산 뒤에 뺀 글은 `excluded` 줄(순위 칸 「뺌」)로 자리에 남고, `recomputePending` 이면 「다시 계산 중」을 보이며 잠시 뒤 다시 읽는다.
        점수는 계산 시점의 값이다(`computedAt`). 채운 줄(`fill`)에는 「어제 글」·「지난주 글」 표시. 뺀 글 목록은 기간과 무관(최근 200개), 지워졌거나 볼 수 없는 글은 「볼 수 없는 글」.
        권한: 보기 `popular.read`, 빼기·다시 넣기 `popular.manage` — 없으면 단추 칸이 없고 안내 한 줄. 이미 지운 글은 서버가 404 로 거절한다(서버 문구 그대로 토스트 + 다시 읽기).
        관리자 화면은 공개 사이트 주소를 모르므로 글 링크 대신 글 번호를 복사하는 단추를 둔다.
    -->
    <div class="flex min-w-0 flex-col gap-4">
        <PageTitle :title="t('popular.rankingTitle')" :subtitle="t('popular.rankingSubtitle')">
            <template #actions>
                <NuxtLink to="/content/popular/settings" class="inline-flex items-center gap-1.5 text-sm text-primary hover:underline">
                    <i class="pi pi-sliders-h text-xs" aria-hidden="true" />{{ t('popular.openSettings') }}
                </NuxtLink>
                <Button type="button" icon="pi pi-refresh" :label="t('popular.refresh')" size="small" severity="secondary" variant="outlined" :loading="busy" @click="reload()" />
            </template>
        </PageTitle>

        <SelectButton v-model="period" :options="periodOptions" option-label="label" option-value="value" :allow-empty="false" :aria-label="t('popular.periodLabel')" class="popular-tabs self-start" @update:model-value="onPeriod" />

        <p v-if="!canManage" class="m-0 flex items-center gap-1.5 text-xs text-muted-color">
            <i class="pi pi-lock" aria-hidden="true" />{{ t('popular.manageReadOnly') }}
        </p>

        <AppGadget :title="t('popular.rankTitle', { period: t(`popular.period.${period}`) })">
            <template #title-extra>
                <Tag v-if="view?.recomputePending" :value="t('popular.recomputing')" icon="pi pi-spin pi-spinner" severity="info" />
            </template>

            <div v-if="view" class="mb-3 flex flex-col gap-1 text-xs leading-relaxed text-muted-color">
                <p class="m-0">
                    <span class="font-medium text-color" :title="`${time.full(view.from)} ~ ${time.full(view.to)}`">{{ UtilPopularMessages.periodRange(time, view) }}</span>
                    · {{ UtilPopularMessages.periodLead(t, view.period) }}<template v-if="view.period === 'WEEK'"> · {{ t('popular.weekStartsOn', { day: t(`popular.weekStart.${view.weekStart}`) }) }}</template>
                </p>
                <p class="m-0">{{ UtilPopularMessages.computedAt(t, time, view.computedAt) }} · {{ t('popular.valuesNote') }}</p>
            </div>
            <Message v-if="view?.recomputePending" severity="info" size="small" :closable="false" class="mb-3">{{ t('popular.recomputeNotice') }}</Message>

            <div v-if="busy && !view" class="flex flex-col gap-2" role="status" :aria-label="t('common.loading')">
                <Skeleton v-for="n in 5" :key="n" height="2.75rem" />
            </div>
            <ErrorState v-else-if="loadError && !view" :title="t('popular.rankingLoadFailed')" :description="loadError.message" retry :retrying="busy" @retry="reload()" />
            <template v-else-if="view">
                <EmptyState v-if="view.items.length === 0" icon="pi pi-chart-line" :title="emptyRanking.title" :description="emptyRanking.text" />
                <div v-else class="overflow-x-auto">
                    <table class="w-full min-w-[44rem] text-[13px]">
                        <thead>
                            <tr class="border-b border-(--line) text-left text-xs text-muted-color">
                                <th class="w-14 py-2 pr-2 font-medium">{{ t('popular.colRank') }}</th>
                                <th class="py-2 pr-2 font-medium">{{ t('popular.colPost') }}</th>
                                <th class="w-20 py-2 pr-2 text-right font-medium">{{ t('popular.colScore') }}</th>
                                <th class="w-36 py-2 pr-2 text-right font-medium">{{ t('popular.colIngredients') }}</th>
                                <th class="w-28 py-2 pr-2 font-medium">{{ t('popular.colCreatedAt') }}</th>
                                <th v-if="canManage" class="w-32 py-2 font-medium"><span class="sr-only">{{ t('popular.colActions') }}</span></th>
                            </tr>
                        </thead>
                        <tbody>
                            <tr v-for="row in view.items" :key="row.post.id" class="border-b border-(--line) last:border-0" :class="row.excluded ? 'opacity-60' : ''">
                                <td class="py-2 pr-2 align-top">
                                    <span v-if="row.rank !== null" class="font-semibold tabular-nums">{{ row.rank }}</span>
                                    <Tag v-else :value="t('popular.excludedTag')" severity="warn" class="text-[11px]!" />
                                </td>
                                <td class="py-2 pr-2 align-top">
                                    <ContentPostCell :post="row.post">
                                        <Tag v-if="row.fill" :value="t(`popular.fill.${row.fill}`)" severity="secondary" class="text-[11px]!" />
                                    </ContentPostCell>
                                </td>
                                <td class="py-2 pr-2 text-right align-top font-medium tabular-nums">{{ UtilPopular.score(row.score, locale) }}</td>
                                <td class="py-2 pr-2 text-right align-top text-xs tabular-nums text-muted-color">
                                    {{ t('popular.ingredients', { likes: row.post.likeCount, comments: row.post.commentCount, views: row.post.viewCount }) }}
                                </td>
                                <td class="py-2 pr-2 align-top text-xs tabular-nums text-muted-color">{{ time.listLabel(row.post.createdAt) }}</td>
                                <td v-if="canManage" class="py-2 text-right align-top">
                                    <Button
                                        v-if="!row.excluded"
                                        type="button"
                                        :label="t('popular.exclude')"
                                        icon="pi pi-eye-slash"
                                        size="small"
                                        severity="danger"
                                        variant="outlined"
                                        :aria-label="t('popular.excludeLabel', { title: row.post.title })"
                                        @click="openExclude(row.post)"
                                    />
                                </td>
                            </tr>
                        </tbody>
                    </table>
                </div>
            </template>
        </AppGadget>

        <!-- 뺀 글 — 기간과 무관, 최근 200개 -->
        <AppGadget v-if="view" :title="t('popular.excludedTitle')">
            <template #title-extra>
                <span class="text-xs tabular-nums text-muted-color">{{ view.excluded.length }}</span>
            </template>
            <p class="mb-3 text-xs leading-relaxed text-muted-color">
                {{ t('popular.excludedLead') }}<template v-if="view.excluded.length >= UtilPopular.EXCLUDED_LIMIT"> {{ t('popular.excludedLimit', { n: UtilPopular.EXCLUDED_LIMIT }) }}</template>
            </p>

            <EmptyState v-if="view.excluded.length === 0" icon="pi pi-check-circle" :title="t('popular.excludedEmpty')" :description="t('popular.excludedEmptyHint')" />
            <ul v-else class="m-0 flex list-none flex-col divide-y divide-(--line) p-0">
                <li v-for="row in view.excluded" :key="row.postId" class="flex flex-wrap items-start justify-between gap-3 py-2.5">
                    <div class="flex min-w-0 flex-1 flex-col gap-1">
                        <ContentPostCell v-if="row.post" :post="row.post">
                            <Tag v-if="!row.available" :value="t('popular.unavailableTag')" severity="secondary" class="text-[11px]!" />
                        </ContentPostCell>
                        <p v-else class="m-0 text-[13px] text-muted-color">{{ t('popular.goneTitle', { id: row.postId }) }}</p>
                        <p class="m-0 flex flex-wrap items-center gap-1.5 text-xs">
                            <Tag :value="t(`popular.reason.${row.reason}`)" severity="warn" class="text-[11px]!" />
                            <span v-if="row.memo" class="break-words">{{ row.memo }}</span>
                        </p>
                        <p class="m-0 text-xs tabular-nums text-muted-color">{{ t('popular.excludedBy', { name: row.excludedBy?.name ?? '—', at: time.full(row.excludedAt) }) }}</p>
                    </div>
                    <Button
                        v-if="canManage"
                        type="button"
                        :label="t('popular.restore')"
                        icon="pi pi-undo"
                        size="small"
                        severity="secondary"
                        variant="outlined"
                        :loading="restoringId === row.postId"
                        :disabled="restoringId !== null && restoringId !== row.postId"
                        :aria-label="t('popular.restoreLabel', { title: row.post?.title ?? row.postId })"
                        @click="restore(row)"
                    />
                </li>
            </ul>
        </AppGadget>

        <!-- 빼기 — 사유 코드 + 메모(`OTHER` 는 메모 필수). 열 때마다 비운다 -->
        <Dialog v-model:visible="excludeOpen" modal :draggable="false" :header="t('popular.excludeTitle')" :style="{ width: 'min(32rem, calc(100vw - 2rem))' }">
            <form :id="excludeFormId" class="flex flex-col gap-4" novalidate @submit.prevent="submitExclude">
                <div v-if="excludeTarget" class="rounded-lg border border-(--line) px-3 py-2">
                    <ContentPostCell :post="excludeTarget" />
                </div>
                <p class="m-0 text-xs leading-relaxed text-muted-color">{{ t('popular.excludeNote') }}</p>

                <fieldset class="m-0 flex flex-col gap-2 border-0 p-0">
                    <legend class="mb-1.5 text-sm font-medium">{{ t('popular.reasonLabel') }}</legend>
                    <div v-for="code in reasonCodes" :key="code" class="flex items-center gap-2">
                        <RadioButton v-model="excludeReason" :input-id="`${excludeFormId}-${code}`" name="reason" :value="code" :disabled="excludeBusy" />
                        <label :for="`${excludeFormId}-${code}`" class="text-sm">{{ t(`popular.reason.${code}`) }}</label>
                    </div>
                    <p v-if="excludeFieldErrors.reason" role="alert" class="m-0 text-xs text-red-600 dark:text-red-400">{{ excludeFieldErrors.reason }}</p>
                </fieldset>

                <LabeledField :label="t('popular.memoLabel')" :hint="excludeReason === 'OTHER' ? t('popular.memoRequiredHint') : t('popular.memoOptionalHint')" :help="t('popular.memoHelp', { max: UtilPopular.MEMO_MAX })" :error="memoError">
                    <template #default="{ id, describedBy, invalid }">
                        <Textarea :id="id" v-model="excludeMemo" name="memo" rows="2" fluid auto-resize :placeholder="t('popular.memoPlaceholder')" :disabled="excludeBusy" :invalid="invalid" :aria-describedby="describedBy" />
                    </template>
                </LabeledField>

                <Message v-if="excludeError" severity="error" size="small" variant="simple" :closable="false">{{ excludeError }}</Message>
            </form>
            <template #footer>
                <Button type="button" :label="t('common.cancel')" severity="secondary" variant="text" :disabled="excludeBusy" @click="excludeOpen = false" />
                <Button type="submit" :form="excludeFormId" :label="t('popular.exclude')" severity="danger" :loading="excludeBusy" />
            </template>
        </Dialog>
    </div>
</template>

<script setup lang="ts">
import type { ApiError } from '~/composables/common/useApiClient';
import type { AdminPostRef, PopularExcludeReason, PopularExcluded, PopularPeriod, PopularRankingView } from '~/composables/content/api/usePopularApi.types';
import UtilPopular from '~/utils/util-popular';
import UtilPopularMessages from '~/utils/util-popular-messages';

const { t, locale } = useI18n();
useSeoMeta({ title: () => t('popular.rankingTitle') });

const api = usePopularApi();
const route = useRoute();
const toast = useAppToast();
const errorOf = useApiErrorOf();
const time = useDisplayTime();
const { confirm } = useConfirmDialog();
const { can } = useAdminPermission();

/** 빼기·다시 넣기 권한(정의: util-permission-catalog.ts) — 숨기는 것은 편의이고 막는 것은 서버다 */
const canManage = computed(() => can('popular.manage'));

const initial = String(route.query.period ?? '').toUpperCase();
const period = ref<PopularPeriod>((UtilPopular.PERIODS as readonly string[]).includes(initial) ? (initial as PopularPeriod) : 'REALTIME');
const view = ref<PopularRankingView | null>(null);
const busy = ref(false);
const loadError = ref<ApiError | null>(null);
const restoringId = ref<string | null>(null);

// ⚠ `computed` 다 — 그냥 배열이면 처음 읽은 순간의 언어로 굳는다
const periodOptions = computed(() => UtilPopular.PERIODS.map((value) => ({ value, label: t(`popular.period.${value}`) })));
const reasonCodes = computed<readonly PopularExcludeReason[]>(() => (view.value?.reasons.length ? view.value.reasons : UtilPopular.REASONS));
const emptyRanking = computed(() => UtilPopularMessages.emptyRanking(t, period.value, view.value?.computedAt ?? null));

onMounted(() => void reload());

let request = 0;
async function reload(silent = false): Promise<void> {
    const mine = ++request;
    if (!silent) pollCount = 0;
    busy.value = true;
    loadError.value = null;
    try {
        const found = await api.ranking(period.value);
        if (mine !== request) return;
        view.value = found;
        schedulePoll();
    } catch (e) {
        if (mine !== request) return;
        loadError.value = errorOf(e);
        if (view.value && !silent) toast.error(loadError.value.message, 8000);
    } finally {
        if (mine === request) busy.value = false;
    }
}

function onPeriod(): void {
    view.value = null;
    void reload();
}

/* ── 다시 계산 기다리기 ── */
let pollTimer: ReturnType<typeof setTimeout> | null = null;
let pollCount = 0;

function schedulePoll(): void {
    if (pollTimer) clearTimeout(pollTimer);
    pollTimer = null;
    if (!view.value?.recomputePending || pollCount >= UtilPopular.RECOMPUTE_POLL_MAX) return;
    pollTimer = setTimeout(() => {
        pollCount += 1;
        void reload(true);
    }, UtilPopular.RECOMPUTE_POLL_MS);
}

onBeforeUnmount(() => {
    if (pollTimer) clearTimeout(pollTimer);
});

/* ── 빼기 ── */
const excludeFormId = useId();
const excludeOpen = ref(false);
const excludeTarget = ref<AdminPostRef | null>(null);
const excludeReason = ref<PopularExcludeReason>('INAPPROPRIATE');
const excludeMemo = ref('');
const excludeTried = ref(false);
const excludeBusy = ref(false);
const excludeError = ref('');
const excludeFieldErrors = ref<Record<string, string>>({});

const memoError = computed(() => excludeFieldErrors.value.memo || (excludeTried.value ? UtilPopularMessages.memoProblem(t, UtilPopular.memoProblem(excludeReason.value, excludeMemo.value)) : ''));

watch([excludeReason, excludeMemo], () => {
    excludeFieldErrors.value = {};
});

/** 열 때마다 비운다 — 앞 글에 적던 메모가 다음 글에 딸려 가면 안 된다 */
function openExclude(post: AdminPostRef): void {
    excludeTarget.value = post;
    excludeReason.value = reasonCodes.value[0] ?? 'INAPPROPRIATE';
    excludeMemo.value = '';
    excludeTried.value = false;
    excludeError.value = '';
    excludeFieldErrors.value = {};
    excludeOpen.value = true;
}

async function submitExclude(): Promise<void> {
    const target = excludeTarget.value;
    if (!target || excludeBusy.value) return;
    excludeTried.value = true;
    if (UtilPopular.memoProblem(excludeReason.value, excludeMemo.value)) return;
    excludeBusy.value = true;
    excludeError.value = '';
    excludeFieldErrors.value = {};
    const memo = excludeMemo.value.trim();
    try {
        await api.exclude({ postId: target.id, reason: excludeReason.value, memo: memo || null });
        excludeOpen.value = false;
        toast.success(t('popular.excluded', { title: target.title }));
        await reload();
    } catch (e) {
        const error = errorOf(e);
        if (error.fields && (error.fields.reason || error.fields.memo)) {
            excludeFieldErrors.value = { ...error.fields };
        } else if (error.code === 'POST_NOT_FOUND') {
            excludeOpen.value = false;
            toast.error(error.message, 8000);
            void reload();
        } else {
            excludeError.value = error.fields?.postId ?? error.message;
        }
    } finally {
        excludeBusy.value = false;
    }
}

/* ── 다시 넣기 ── */
async function restore(row: PopularExcluded): Promise<void> {
    if (restoringId.value !== null) return;
    const title = row.post?.title ?? row.postId;
    const ok = await confirm({ title: t('popular.restoreTitle'), message: t('popular.restoreMessage', { title }), confirmLabel: t('popular.restore') });
    if (!ok) return;
    restoringId.value = row.postId;
    try {
        await api.restore(row.postId);
        toast.success(t('popular.restored', { title }));
        await reload();
    } catch (e) {
        toast.error(errorOf(e).message, 8000);
    } finally {
        restoringId.value = null;
    }
}
</script>

<style scoped>
/* 기간 탭 — 고른 탭을 강조색으로 짚는다(닉네임 이미지 심사 탭과 같은 모양) */
.popular-tabs :deep(.p-togglebutton-checked) {
    background: var(--p-primary-color);
    color: var(--p-primary-contrast-color);
    border-color: var(--p-primary-color);
}

.popular-tabs :deep(.p-togglebutton-content) {
    background: transparent;
    box-shadow: none;
}
</style>
