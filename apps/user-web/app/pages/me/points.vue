<template>
    <div class="mx-auto flex max-w-2xl flex-col gap-4">
        <h1 class="text-lg font-bold">{{ t('point.title') }}</h1>

        <!-- 사업자 전용 계정(H-2) — 포인트가 없다(서버 `403 BUSINESS_ACCOUNT_RESTRICTED`) -->
        <EmptyState v-if="businessAccount" :title="t('point.businessTitle')" :description="t('point.businessText')" icon="pi pi-wallet" data-testid="points-business" />

        <template v-else-if="user">
            <!-- 보유 포인트 · 오늘 적립 — 실제 `GET /api/me/points` -->
            <section class="rounded-xl border border-surface bg-surface-0 p-4 dark:bg-surface-900" :aria-labelledby="`${uid}-balance`" data-testid="points-summary">
                <h2 :id="`${uid}-balance`" class="text-sm font-semibold text-muted-color">{{ t('point.balance') }}</h2>

                <div v-if="!summary && (summaryStatus === 'loading' || summaryStatus === 'idle')" class="mt-2 flex flex-col gap-3" role="status" :aria-label="t('point.loading')" data-testid="points-loading">
                    <div class="h-9 w-40 animate-pulse rounded bg-surface-200 dark:bg-surface-800" />
                    <div v-for="n in 3" :key="n" class="h-8 animate-pulse rounded bg-surface-200 dark:bg-surface-800" />
                </div>
                <ErrorState v-else-if="!summary" :title="t('point.loadFailed')" retry class="py-6!" data-testid="points-error" @retry="points.load" />
                <template v-else>
                    <p class="mt-1 text-3xl font-bold tabular-nums" data-testid="points-balance">{{ UtilPoint.points(summary.balance) }}</p>

                    <h3 class="mt-4 text-xs font-semibold text-muted-color">{{ t('point.todayTitle') }}</h3>
                    <ul class="m-0 mt-2 flex list-none flex-col gap-3 p-0" data-testid="points-today">
                        <li v-for="row in todayRows" :key="row.key" class="text-sm">
                            <div class="flex items-baseline justify-between gap-2">
                                <span>{{ t(row.labelKey) }}</span>
                                <span v-if="row.value.cap > 0" class="tabular-nums text-muted-color">
                                    <b class="text-color">{{ t('point.countOfCap', { count: row.value.count, cap: row.value.cap }) }}</b>
                                    <template v-if="UtilPoint.remaining(row.value) === 0"> · {{ t('point.capReached') }}</template>
                                </span>
                                <span v-else class="text-xs text-muted-color">{{ t('point.ruleOff') }}</span>
                            </div>
                            <div
                                v-if="row.value.cap > 0"
                                class="mt-1 h-1.5 overflow-hidden rounded-full bg-surface-200 dark:bg-surface-800"
                                role="progressbar"
                                :aria-label="t(row.labelKey)"
                                :aria-valuenow="row.value.count"
                                aria-valuemin="0"
                                :aria-valuemax="row.value.cap"
                            >
                                <div class="h-full rounded-full bg-primary" :style="{ width: `${UtilPoint.progress(row.value)}%` }" />
                            </div>
                        </li>
                    </ul>
                    <p class="mt-3 text-xs text-muted-color">{{ t('point.dayBasis') }}</p>
                </template>
            </section>

            <!-- 적립 규칙 요약 — 운영자가 정한 값(최대 15초 뒤 반영). 지급 지점이 없는 규칙(출석·인기글·교환권)은 보이지 않는다 -->
            <section v-if="summary" class="rounded-xl border border-surface bg-surface-0 p-4 dark:bg-surface-900" :aria-labelledby="`${uid}-rules`" data-testid="points-rules">
                <h2 :id="`${uid}-rules`" class="text-sm font-semibold">{{ t('point.rulesTitle') }}</h2>
                <ul class="m-0 mt-2 flex list-none flex-col gap-1.5 p-0 text-sm">
                    <li v-for="rule in ruleLines" :key="rule.key" class="flex items-baseline justify-between gap-3">
                        <span>{{ rule.label }}</span>
                        <span class="shrink-0 text-right text-muted-color">{{ rule.value }}</span>
                    </li>
                </ul>
                <p class="mt-3 text-xs leading-relaxed text-muted-color">{{ t('point.rulesRevokeNote') }}</p>
            </section>

            <!-- 내역 — `GET /api/me/points/ledger`. 거르기는 전체·적립·차감(교환 기능이 없어 「사용」은 두지 않는다) -->
            <section class="rounded-xl border border-surface bg-surface-0 dark:bg-surface-900" :aria-labelledby="`${uid}-ledger`" data-testid="points-ledger">
                <div class="flex flex-wrap items-center justify-between gap-2 px-4 pt-4">
                    <h2 :id="`${uid}-ledger`" class="text-sm font-semibold">{{ t('point.history') }}</h2>
                    <SelectButton
                        v-model="kind"
                        :options="kindOptions"
                        option-label="label"
                        option-value="value"
                        :allow-empty="false"
                        size="small"
                        :aria-label="t('point.kindFilter')"
                        data-testid="points-kind"
                    />
                </div>

                <div class="mt-2" :aria-busy="ledgerStatus === 'loading'">
                    <div v-if="ledgerStatus === 'loading'" aria-hidden="true" data-testid="ledger-loading">
                        <div v-for="n in 5" :key="n" class="flex items-center gap-3 border-t border-surface px-4 py-3">
                            <div class="flex-1">
                                <div class="h-3.5 w-1/3 animate-pulse rounded bg-surface-200 dark:bg-surface-800" />
                                <div class="mt-2 h-3 w-1/2 animate-pulse rounded bg-surface-200 dark:bg-surface-800" />
                            </div>
                            <div class="h-4 w-14 animate-pulse rounded bg-surface-200 dark:bg-surface-800" />
                        </div>
                    </div>
                    <ErrorState v-else-if="ledgerStatus === 'error'" :title="t('point.historyLoadFailed')" :description="ledgerError" retry data-testid="ledger-error" @retry="loadLedger(true)" />
                    <EmptyState v-else-if="ledger.length === 0" :title="t(kind === 'ALL' ? 'point.historyEmpty' : 'point.historyEmptyFiltered')" icon="pi pi-wallet" data-testid="ledger-empty" />
                    <template v-else>
                        <ul class="m-0 list-none p-0" data-testid="ledger-list">
                            <li v-for="row in ledger" :key="row.entryId" class="flex items-start gap-3 border-t border-surface px-4 py-3" data-testid="ledger-row">
                                <div class="min-w-0 flex-1 text-sm">
                                    <p class="font-medium">{{ row.reason }}</p>
                                    <p v-if="row.note" class="mt-0.5 break-words text-xs text-muted-color">{{ row.note }}</p>
                                    <p v-if="row.target" class="mt-0.5 truncate text-xs text-muted-color">
                                        <NuxtLink v-if="row.target.available && row.target.title" :to="`/posts/${row.target.postId}`" class="hover:underline focus-visible:outline-2 focus-visible:outline-primary">{{ row.target.title }}</NuxtLink>
                                        <template v-else>{{ t('point.targetUnavailable') }}</template>
                                    </p>
                                    <time class="mt-0.5 block text-xs text-muted-color" :datetime="row.createdAt" :title="time.full(row.createdAt)">{{ time.listLabel(row.createdAt) }}</time>
                                </div>
                                <div class="shrink-0 text-right">
                                    <p class="text-sm font-semibold tabular-nums" :class="row.delta < 0 ? 'text-red-600 dark:text-red-400' : 'text-green-700 dark:text-green-400'">{{ UtilPoint.signed(row.delta) }}</p>
                                    <p class="mt-0.5 text-xs tabular-nums text-muted-color">{{ t('point.balanceAfter', { points: UtilPoint.points(row.balanceAfter) }) }}</p>
                                </div>
                            </li>
                        </ul>
                        <div v-if="hasNext" class="border-t border-surface p-3 text-center">
                            <Button type="button" :label="t('common.more')" size="small" severity="secondary" variant="outlined" :loading="loadingMore" data-testid="ledger-more" @click="loadLedger(false)" />
                        </div>
                    </template>
                </div>
            </section>
        </template>
    </div>
