<template>
    <!-- 포인트 상점(`/point-shop`)과 같은 틀 — 바깥 화면 카드 하나에 제목 · 잔액 줄(panel-card) · 판 · 확률표 · 안내 문구. 로그인 회원만(내 정보 메뉴 틀 `meMenu` 안) -->
    <div class="content-card flex flex-col gap-4">
        <h1 class="text-lg font-bold">{{ t('pointGame.rouletteTitle') }}</h1>

        <!-- 사업자 전용 계정(H-2) — 포인트가 없다 -->
        <EmptyState v-if="businessAccount" :title="t('point.businessTitle')" :description="t('point.businessText')" icon="pi pi-wallet" data-testid="roulette-business" />

        <div v-else-if="status === 'loading'" class="flex flex-col gap-3" role="status" :aria-label="t('pointGame.loading')" data-testid="roulette-loading">
            <div class="h-20 animate-pulse rounded-xl bg-surface-200 dark:bg-surface-800" />
            <div class="mx-auto aspect-square w-full max-w-72 animate-pulse rounded-full bg-surface-200 dark:bg-surface-800" />
        </div>
        <ErrorState v-else-if="status === 'error' || !data" :title="t('pointGame.loadFailed')" :description="loadError" retry data-testid="roulette-error" @retry="load" />

        <template v-else>
            <!-- 적립 잔액 · 오늘 남은 횟수 -->
            <section class="flex flex-wrap items-center justify-between gap-3 panel-card p-4" :aria-label="t('pointGame.myStatus')" data-testid="roulette-status">
                <div class="flex flex-wrap gap-x-8 gap-y-2">
                    <div>
                        <p class="m-0 text-xs font-semibold text-muted-color">{{ t('pointGame.freeBalance') }}</p>
                        <p class="m-0 mt-0.5 text-2xl font-bold tabular-nums" data-testid="roulette-balance">{{ UtilPoint.points(data.freeBalance) }}</p>
                    </div>
                    <div>
                        <p class="m-0 text-xs font-semibold text-muted-color">{{ t('pointGame.remainingToday') }}</p>
                        <p class="m-0 mt-0.5 text-2xl font-bold tabular-nums" data-testid="roulette-remaining">
                            {{ remaining }}<span class="text-sm font-semibold text-muted-color"> / {{ data.dailyLimit }}</span>
                        </p>
                    </div>
                </div>
                <Button as="router-link" :to="UtilPointGame.LOTTO_PATH" :label="t('pointGame.lottoTitle')" icon="pi pi-ticket" size="small" severity="secondary" variant="outlined" data-testid="roulette-to-lotto" />
                <p class="m-0 w-full text-xs text-muted-color">{{ t('pointGame.freeOnly') }}</p>
            </section>

            <!-- 판 — 칸 크기 = 실제 확률. 결과는 서버가 정하고 판은 그 칸에 멈출 뿐이다. 그림은 보조라 읽는 것은 아래 확률표다 -->
            <section class="flex flex-col items-center gap-4" :aria-labelledby="`${uid}-wheel`">
                <h2 :id="`${uid}-wheel`" class="sr-only">{{ t('pointGame.wheelLabel') }}</h2>
                <div class="relative w-full max-w-72 sm:max-w-80">
                    <!-- 바늘(맨 위, 돌지 않는다) -->
                    <svg viewBox="0 0 24 20" class="absolute -top-1 left-1/2 z-10 h-6 w-7 -translate-x-1/2 drop-shadow" aria-hidden="true">
                        <path d="M2 0 H22 L12 18 Z" class="fill-(--p-text-color)" />
                    </svg>
                    <div class="aspect-square w-full rounded-full" :style="wheelStyle" data-testid="roulette-wheel">
                        <svg viewBox="0 0 200 200" class="block h-full w-full" aria-hidden="true">
                            <circle cx="100" cy="100" r="99" class="fill-(--p-surface-0) stroke-(--p-surface-300) dark:fill-(--p-surface-900) dark:stroke-(--p-surface-600)" stroke-width="2" />
                            <path v-for="s in slices" :key="s.prize.key" :d="UtilPointGame.slicePath(100, 96, s.start, s.end)" :class="sliceFill(s.prize.key)" class="stroke-(--p-surface-0) dark:stroke-(--p-surface-900)" stroke-width="0.6" />
                            <!-- 글자는 넓은 칸에만(좁은 칸은 확률표가 알린다) — 칸 가운데 각도로 세운다 -->
                            <template v-for="s in slices" :key="`label-${s.prize.key}`">
                                <text
                                    v-if="s.end - s.start >= 14"
                                    :transform="labelTransform(s)"
                                    text-anchor="middle"
                                    dominant-baseline="middle"
                                    class="text-[9px] font-bold"
                                    :class="s.prize.points > 0 ? 'fill-[#1f2937]' : 'fill-(--p-text-color)'"
                                >
                                    {{ UtilPointGameMessages.prize(t, s.prize) }}
                                </text>
                            </template>
                            <circle cx="100" cy="100" r="16" class="fill-(--p-surface-0) stroke-(--p-surface-300) dark:fill-(--p-surface-900) dark:stroke-(--p-surface-600)" stroke-width="1.5" />
                        </svg>
                    </div>
                </div>

                <Button
                    type="button"
                    :label="t('pointGame.spin', { points: UtilPoint.points(data.cost) })"
                    icon="pi pi-sync"
                    :loading="spinning"
                    :disabled="!canSpin"
                    class="w-full max-w-72 sm:max-w-80"
                    data-testid="roulette-spin"
                    @click="spin"
                />

                <!-- 결과 · 막힌 까닭 — 읽어 주는 자리 하나 -->
                <div class="min-h-6 text-center" aria-live="polite" data-testid="roulette-result">
                    <p v-if="result && !spinning" class="m-0 text-base font-bold" :class="result.won > 0 ? 'text-primary' : 'text-muted-color'">
                        {{ result.won > 0 ? t('pointGame.won', { points: UtilPoint.points(result.won) }) : t('pointGame.missResult') }}
                    </p>
                    <p v-else-if="!spinning && remaining === 0" class="m-0 text-sm text-muted-color" data-testid="roulette-limit">{{ t('pointGame.limitReached', { count: data.dailyLimit }) }}</p>
                    <p v-else-if="!spinning && data.freeBalance < data.cost" class="m-0 text-sm text-muted-color" data-testid="roulette-insufficient">
                        {{ t('pointGame.insufficient', { points: UtilPoint.points(data.cost) }) }}
                        <NuxtLink to="/me/points" class="ml-1 whitespace-nowrap font-medium text-primary hover:underline">{{ t('pointGame.howToEarn') }} ›</NuxtLink>
                    </p>
                </div>
            </section>

            <!-- 확률표 — 판의 칸과 같은 색 점 -->
            <section class="flex flex-col gap-2" :aria-labelledby="`${uid}-odds`" data-testid="roulette-odds">
                <h2 :id="`${uid}-odds`" class="m-0 text-sm font-semibold">{{ t('pointGame.oddsTitle') }}</h2>
                <table class="w-full border-collapse text-sm">
                    <thead>
                        <tr class="border-b border-hairline text-left text-xs text-muted-color">
                            <th scope="col" class="py-2 font-semibold">{{ t('pointGame.oddsPrize') }}</th>
                            <th scope="col" class="py-2 text-right font-semibold">{{ t('pointGame.oddsChance') }}</th>
                        </tr>
                    </thead>
                    <tbody>
                        <tr v-for="p in data.prizes" :key="p.key" class="border-b border-hairline">
                            <td class="py-2">
                                <span class="inline-flex items-center gap-2">
                                    <svg viewBox="0 0 10 10" class="size-3 shrink-0" aria-hidden="true"><circle cx="5" cy="5" r="5" :class="sliceFill(p.key)" /></svg>
                                    {{ UtilPointGameMessages.prize(t, p) }}
                                </span>
                            </td>
                            <td class="py-2 text-right tabular-nums">{{ UtilPointGame.percent(p.chanceBp) }}%</td>
                        </tr>
                    </tbody>
                </table>
                <p class="m-0 text-xs leading-relaxed text-muted-color" data-testid="roulette-expected">
                    {{ t('pointGame.expectedReturn', { percent: expectedPercent, cost: UtilPoint.points(data.cost), points: expectedPoints }) }}
                </p>
            </section>

            <ul class="m-0 flex list-none flex-col gap-1.5 p-0 text-xs leading-relaxed text-muted-color">
                <li class="flex items-start gap-2"><i class="pi pi-info-circle mt-0.5 text-xs" aria-hidden="true" /><span>{{ t('pointGame.noteRouletteRule', { cost: UtilPoint.points(data.cost), count: data.dailyLimit }) }}</span></li>
                <li class="flex items-start gap-2"><i class="pi pi-info-circle mt-0.5 text-xs" aria-hidden="true" /><span>{{ t('pointGame.noteServerDecides') }}</span></li>
                <li class="flex items-start gap-2"><i class="pi pi-info-circle mt-0.5 text-xs" aria-hidden="true" /><span>{{ t('pointGame.noteNoCash') }}</span></li>
            </ul>
        </template>
    </div>
