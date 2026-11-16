<template>
    <!-- 포인트 상점(`/point-shop`)과 같은 틀 — 바깥 화면 카드 하나에 제목 · 안내·잔액 줄(panel-card) · 번호 조건(선택) · 뽑기 단추 · 방금 뽑은 번호 · 내 번호 · 안내 문구 -->
    <div class="content-card flex flex-col gap-4">
        <h1 class="text-lg font-bold">{{ t('pointGame.lottoTitle') }}</h1>

        <!-- 사업자 전용 계정(H-2) — 포인트가 없다 -->
        <EmptyState v-if="businessAccount" :title="t('point.businessTitle')" :description="t('point.businessText')" icon="pi pi-wallet" data-testid="lotto-business" />

        <div v-else-if="status === 'loading'" class="flex flex-col gap-3" role="status" :aria-label="t('pointGame.loading')" data-testid="lotto-loading">
            <div class="h-24 animate-pulse rounded-xl bg-surface-200 dark:bg-surface-800" />
            <div class="h-40 animate-pulse rounded-xl bg-surface-200 dark:bg-surface-800" />
        </div>
        <ErrorState v-else-if="status === 'error' || !data" :title="t('pointGame.loadFailed')" :description="loadError" retry data-testid="lotto-error" @retry="load" />

        <template v-else>
            <!-- 무엇을 하는 곳인지 · 적립 잔액 · 당첨 확인 링크(관리자 설정 — 새 탭) -->
            <section class="flex flex-col gap-3 panel-card p-4" :aria-label="t('pointGame.myStatus')" data-testid="lotto-status">
                <p class="m-0 text-sm leading-relaxed">{{ t('pointGame.lottoLead', { site: siteLabel }) }}</p>
                <div class="flex flex-wrap items-end justify-between gap-3">
                    <div>
                        <p class="m-0 text-xs font-semibold text-muted-color">{{ t('pointGame.freeBalance') }}</p>
                        <p class="m-0 mt-0.5 text-2xl font-bold tabular-nums" data-testid="lotto-balance">{{ UtilPoint.points(data.freeBalance) }}</p>
                    </div>
                    <div class="flex flex-wrap gap-2">
                        <Button
                            v-if="checkLink"
                            as="a"
                            :href="checkLink.url"
                            target="_blank"
                            rel="noopener noreferrer"
                            :label="t('pointGame.checkLink', { provider: checkLink.providerName })"
                            :aria-label="t('pointGame.checkLinkAria', { provider: checkLink.providerName })"
                            icon="pi pi-external-link"
                            icon-pos="right"
                            size="small"
                            severity="secondary"
                            variant="outlined"
                            data-testid="lotto-check-link"
                        />
                        <Button as="router-link" :to="UtilPointGame.ROULETTE_PATH" :label="t('pointGame.rouletteTitle')" icon="pi pi-sync" size="small" severity="secondary" variant="outlined" data-testid="lotto-to-roulette" />
                    </div>
                </div>
                <p class="m-0 text-xs text-muted-color">{{ t('pointGame.freeOnly') }}</p>
            </section>

            <!-- 포인트 동결(기능 제한) — 포인트를 쓸 수 없어 뽑기를 끈다(서버도 403 MEMBER_RESTRICTED — 공개 QA P-5) -->
            <FeatureRestrictionNotice v-if="freeze" :restriction="freeze" />

            <!-- 뽑는 방법 — 자동(6개 모두 무작위) · 반자동(고정 1~5개 + 나머지 무작위, 제외는 선택). 반자동일 때만 번호판이 열린다 -->
            <section class="flex flex-col gap-3" :aria-labelledby="`${uid}-method`" data-testid="lotto-method">
                <div class="flex flex-wrap items-center gap-3">
                    <h2 :id="`${uid}-method`" class="m-0 text-sm font-semibold">{{ t('pointGame.methodLabel') }}</h2>
                    <SelectButton v-model="method" :options="methodOptions" option-label="label" option-value="value" :allow-empty="false" :aria-labelledby="`${uid}-method`" data-testid="lotto-method-select" />
                </div>
                <p class="m-0 text-xs text-muted-color">{{ method === 'AUTO' ? t('pointGame.methodAutoHint') : t('pointGame.methodSemiAutoHint', { max: UtilPointGame.LOTTO_FIXED_MAX }) }}</p>

                <div v-if="method === 'SEMI_AUTO'" class="flex flex-col gap-3" data-testid="lotto-conditions">
                    <div class="flex flex-wrap items-center justify-between gap-2">
                        <SelectButton v-model="pickMode" :options="modeOptions" option-label="label" option-value="value" :allow-empty="false" size="small" :aria-label="t('pointGame.pickModeLabel')" data-testid="lotto-pick-mode" />
                        <Button type="button" :label="t('pointGame.clearConditions')" icon="pi pi-replay" size="small" severity="secondary" variant="text" :disabled="fixed.length === 0 && excluded.length === 0" data-testid="lotto-clear" @click="clearConditions" />
                    </div>
                    <p class="m-0 text-xs text-muted-color">{{ pickMode === 'fixed' ? t('pointGame.pickFixedHint', { max: UtilPointGame.LOTTO_FIXED_MAX }) : t('pointGame.pickExcludedHint', { max: UtilPointGame.LOTTO_EXCLUDED_MAX }) }}</p>

                    <!-- 1~45 — 누르면 지금 고르는 쪽(고정/제외)에 넣고 뺀다 -->
                    <div role="group" :aria-label="t('pointGame.numberGrid')" class="grid grid-cols-9 gap-1.5 max-sm:gap-1" data-testid="lotto-grid">
                        <button
                            v-for="n in numbers"
                            :key="n"
                            type="button"
                            class="grid aspect-square min-w-0 place-items-center rounded-full text-xs font-bold tabular-nums focus-visible:outline-2 focus-visible:outline-offset-1 focus-visible:outline-primary sm:text-sm"
                            :class="cellClass(n)"
                            :aria-pressed="pickMode === 'fixed' ? fixed.includes(n) : excluded.includes(n)"
                            :aria-label="cellLabel(n)"
                            @click="toggle(n)"
                        >
                            {{ n }}
                        </button>
                    </div>
                    <div class="flex flex-wrap gap-x-4 gap-y-1 text-xs text-muted-color">
                        <span>{{ t('pointGame.fixedCount', { count: fixed.length, max: UtilPointGame.LOTTO_FIXED_MAX }) }}</span>
                        <span>{{ t('pointGame.excludedCount', { count: excluded.length, max: UtilPointGame.LOTTO_EXCLUDED_MAX }) }}</span>
                    </div>
                    <p v-if="problemText" class="m-0 text-xs text-red-600 dark:text-red-400" role="alert">{{ problemText }}</p>
                </div>
            </section>

            <!-- 뽑기 — 1세트 · 묶음(5세트) -->
            <section class="flex flex-col gap-2" :aria-label="t('pointGame.drawLabel')">
                <div class="grid grid-cols-2 gap-2">
                    <Button
                        type="button"
                        :label="t('pointGame.drawOne', { points: UtilPoint.points(data.setCost) })"
                        icon="pi pi-ticket"
                        :loading="drawing === 1"
                        :disabled="!canDraw(1)"
                        data-testid="lotto-draw-one"
                        @click="draw(1)"
                    />
                    <Button
                        type="button"
                        :label="t('pointGame.drawBundle', { count: data.bundleSize, points: UtilPoint.points(data.bundleCost) })"
                        icon="pi pi-ticket"
                        :loading="drawing === data.bundleSize"
                        :disabled="!canDraw(data.bundleSize)"
                        data-testid="lotto-draw-bundle"
                        @click="draw(data.bundleSize)"
                    />
                </div>
                <p v-if="blockedText" class="m-0 text-xs text-muted-color" data-testid="lotto-blocked">{{ blockedText }}</p>
            </section>

            <!-- 방금 뽑은 번호 -->
            <section v-if="latest.length" class="flex flex-col gap-2 panel-card p-4" :aria-labelledby="`${uid}-latest`" aria-live="polite" data-testid="lotto-latest">
                <h2 :id="`${uid}-latest`" class="m-0 text-sm font-semibold">{{ t('pointGame.latestTitle') }}</h2>
                <ul class="m-0 flex list-none flex-col gap-2 p-0">
                    <li v-for="(d, i) in latest" :key="d.id" class="flex flex-wrap items-center gap-x-3 gap-y-1.5">
                        <span class="w-5 text-xs font-semibold text-muted-color tabular-nums">{{ setLetter(i) }}</span>
                        <span class="flex gap-1.5" :aria-label="t('pointGame.numbersAria', { numbers: UtilPointGame.copyText(d.numbers) })" role="img">
                            <span v-for="n in d.numbers" :key="n" class="grid size-9 place-items-center rounded-full text-sm font-bold tabular-nums shadow-sm max-sm:size-8" :class="UtilPointGame.ballClass(n)" aria-hidden="true">{{ n }}</span>
                        </span>
                        <Button type="button" icon="pi pi-copy" size="small" severity="secondary" variant="text" rounded :aria-label="t('pointGame.copyAria', { numbers: UtilPointGame.copyText(d.numbers) })" class="ml-auto" @click="copy(d.numbers)" />
                    </li>
                </ul>
            </section>

            <!-- 내 번호 — 회원별로 남는다(보관 한도). 최신이 위 -->
            <section class="flex flex-col gap-2" :aria-labelledby="`${uid}-mine`" data-testid="lotto-mine">
                <div class="flex flex-wrap items-baseline justify-between gap-2">
                    <h2 :id="`${uid}-mine`" class="m-0 text-sm font-semibold">{{ t('pointGame.myNumbers') }}</h2>
                    <span class="text-xs tabular-nums text-muted-color" data-testid="lotto-stored">{{ t('pointGame.storedCount', { count: data.draws.length, max: data.storageLimit }) }}</span>
                </div>
                <EmptyState v-if="data.draws.length === 0" :title="t('pointGame.myNumbersEmpty')" :description="t('pointGame.myNumbersEmptyText')" icon="pi pi-ticket" data-testid="lotto-mine-empty" />
                <ul v-else class="m-0 flex list-none flex-col p-0">
                    <li v-for="d in data.draws" :key="d.id" class="flex flex-wrap items-center gap-x-3 gap-y-1 border-b border-hairline py-2 last:border-b-0" data-testid="lotto-mine-row">
                        <span class="flex gap-1" role="img" :aria-label="t('pointGame.numbersAria', { numbers: UtilPointGame.copyText(d.numbers) })">
                            <span v-for="n in d.numbers" :key="n" class="grid size-7 place-items-center rounded-full text-xs font-bold tabular-nums" :class="UtilPointGame.ballClass(n)" aria-hidden="true">{{ n }}</span>
                        </span>
                        <Tag :value="methodLabel(d.mode)" severity="secondary" class="text-xs" data-testid="lotto-mine-method" />
                        <span class="text-xs tabular-nums text-muted-color">{{ time.full(d.drawnAt) }}</span>
                        <span class="ml-auto flex gap-0.5">
                            <Button type="button" icon="pi pi-copy" size="small" severity="secondary" variant="text" rounded :aria-label="t('pointGame.copyAria', { numbers: UtilPointGame.copyText(d.numbers) })" @click="copy(d.numbers)" />
                            <Button
                                type="button"
                                icon="pi pi-trash"
                                size="small"
                                severity="secondary"
                                variant="text"
                                rounded
                                :loading="removing === d.id"
                                :aria-label="t('pointGame.removeAria', { numbers: UtilPointGame.copyText(d.numbers) })"
                                data-testid="lotto-remove"
                                @click="remove(d)"
                            />
                        </span>
                    </li>
                </ul>
            </section>

            <ul class="m-0 flex list-none flex-col gap-1.5 p-0 text-xs leading-relaxed text-muted-color">
                <li class="flex items-start gap-2"><i class="pi pi-info-circle mt-0.5 text-xs" aria-hidden="true" /><span>{{ t('pointGame.noteRandom') }}</span></li>
                <li class="flex items-start gap-2"><i class="pi pi-info-circle mt-0.5 text-xs" aria-hidden="true" /><span>{{ t('pointGame.noteNoWinning', { site: siteLabel }) }}</span></li>
                <li class="flex items-start gap-2"><i class="pi pi-info-circle mt-0.5 text-xs" aria-hidden="true" /><span>{{ t('pointGame.noteStorage', { max: data.storageLimit }) }}</span></li>
                <li class="flex items-start gap-2"><i class="pi pi-info-circle mt-0.5 text-xs" aria-hidden="true" /><span>{{ t('pointGame.noteAge') }}</span></li>
            </ul>
        </template>
    </div>