</template>

<script setup lang="ts">
import type { MyPointLedgerItem, MyPointLedgerKind, PointCount } from '~/composables/point/api/usePointApi.types';
import UtilAuthMessages from '~/utils/util-auth-messages';
import UtilBusiness from '~/utils/util-business';
import UtilPoint from '~/utils/util-point';

/**
 * 포인트 `/me/points` — 로그인 필요(`auth` 미들웨어). 헤더 프로필 메뉴의 「포인트」·햄버거의 포인트 줄·내 정보의 「포인트 내역」이 온다.
 * 보유 포인트·오늘 적립(횟수/상한)·적립 규칙은 `GET /api/me/points`, 내역 목록은 `GET /api/me/points/ledger`(커서 더 보기) — 둘 다 서버 연결됨.
 * 출석 기능이 없으면(`attendanceAvailable=false`, 지금은 늘) 출석 줄·출석 규칙을 그리지 않는다. 인기글 보상·닉네임 변경권도 지급 지점이 없어 그리지 않는다.
 */
definePageMeta({ middleware: 'auth' });
const { t } = useI18n();
const { siteLabel } = useSiteName();
useSeoMeta({ title: () => t('point.title'), description: () => t('point.seoDescription', { site: siteLabel.value }), robots: 'noindex' });

