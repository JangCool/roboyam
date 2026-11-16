<template>
    <!--
        포인트 게임 가젯(`POINT_GAMES` — point-games-contract 「홈 가젯」) — 룰렛 · 로또 번호 뽑기 두 입구를 줄 두 개로(아이콘 · 이름 · 짧은 설명 · 오른쪽 꼬리표 · ›). 설정 없음.
        가젯 `data` 는 늘 null 이다(공용 응답·캐시) — 오늘 남은 룰렛 횟수는 로그인했고 사업자 전용 계정이 아닌 회원만 `useRouletteToday`(`GET /api/me/point-games/roulette`)로 받아
        룰렛 줄 오른쪽에 「오늘 3/5」(다 쓰면 「오늘 끝」)로 보인다. 못 받았으면 꼬리표 없이 입구만.
        비로그인: 입구만 — 누르면 로그인(`UtilRedirect.toLogin`, 로그인 뒤 그 게임으로). 설명 줄은 「로그인하고 시작해요」.
        사업자 전용 계정: 포인트가 없어 격자가 자리째 감춘다(`UtilGadget.hiddenForViewer`) — 격자 밖에서 그려져도 여기서 한 번 더 거른다(서버 403 을 부르지 않는다).
        비지 않는다(`:empty="false"`) — 두 입구는 늘 있다.
    -->
    <GadgetFrame v-if="!business" :gadget="gadget" :empty="false">
        <nav :aria-label="gadget.title || t('pointGame.gadgetTitle')">
            <ul class="m-0 flex list-none flex-col gap-2 p-0" data-testid="home-point-games">
                <li v-for="entry in entries" :key="entry.key">
                    <NuxtLink
                        :to="entry.to"
                        class="flex min-w-0 items-center gap-3 rounded-lg border border-hairline px-3 py-2 hover:bg-surface-50 focus-visible:outline-2 focus-visible:outline-primary dark:hover:bg-surface-800"
                        :data-testid="`home-point-game-${entry.key}`"
                    >
                        <span class="flex size-9 shrink-0 items-center justify-center rounded-lg bg-primary/10 text-primary" aria-hidden="true">
                            <i :class="entry.icon" />
                        </span>
                        <span class="flex min-w-0 flex-1 flex-col gap-0.5">
                            <span class="truncate text-sm font-semibold">{{ entry.label }}</span>
                            <span class="truncate text-xs text-muted-color">{{ entry.hint }}</span>
                        </span>
                        <span
                            v-if="entry.badge"
                            class="shrink-0 rounded-full px-2 py-0.5 text-xs font-semibold tabular-nums"
                            :class="entry.badge.done ? 'bg-surface-100 text-muted-color dark:bg-surface-800' : 'bg-primary/10 text-primary'"
                            :aria-label="entry.badge.label"
                            data-testid="home-point-game-remaining"
                        >
                            {{ entry.badge.text }}
                        </span>
                        <i class="pi pi-angle-right shrink-0 text-xs text-muted-color" aria-hidden="true" />
                    </NuxtLink>
                </li>
            </ul>
        </nav>
    </GadgetFrame>
</template>

<script setup lang="ts">
import type { RouteLocationRaw } from 'vue-router';
import type { Gadget } from '~/composables/gadget/api/useGadgetApi.types';
import UtilBusiness from '~/utils/util-business';
import UtilPointGame from '~/utils/util-point-game';
import UtilRedirect from '~/utils/util-redirect';

interface Props {
    gadget: Gadget;
}

const props = withDefaults(defineProps<Props>(), {});
void props;

interface Entry {
    key: 'roulette' | 'lotto';
    to: RouteLocationRaw;
    icon: string;
    label: string;
    hint: string;
    badge: { text: string; label: string; done: boolean } | null;
}

const { t } = useI18n();
const { user } = useAuth();
const roulette = useRouletteToday();

const signedIn = computed(() => Boolean(user.value?.id));
const business = computed(() => UtilBusiness.isBusinessAccount(user.value?.role));

// 회원이 바뀌면(로그인·로그아웃·계정 전환) 다시 묻는다 — 비로그인·사업자는 묻지 않는다(`useRouletteToday.load` 가 거른다)
watch(
    () => user.value?.id ?? null,
    () => {
        void roulette.load();
    },
    { immediate: true },
);

/** 들어갈 곳 — 비로그인은 로그인 뒤 그 게임으로 */
function entryTo(path: string): RouteLocationRaw {
    return signedIn.value ? path : UtilRedirect.toLogin(path);
}

const rouletteBadge = computed<Entry['badge']>(() => {
    const today = roulette.today.value;
    const left = roulette.remaining.value;
    if (!signedIn.value || !today || left === null) return null;
    if (left === 0) return { text: t('pointGame.gadgetDone'), label: t('pointGame.gadgetDoneLabel', { limit: today.dailyLimit }), done: true };
    return { text: t('pointGame.gadgetRemaining', { left, limit: today.dailyLimit }), label: t('pointGame.gadgetRemainingLabel', { left, limit: today.dailyLimit }), done: false };
});

const entries = computed<Entry[]>(() => [
    {
        key: 'roulette',
        to: entryTo(UtilPointGame.ROULETTE_PATH),
        icon: 'pi pi-sync',
        label: t('pointGame.rouletteTitle'),
        hint: signedIn.value ? t('pointGame.gadgetRouletteHint') : t('pointGame.gadgetLoginHint'),
        badge: rouletteBadge.value,
    },
    {
        key: 'lotto',
        to: entryTo(UtilPointGame.LOTTO_PATH),
        icon: 'pi pi-ticket',
        label: t('pointGame.lottoTitle'),
        hint: signedIn.value ? t('pointGame.gadgetLottoHint') : t('pointGame.gadgetLoginHint'),
        badge: null,
    },
]);
</script>