</template>

<script setup lang="ts">
import type { LottoDraw, LottoPickMode, LottoStatus } from '~/composables/pointgame/api/usePointGameApi.types';
import { ApiError } from '~/composables/common/useApiClient';
import UtilBusiness from '~/utils/util-business';
import UtilPoint from '~/utils/util-point';
import UtilPointGame from '~/utils/util-point-game';
import UtilPointGameMessages from '~/utils/util-point-game-messages';

/**
 * 로또 번호 뽑기 `/games/lotto` — 로그인 필요. 회원이 실제 로또를 사기 전에 여기서 번호를 뽑아 그 번호로 산다. **사이트는 당첨 번호를 만들거나 자동 비교하지 않는다**(당첨 인증 게시판도 없다).
 * 1~45 중 6개 · 1세트 20P · 5세트 한 번에 80P · 적립 포인트만. 뽑는 방법: 자동(6개 모두 무작위, 기본) · 반자동(고정 번호 1~5개 필수 + 제외 번호 39개까지 선택, 나머지 무작위 — 사용자 요구 2026-11-16).
 * 내 번호 줄마다 자동/반자동 표시.
 * 뽑은 번호는 회원별로 남는다(「내 번호」 — 뽑은 날짜·번호, 한 줄 지우기, 복사, 보관 한도는 서버 값 `storageLimit` — 50세트).
 * 「당첨 번호 확인」 링크 하나 — 사업자 이름·주소는 관리자 설정값(`checkLink`, 기본 「동행복권」 `https://dhlottery.co.kr`), 새 탭. 공식 로고·공식처럼 보이는 표현은 쓰지 않는다.
 * 서버 `usePointGameApi`(`/api/me/point-games/lotto…`). 400(조건 — 서로 다른 N세트를 못 만들면 `fields.excluded`)은 칸 문구를 알림으로, 409 는 상태를 다시 받는다.
 */