</template>

<script setup lang="ts">
import type { RouletteSpinResult, RouletteStatus } from '~/composables/pointgame/api/usePointGameApi.types';
import type { RouletteSlice } from '~/utils/util-point-game';
import { ApiError } from '~/composables/common/useApiClient';
import UtilBusiness from '~/utils/util-business';
import UtilPoint from '~/utils/util-point';
import UtilPointGame from '~/utils/util-point-game';
import UtilPointGameMessages from '~/utils/util-point-game-messages';

/**
 * 룰렛 `/games/roulette` — 로그인 필요. 적립 포인트를 쓰는 포인트 게임(사이트는 돈·실물 상금을 주지 않는다). 내 정보 메뉴 맨 위 칸·헤더 계정 메뉴가 온다.
 * 1회 50P · 하루 5회(한국 시간 자정에 다시 찬다) · 적립 포인트만. 판의 칸 크기 = 실제 확률(꽝 70% 면 판의 70%), 확률표와 평균 돌려받는 양(약 20%)을 공개한다.
 * 결과는 서버가 정한다(`POST …/roulette/spins`) — 화면은 받은 칸에 바늘이 오도록 판을 돌려 멈춘 뒤 결과·잔액·남은 횟수를 바꾼다.
 * 서버 `usePointGameApi`(`/api/me/point-games/roulette…`). 409(횟수·잔액)면 상태를 다시 받는다. 정지·포인트 동결은 403 문구를 알림으로.
 */