const uid = useId();
const time = useDisplayTime();
const { user } = useAuth();
const api = usePointApi();
const toast = useAppToast();
const points = usePointSummary();
const { summary, status: summaryStatus } = points;

const businessAccount = computed(() => UtilBusiness.isBusinessAccount(user.value?.role));

/** 오늘 적립 줄 — 출석은 기능이 있을 때만(지금은 없다) */
const todayRows = computed(() => {
    const today = summary.value?.today;
    if (!today) return [];
    const rows: { key: string; labelKey: string; value: PointCount }[] = [
        { key: 'post', labelKey: 'point.todayPost', value: today.post },
        { key: 'comment', labelKey: 'point.todayComment', value: today.comment },
        { key: 'likeBonus', labelKey: 'point.todayLikeBonus', value: today.likeBonus },
    ];
    return rows;
});

/** 적립 규칙 한 줄씩 — 값이 0 이거나 하루 상한이 0 이면 「지급하지 않아요」 */
const ruleLines = computed(() => {
    const s = summary.value;
    if (!s) return [];
    const r = s.rules;
    const perDay = (amount: number, cap: number) => (amount > 0 && cap > 0 ? t('point.rulePerDay', { amount: UtilPoint.number(amount), cap }) : t('point.ruleOff'));
    const lines = [
        { key: 'post', label: t('point.rulePost'), value: perDay(r.post, s.today.post.cap) },
        { key: 'comment', label: t('point.ruleComment'), value: perDay(r.comment, s.today.comment.cap) },
        {
            key: 'likeBonus',
            label: t('point.ruleLikeBonus', { threshold: r.likeBonus.threshold }),
            value: perDay(r.likeBonus.points, r.likeBonus.dailyCap),
        },
    ];
    if (s.attendanceAvailable) lines.unshift({ key: 'attendance', label: t('point.ruleAttendance'), value: r.attendance > 0 ? `+${UtilPoint.points(r.attendance)}` : t('point.ruleOff') });
    return lines;
});

// ── 내역 ─────────────────────────────────────────────
type KindFilter = 'ALL' | Exclude<MyPointLedgerKind, 'USE'>;
const kind = ref<KindFilter>('ALL');
const kindOptions = computed(() => [
    { value: 'ALL', label: t('common.all') },
    { value: 'EARN', label: t('point.kindEarn') },
    { value: 'REVOKE', label: t('point.kindRevoke') },
]);

const ledger = ref<MyPointLedgerItem[]>([]);
const ledgerStatus = ref<'loading' | 'ready' | 'error'>('loading');
const ledgerError = ref('');
const hasNext = ref(false);
const nextCursor = ref<string | null>(null);
const loadingMore = ref(false);
let ledgerSeq = 0;

/** 첫 쪽부터(`reset`) 또는 다음 쪽을 받는다. 거르기를 바꾸면 앞 요청의 늦은 응답은 버린다. 다음 쪽 실패는 토스트로 알리고 받은 줄은 그대로 둔다 */
async function loadLedger(reset: boolean) {
    const mine = ++ledgerSeq;
    if (reset) {
        ledgerStatus.value = 'loading';
        ledgerError.value = '';
        nextCursor.value = null;
    } else {
        loadingMore.value = true;
    }
    try {
        const page = await api.ledger({ kind: kind.value === 'ALL' ? undefined : kind.value, cursor: reset ? undefined : (nextCursor.value ?? undefined) });
        if (mine !== ledgerSeq) return;
        ledger.value = reset ? page.items : [...ledger.value, ...page.items];
        hasNext.value = page.hasNext;
        nextCursor.value = page.nextCursor;
        ledgerStatus.value = 'ready';
    } catch (e) {
        if (mine !== ledgerSeq) return;
        const message = e instanceof ApiError ? e.message : UtilAuthMessages.fallback(t, 0, 'NETWORK');
        if (reset) {
            ledgerError.value = message;
            ledgerStatus.value = 'error';
        } else {
            toast.error(message, 8000);
        }
    } finally {
        if (mine === ledgerSeq) loadingMore.value = false;
    }
}

watch(kind, () => void loadLedger(true));
onBeforeUnmount(() => ledgerSeq++);

// 로그인한 사람이 정해지면 요약·내역을 받는다(요약은 받아 둔 값이 있으면 그대로 보이면서 바꾼다)
watch(
    () => user.value?.id ?? null,
    (id) => {
        if (!id || businessAccount.value) return;
        void points.load();
        void loadLedger(true);
    },
    { immediate: true },
);
</script>