definePageMeta({ middleware: 'auth', meMenu: true });
const { t } = useI18n();
const { siteLabel } = useSiteName();
useSeoMeta({ title: () => t('pointGame.lottoTitle'), description: () => t('pointGame.lottoSeo', { site: siteLabel.value }), robots: 'noindex' });

const uid = useId();
const api = usePointGameApi();
const toast = useAppToast();
const time = useDisplayTime();
const points = usePointSummary();
const { copy: copyText } = useCopyText();
const { confirm } = useConfirmDialog();
const { user } = useAuth();

const businessAccount = computed(() => UtilBusiness.isBusinessAccount(user.value?.role));

const data = ref<LottoStatus | null>(null);
const status = ref<'loading' | 'ready' | 'error'>('loading');
const loadError = ref('');

/** 지금 뽑는 세트 수(단추 하나만 돈다) — 0 이면 아님 */
const drawing = ref(0);
/** 방금 뽑은 세트 — 회원이 바뀌면 비운다(아래 `watch` 가 쓰므로 먼저 둔다) */
const latest = ref<LottoDraw[]>([]);

/** 당첨 확인 링크 — `https://` 주소일 때만 그린다 */
const checkLink = computed(() => (data.value?.checkLink && UtilPointGame.safeLink(data.value.checkLink.url) ? data.value.checkLink : null));

