<template>
    <div class="content-card">
        <h1 class="m-0 text-lg font-bold">{{ t('memberTitle.hallOfFame') }}</h1>
        <p class="m-0 mt-0.5 text-sm text-muted-color">{{ t('memberTitle.hallLead') }}</p>

        <!-- 명예의 전당 — 명예 작가 · 역대 올해의 작가 · 역대 이달의 작가 -->
        <section class="mt-5" :aria-labelledby="hallHeadingId" data-testid="hall-section">
            <h2 :id="hallHeadingId" class="sr-only">{{ t('memberTitle.hallHeading') }}</h2>
            <div v-if="hallState === 'loading'" class="space-y-3" role="status" :aria-label="t('memberTitle.loading')">
                <div v-for="n in 3" :key="n" class="h-20 animate-pulse rounded-xl bg-surface-200 dark:bg-surface-800" />
            </div>
            <ErrorState v-else-if="hallState === 'error'" :title="t('memberTitle.hallLoadFailed')" :description="hallError" retry class="panel-card" @retry="loadHall" />
            <template v-else-if="hall">
                <EmptyState v-if="hallEmpty" :title="t('memberTitle.hallEmpty')" :description="t('memberTitle.hallEmptyHint')" icon="pi pi-trophy" class="panel-card" data-testid="hall-empty" />
                <template v-else>
                    <!-- 명예 작가 — 기간 없음 -->
                    <div v-if="hall.honorary.length" class="rounded-xl border border-amber-200 bg-amber-50/60 p-4 dark:border-amber-900 dark:bg-amber-950/20" data-testid="hall-honorary">
                        <h3 class="m-0 flex items-center gap-2 text-sm font-semibold">
                            <TitleIcon icon="wreath" :scale="3" />{{ t('memberTitle.honoraryHeading') }}
                        </h3>
                        <ul class="m-0 mt-3 grid list-none grid-cols-2 gap-2 p-0 sm:grid-cols-3 md:grid-cols-4">
                            <li v-for="member in hall.honorary" :key="member.id">
                                <NuxtLink :to="UtilProfile.path(member.id)" class="flex min-h-11 items-center gap-2 rounded-lg px-1.5 py-1 hover:bg-amber-100/70 focus-visible:outline-2 focus-visible:outline-primary dark:hover:bg-amber-900/30">
                                    <ProfileBorderAvatar :initial="UtilPointShop.initial(member.nickname)" :src="member.avatarUrl" size="sm" lazy />
                                    <span class="min-w-0 truncate text-sm font-medium">{{ member.nickname }}</span>
                                </NuxtLink>
                            </li>
                        </ul>
                    </div>

                    <!-- 역대 올해의 작가 -->
                    <div v-if="hall.yearly.length" class="mt-4 panel-card p-4" data-testid="hall-yearly">
                        <h3 class="m-0 flex items-center gap-2 text-sm font-semibold"><TitleIcon icon="trophy" :scale="3" />{{ t('memberTitle.yearlyHeading') }}</h3>
                        <dl class="m-0 mt-2">
                            <div v-for="row in hall.yearly" :key="row.year" class="flex flex-wrap items-baseline gap-x-3 gap-y-1 border-b border-surface py-2 last:border-b-0">
                                <dt class="w-24 shrink-0 text-xs font-semibold tabular-nums text-muted-color">{{ UtilMemberTitleMessages.period(t, row.year) }}</dt>
                                <dd class="m-0 flex min-w-0 flex-1 flex-wrap gap-x-3 gap-y-1">
                                    <NuxtLink v-for="member in row.members" :key="member.id" :to="UtilProfile.path(member.id)" class="text-sm font-medium hover:underline">{{ member.nickname }}</NuxtLink>
                                </dd>
                            </div>
                        </dl>
                    </div>

                    <!-- 역대 이달의 작가 -->
                    <div v-if="hall.monthly.length" class="mt-4 panel-card p-4" data-testid="hall-monthly">
                        <h3 class="m-0 flex items-center gap-2 text-sm font-semibold"><TitleIcon icon="pen" :scale="3" />{{ t('memberTitle.monthlyHeading') }}</h3>
                        <dl class="m-0 mt-2">
                            <div v-for="row in hall.monthly" :key="row.month" class="flex flex-wrap items-baseline gap-x-3 gap-y-1 border-b border-surface py-2 last:border-b-0">
                                <dt class="w-24 shrink-0 text-xs font-semibold tabular-nums text-muted-color">{{ UtilMemberTitleMessages.period(t, row.month) }}</dt>
                                <dd class="m-0 flex min-w-0 flex-1 flex-wrap gap-x-3 gap-y-1">
                                    <NuxtLink v-for="member in row.members" :key="member.id" :to="UtilProfile.path(member.id)" class="text-sm font-medium hover:underline">{{ member.nickname }}</NuxtLink>
                                </dd>
                            </div>
                        </dl>
                    </div>
                </template>
            </template>
        </section>

        <!-- 칭호 도감 — 칭호별 받는 방법·가진 회원 수·비율(단계 칭호는 단계마다) -->
        <section class="mt-6" :aria-labelledby="catalogHeadingId" data-testid="title-catalog">
            <h2 :id="catalogHeadingId" class="m-0 text-base font-bold">{{ t('memberTitle.catalogHeading') }}</h2>
            <div v-if="catalogState === 'loading'" class="mt-3 grid gap-3 sm:grid-cols-2" role="status" :aria-label="t('memberTitle.loading')">
                <div v-for="n in 4" :key="n" class="h-32 animate-pulse rounded-xl bg-surface-200 dark:bg-surface-800" />
            </div>
            <ErrorState v-else-if="catalogState === 'error'" :title="t('memberTitle.catalogLoadFailed')" :description="catalogError" retry class="mt-3 panel-card" @retry="loadCatalog" />
            <EmptyState v-else-if="catalog.length === 0" :title="t('memberTitle.catalogEmpty')" icon="pi pi-trophy" class="mt-3 panel-card" />
            <ul v-else class="m-0 mt-3 grid list-none gap-3 p-0 sm:grid-cols-2">
                <li v-for="item in catalog" :key="item.key" class="panel-card flex gap-3 p-4" :data-testid="`title-catalog-${item.key}`">
                    <TitleIcon :icon="item.icon" :tier="item.tiered ? 'GOLD' : null" :label="localizedName(item.label, item.labelEn ?? null)" :scale="4" />
                    <div class="min-w-0 flex-1">
                        <p class="m-0 flex flex-wrap items-center gap-1.5">
                            <b class="text-sm font-semibold">{{ localizedName(item.label, item.labelEn ?? null) }}</b>
                            <span :class="[TAG, UtilMemberTitle.chipClass(item.chip)]"><span class="relative top-[-0.09em]">{{ UtilMemberTitleMessages.method(t, item.method) }}</span></span>
                        </p>
                        <p class="m-0 mt-1 text-xs leading-snug text-muted-color">{{ item.howTo }}</p>
                        <p class="m-0 mt-1.5 text-xs tabular-nums">{{ t('memberTitle.holders', { count: item.holders.toLocaleString() }) }} · {{ UtilMemberTitleMessages.ratio(t, item.ratio) }}</p>
                        <ul v-if="item.tiers.length" class="m-0 mt-2 flex list-none flex-wrap gap-x-3 gap-y-1 p-0 text-xs">
                            <li v-for="tier in item.tiers" :key="tier.tier" class="inline-flex items-center gap-1 tabular-nums">
                                <TitleIcon :icon="item.icon" :tier="tier.tier" :scale="2" :label="t('memberTitle.progressTier', { label: localizedName(item.label, item.labelEn ?? null), tier: UtilMemberTitleMessages.tier(t, tier.tier) })" />
                                {{ t('memberTitle.tierLine', { tier: UtilMemberTitleMessages.tier(t, tier.tier), threshold: tier.threshold.toLocaleString(), count: tier.holders.toLocaleString() }) }}
                            </li>
                        </ul>
                    </div>
                </li>
            </ul>
        </section>
    </div>