definePageMeta({ middleware: 'auth', meMenu: true });
const { t } = useI18n();
const { siteLabel } = useSiteName();
useSeoMeta({ title: () => t('pointGame.rouletteTitle'), description: () => t('pointGame.rouletteSeo', { site: siteLabel.value }), robots: 'noindex' });

const uid = useId();
const api = usePointGameApi();
const toast = useAppToast();
const points = usePointSummary();
const { user } = useAuth();
const reduceMotion = useMediaQuery('(prefers-reduced-motion: reduce)');

const businessAccount = computed(() => UtilBusiness.isBusinessAccount(user.value?.role));

const data = ref<RouletteStatus | null>(null);
const status = ref<'loading' | 'ready' | 'error'>('loading');
const loadError = ref('');

const slices = computed(() => (data.value ? UtilPointGame.slices(data.value.prizes) : []));
const remaining = computed(() => (data.value ? Math.max(0, data.value.dailyLimit - data.value.usedToday) : 0));
const canSpin = computed(() => Boolean(data.value) && !spinning.value && remaining.value > 0 && (data.value?.freeBalance ?? 0) >= (data.value?.cost ?? Infinity));
const expectedRatio = computed(() => (data.value ? UtilPointGame.expectedReturn(data.value.prizes, data.value.cost) : 0));
const expectedPercent = computed(() => Math.round(expectedRatio.value * 100));
const expectedPoints = computed(() => (data.value ? UtilPoint.number(Math.round(expectedRatio.value * data.value.cost * 10) / 10) : '0'));