async function load(): Promise<void> {
    status.value = 'loading';
    loadError.value = '';
    try {
        data.value = await api.lotto();
        status.value = 'ready';
    } catch (e) {
        loadError.value = UtilPointGameMessages.error(t, e);
        status.value = 'error';
    }
}

watch(
    () => user.value?.id ?? null,
    (id) => {
        if (!id || businessAccount.value) return;
        latest.value = [];
        void load();
    },
    { immediate: true },
);

/* ── 뽑는 방법 · 번호 조건(반자동) ── */
const numbers = UtilPointGame.lottoNumbers();
const method = ref<LottoPickMode>('AUTO');
const methodOptions = computed(() => [
    { value: 'AUTO', label: t('pointGame.methodAuto') },
    { value: 'SEMI_AUTO', label: t('pointGame.methodSemiAuto') },
]);
function methodLabel(mode: LottoPickMode): string {
    return mode === 'SEMI_AUTO' ? t('pointGame.methodSemiAuto') : t('pointGame.methodAuto');
}
const pickMode = ref<'fixed' | 'excluded'>('fixed');
const fixed = ref<number[]>([]);
const excluded = ref<number[]>([]);
const modeOptions = computed(() => [
    { value: 'fixed', label: t('pointGame.modeFixed') },
    { value: 'excluded', label: t('pointGame.modeExcluded') },
]);