</template>

<script setup lang="ts">
import type { HallOfFame, TitleCatalogItem } from '~/composables/title/api/useTitleApi.types';
import UtilBoardMessages from '~/utils/util-board-messages';
import UtilMemberTitle from '~/utils/util-member-title';
import UtilMemberTitleMessages from '~/utils/util-member-title-messages';
import UtilPointShop from '~/utils/util-point-shop';
import UtilProfile from '~/utils/util-profile';

/**
 * 명예의 전당 · 칭호 도감 — `/hall-of-fame`(member-title-contract 「화면」, **누구나**). 들어가는 곳 = 푸터 · 내 정보 › 칭호.
 * 명예의 전당(`GET /api/hall-of-fame`)과 도감(`GET /api/titles`)은 따로 받는다 — 한쪽이 실패해도 다른 쪽은 그대로, 그쪽만 다시 시도한다.
 * 한 화면의 값이라 페이지 `ref` 로 둔다. 서버는 `useTitleApi`(⚠ 지금은 목업).
 */
const TAG = 'inline-flex items-center justify-center rounded px-1.5 py-1 text-[0.6875rem] font-semibold leading-none';

const { t } = useI18n();
const api = useTitleApi();
const { localizedName } = useLocalizedName();
const hallHeadingId = useId();
const catalogHeadingId = useId();

const hall = ref<HallOfFame | null>(null);
const hallState = ref<'loading' | 'ready' | 'error'>('loading');
const hallError = ref('');
const hallEmpty = computed(() => !!hall.value && hall.value.honorary.length === 0 && hall.value.yearly.length === 0 && hall.value.monthly.length === 0);

async function loadHall(): Promise<void> {
    hallState.value = 'loading';
    try {
        hall.value = await api.hallOfFame();
        hallState.value = 'ready';
    } catch (e) {
        hallError.value = UtilBoardMessages.of(t, e);
        hallState.value = 'error';
    }
}

const catalog = ref<TitleCatalogItem[]>([]);
const catalogState = ref<'loading' | 'ready' | 'error'>('loading');
const catalogError = ref('');

async function loadCatalog(): Promise<void> {
    catalogState.value = 'loading';
    try {
        catalog.value = await api.catalog();
        catalogState.value = 'ready';
    } catch (e) {
        catalogError.value = UtilBoardMessages.of(t, e);
        catalogState.value = 'error';
    }
}

void loadHall();
void loadCatalog();

const { siteLabel } = useSiteName();
useSeoMeta({ title: () => t('memberTitle.hallOfFame'), description: () => t('memberTitle.hallSeo', { site: siteLabel.value }) });
</script>
