<template>
    <!--
        체험단 페이지 `/trials?tab=running|closed`(trial-group-contract 「사용기 쓰는 곳」 — 메뉴 바로 가기 내장 화면 `TRIALS`). 주소의 `?tab=` 이 화면 상태다.
        탭: 모집 중(뽑는 중 포함) · 진행 중 · 끝난 모집. 카드(서버 `TrialCard`): 「광고」 딱지 + 광고주 이름 · 대표 그림 · 제목 · 형태(제공형/대여형) · 제품값 · 인원 · 신청 수 · 마감(진행 중·끝난 모집은 사용기 게시판).
        누르면 모집 상세(`/trials/{id}` — 끝난 모집은 그 아래 사용기 모아 보기). 로그인한 회원은 오른쪽 위 [내 체험단].
        모집 글은 게시판 글이 아니라 이 페이지에만 있다(계약 「광고 표시」).
    -->
    <div class="content-card flex flex-col gap-4" data-testid="trials">
        <header class="flex flex-wrap items-center justify-between gap-2">
            <div>
                <h1 class="m-0 text-lg font-bold">{{ t('trial.title') }}</h1>
                <p class="m-0 mt-0.5 text-xs text-muted-color">{{ t('trial.subtitle') }}</p>
            </div>
            <Button v-if="user" as="router-link" :to="UtilTrial.MY_PATH" :label="t('trial.myTitle')" icon="pi pi-box" size="small" severity="secondary" variant="outlined" data-testid="trials-mine" />
        </header>

        <nav class="flex gap-5 border-b border-hairline" :aria-label="t('trial.tabsLabel')" data-testid="trials-tabs">
            <NuxtLink
                v-for="tab in UtilTrial.TABS"
                :key="tab"
                :to="{ path: UtilTrial.LIST_PATH, query: tab === 'RECRUITING' ? {} : { tab: tab.toLowerCase() } }"
                class="-mb-px inline-flex min-h-11 items-center border-b-2 text-[0.9375rem]"
                :class="active === tab ? 'border-(--p-text-color) font-bold text-color' : 'border-transparent text-muted-color hover:text-color'"
                :aria-current="active === tab ? 'page' : undefined"
                :data-testid="`trials-tab-${tab}`"
            >
                {{ t(`trial.tab.${tab}`) }}
            </NuxtLink>
        </nav>

        <div v-if="state === 'loading'" class="grid gap-3 sm:grid-cols-2" role="status" :aria-label="t('common.loading')">
            <div v-for="n in 4" :key="n" class="h-64 animate-pulse rounded-xl bg-surface-200 dark:bg-surface-800" />
        </div>
        <ErrorState v-else-if="state === 'error'" :title="t('trial.loadFailed')" :description="errorMessage" retry @retry="load" />
        <EmptyState v-else-if="items.length === 0" icon="pi pi-box" :title="t(`trial.empty.${active}`)" data-testid="trials-empty" />
        <ul v-else class="m-0 grid list-none gap-3 p-0 sm:grid-cols-2" data-testid="trials-list">
            <li v-for="item in items" :key="item.id">
                <NuxtLink :to="UtilTrial.detailPath(item.id)" class="panel-card flex h-full flex-col overflow-hidden rounded-xl border border-surface hover:border-primary focus-visible:outline-2 focus-visible:outline-primary" :data-testid="`trial-card-${item.id}`">
                    <div class="relative aspect-[16/9] bg-surface-100 dark:bg-surface-800">
                        <img v-if="item.imageUrl" :src="item.imageUrl" alt="" class="size-full object-cover" loading="lazy" />
                        <span v-else class="grid size-full place-items-center text-muted-color" aria-hidden="true"><i class="pi pi-box text-3xl" /></span>
                        <span class="absolute left-2 top-2 inline-flex items-center justify-center rounded bg-amber-500 px-1.5 py-1 text-[0.6875rem] font-bold leading-none text-white">
                            <span class="relative top-[-0.09em]">{{ t('trial.adBadge') }}</span>
                        </span>
                    </div>
                    <div class="flex flex-1 flex-col gap-1.5 p-3">
                        <p class="m-0 text-xs text-muted-color">{{ t('trial.byAdvertiser', { name: item.advertiserName }) }}</p>
                        <h2 class="m-0 line-clamp-2 text-[0.9375rem] font-semibold">{{ item.title }}</h2>
                        <p class="m-0 flex flex-wrap items-center gap-1.5 text-xs">
                            <span :class="[CHIP, item.form === 'GIVE' ? 'bg-green-100 text-green-900 dark:bg-green-900/40 dark:text-green-200' : 'bg-sky-100 text-sky-900 dark:bg-sky-900/40 dark:text-sky-200']">
                                <span class="relative top-[-0.09em]">{{ t(`trial.form.${item.form}`) }}</span>
                            </span>
                            <span class="tabular-nums text-muted-color">{{ t('trial.productPrice', { price: UtilTrialMessages.won(t, item.productPrice, locale) }) }}</span>
                        </p>
                        <p class="m-0 mt-auto flex flex-wrap items-center gap-x-3 gap-y-0.5 pt-1 text-xs tabular-nums text-muted-color">
                            <span>{{ t('trial.recruitCount', { n: item.recruitCount }) }}</span>
                            <span>{{ t('trial.applicantCount', { n: item.applicantCount }) }}</span>
                            <span v-if="active === 'RECRUITING'">{{ t('trial.applyEnd', { date: time.date(item.applyEndAt) }) }}</span>
                            <span v-else>{{ t('trial.boardLine', { board: item.reviewBoard.name }) }}</span>
                        </p>
                    </div>
                </NuxtLink>
            </li>
        </ul>
    </div>
</template>

<script setup lang="ts">
import type { TrialCard } from '~/composables/trial/api/useTrialApi.types';
import UtilBoardMessages from '~/utils/util-board-messages';
import UtilTrial from '~/utils/util-trial';
import UtilTrialMessages from '~/utils/util-trial-messages';

const CHIP = 'inline-flex items-center justify-center rounded px-1.5 py-1 text-[0.6875rem] font-semibold leading-none';

const { t, locale } = useI18n();
const route = useRoute();
const api = useTrialApi();
const time = useDisplayTime();
const { user } = useAuth();

const active = computed(() => UtilTrial.tabOf(route.query.tab));
const items = ref<TrialCard[]>([]);
const state = ref<'loading' | 'ready' | 'error'>('loading');
const errorMessage = ref('');

let seq = 0;
async function load(): Promise<void> {
    const mine = ++seq;
    state.value = 'loading';
    try {
        const res = await api.list(active.value);
        if (mine !== seq) return;
        items.value = res;
        state.value = 'ready';
    } catch (e) {
        if (mine !== seq) return;
        errorMessage.value = UtilBoardMessages.of(t, e);
        state.value = 'error';
    }
}

watch(active, () => void load(), { immediate: true });

useSeoMeta({ title: () => t('trial.title') });
</script>