const problem = computed(() => UtilPointGame.pickProblem(method.value, fixed.value, excluded.value));
const problemText = computed(() => (problem.value ? UtilPointGameMessages.pickProblem(t, problem.value) : ''));

/** 지금 고르는 쪽에 넣거나 뺀다 — 다른 쪽에 있던 번호면 옮긴다. 한도에 닿으면 알린다 */
function toggle(n: number): void {
    const mine = pickMode.value === 'fixed' ? fixed : excluded;
    const other = pickMode.value === 'fixed' ? excluded : fixed;
    if (mine.value.includes(n)) {
        mine.value = mine.value.filter((x) => x !== n);
        return;
    }
    const max = pickMode.value === 'fixed' ? UtilPointGame.LOTTO_FIXED_MAX : UtilPointGame.LOTTO_EXCLUDED_MAX;
    if (mine.value.length >= max) {
        toast.show(pickMode.value === 'fixed' ? t('pointGame.fixedFull', { max }) : t('pointGame.excludedFull', { max }));
        return;
    }
    other.value = other.value.filter((x) => x !== n);
    mine.value = [...mine.value, n].sort((a, b) => a - b);
}

function clearConditions(): void {
    fixed.value = [];
    excluded.value = [];
}

function cellClass(n: number): string {
    if (fixed.value.includes(n)) return `${UtilPointGame.ballClass(n)} ring-2 ring-offset-1 ring-(--p-text-color) ring-offset-(--p-content-background)`;
    if (excluded.value.includes(n)) return 'bg-surface-100 text-muted-color line-through opacity-60 dark:bg-surface-800';
    return 'border border-hairline hover:bg-surface-100 dark:hover:bg-surface-800';
}

function cellLabel(n: number): string {
    if (fixed.value.includes(n)) return t('pointGame.cellFixed', { n });
    if (excluded.value.includes(n)) return t('pointGame.cellExcluded', { n });
    return String(n);
}

/* ── 뽑기 ── */
function costOf(sets: number): number {
    if (!data.value) return Infinity;
    return sets === 1 ? data.value.setCost : data.value.bundleCost;
}

/** 포인트 동결 — 포인트 요약의 `restriction`(요약이 없으면 받아 온다) */
const freeze = computed(() => UtilPoint.freeze(points.summary.value));
onMounted(() => {
    if (!points.summary.value) void points.load();
});