async function load(): Promise<void> {
    status.value = 'loading';
    loadError.value = '';
    try {
        data.value = await api.roulette();
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
        void load();
    },
    { immediate: true },
);

/* ── 칸 색 — 꽝은 바탕과 같은 회색 계열(테마를 따라), 상품은 두 테마에서 같은 밝은 색(그 위 글자는 진한 색) ── */
const FILLS: Record<string, string> = {
    MISS: 'fill-(--p-surface-200) dark:fill-(--p-surface-700)',
    P10: 'fill-[#93c5fd]',
    P20: 'fill-[#6ee7b7]',
    P30: 'fill-[#fcd34d]',
    P50: 'fill-[#fdba74]',
    P100: 'fill-[#fca5a5]',
    P500: 'fill-[#c4b5fd]',
};
function sliceFill(key: string): string {
    return FILLS[key] ?? 'fill-[#d1d5db]';
}

/** 칸 글자 — 칸 가운데 각도, 반지름 62% 자리에 반지름 방향으로 눕힌다(왼쪽 반은 뒤집어 거꾸로 읽히지 않게) */
function labelTransform(s: RouletteSlice): string {
    const mid = (s.start + s.end) / 2;
    const [x, y] = UtilPointGame.point(100, 62, mid);
    return `translate(${x} ${y}) rotate(${mid < 180 ? mid - 90 : mid + 90})`;
}

/* ── 돌리기 ── */
const rotation = ref(0);
const spinning = ref(false);
const result = ref<RouletteSpinResult | null>(null);
const spinMs = computed(() => (reduceMotion.value ? 600 : 4200));
const wheelStyle = computed(() => ({
    transform: `rotate(${rotation.value}deg)`,
    transition: spinning.value ? `transform ${spinMs.value}ms cubic-bezier(0.15, 0.7, 0.1, 1)` : 'none',
}));

let timer: ReturnType<typeof setTimeout> | null = null;
onBeforeUnmount(() => {
    if (timer) clearTimeout(timer);
});

async function spin(): Promise<void> {
    if (!canSpin.value || !data.value) return;
    spinning.value = true;
    result.value = null;
    try {
        const outcome = await api.spin();
        const slice = slices.value.find((s) => s.prize.key === outcome.prizeKey);
        if (slice) rotation.value = UtilPointGame.targetRotation(rotation.value, slice, Math.random());
        // 판이 멈춘 뒤에 결과·잔액을 보인다(멈추기 전에 숫자가 바뀌면 결과를 미리 알게 된다)
        timer = setTimeout(() => {
            timer = null;
            if (data.value) data.value = { ...data.value, freeBalance: outcome.freeBalance, usedToday: outcome.usedToday };
            result.value = outcome;
            spinning.value = false;
            // 헤더·포인트 화면의 잔액도 맞춘다
            void points.load();
        }, spinMs.value);
    } catch (e) {
        spinning.value = false;
        toast.error(UtilPointGameMessages.error(t, e), 6000);
        // 다른 기기에서 다 돌렸거나 잔액이 바뀌었다 — 지금 상태를 다시 받는다
        if (e instanceof ApiError && (e.code === 'POINT_GAME_DAILY_LIMIT' || e.code === 'POINT_INSUFFICIENT')) {
            try {
                data.value = await api.roulette();
            } catch {
                // 받아 둔 값을 그대로 둔다
            }
        }
    }
}
</script>