function canDraw(sets: number): boolean {
    if (!data.value || drawing.value || problem.value || freeze.value) return false;
    return data.value.freeBalance >= costOf(sets) && data.value.draws.length + sets <= data.value.storageLimit;
}

/** 단추가 꺼진 까닭 한 줄 — 보관함 가득 · 포인트 모자람(1세트 기준) */
const blockedText = computed(() => {
    const d = data.value;
    if (!d || drawing.value) return '';
    if (d.draws.length + 1 > d.storageLimit) return t('pointGame.storageFull', { max: d.storageLimit });
    if (d.freeBalance < d.setCost) return t('pointGame.insufficient', { points: UtilPoint.points(d.setCost) });
    if (d.draws.length + d.bundleSize > d.storageLimit) return t('pointGame.storageAlmostFull', { count: d.storageLimit - d.draws.length });
    return '';
});

/** A · B · C … — 방금 뽑은 세트의 줄 이름(로또 용지의 차례처럼) */
function setLetter(i: number): string {
    return String.fromCharCode(65 + i);
}

async function draw(sets: number): Promise<void> {
    if (!canDraw(sets) || !data.value) return;
    drawing.value = sets;
    try {
        // 자동은 조건을 보내지 않는다(반자동에서 골라 둔 번호는 화면에 남겨 두고 다시 반자동으로 오면 그대로 쓴다)
        const semi = method.value === 'SEMI_AUTO';
        const outcome = await api.draw({ sets, mode: method.value, fixed: semi ? fixed.value : [], excluded: semi ? excluded.value : [] });
        latest.value = outcome.draws;
        if (data.value) data.value = { ...data.value, freeBalance: outcome.freeBalance, draws: [...outcome.draws, ...data.value.draws] };
        // 헤더·포인트 화면의 잔액도 맞춘다
        void points.load();
    } catch (e) {
        toast.error(UtilPointGameMessages.error(t, e), 6000);
        // 보관함·잔액이 다른 곳에서 바뀌었다 — 지금 상태를 다시 받는다
        if (e instanceof ApiError && (e.code === 'LOTTO_STORAGE_FULL' || e.code === 'POINT_INSUFFICIENT')) {
            try {
                data.value = await api.lotto();
            } catch {
                // 받아 둔 값을 그대로 둔다
            }
        }
    } finally {
        drawing.value = 0;
    }
}

/* ── 내 번호 ── */
async function copy(nums: readonly number[]): Promise<void> {
    if (await copyText(UtilPointGame.copyText(nums))) toast.success(t('pointGame.copied'));
    else toast.error(t('pointGame.copyFailed'));
}

const removing = ref<string | null>(null);
async function remove(d: LottoDraw): Promise<void> {
    if (removing.value) return;
    const ok = await confirm({ title: t('pointGame.removeTitle'), message: t('pointGame.removeMessage', { numbers: UtilPointGame.copyText(d.numbers) }), confirmLabel: t('common.delete'), cancelLabel: t('common.cancel'), tone: 'danger' });
    if (!ok) return;
    removing.value = d.id;
    try {
        await api.removeDraw(d.id);
        if (data.value) data.value = { ...data.value, draws: data.value.draws.filter((x) => x.id !== d.id) };
        latest.value = latest.value.filter((x) => x.id !== d.id);
        toast.success(t('pointGame.removed'));
    } catch (e) {
        toast.error(UtilPointGameMessages.error(t, e), 6000);
        // 내 줄이 아니다(다른 계정으로 바뀌었거나 목록이 낡았다) — 지금 목록을 다시 받는다
        if (e instanceof ApiError && e.code === 'LOTTO_DRAW_NOT_FOUND') {
            try {
                data.value = await api.lotto();
            } catch {
                // 받아 둔 값을 그대로 둔다
            }
        }
    } finally {
        removing.value = null;
    }
}
</script>
