<template>
    <div class="mx-auto max-w-xl">
        <!-- 불러오는 중 -->
        <div v-if="status === 'loading'" class="mt-2" role="status" :aria-label="t('sanction.loading')" data-testid="sanction-loading">
            <div class="mx-auto size-14 animate-pulse rounded-full bg-surface-200 dark:bg-surface-800" />
            <div class="mx-auto mt-3 h-5 w-1/2 animate-pulse rounded bg-surface-200 dark:bg-surface-800" />
            <div class="mx-auto mt-2 h-4 w-2/3 animate-pulse rounded bg-surface-200 dark:bg-surface-800" />
            <div class="mt-5 h-32 animate-pulse rounded-xl bg-surface-200 dark:bg-surface-800" />
            <div class="mt-3 h-20 animate-pulse rounded-xl bg-surface-200 dark:bg-surface-800" />
        </div>

        <!-- 불러오지 못했다 -->
        <ErrorState v-else-if="status === 'error'" :title="t('sanction.loadError')" :description="errorMessage" retry data-testid="sanction-error" @retry="load" />

        <template v-else-if="screen">
            <!-- ① 확인하지 않은 경고 — 체크하고 「확인하고 계속」 -->
            <section v-if="screen === 'WARNING'" :aria-label="headerTitle" data-testid="sanction-warning">
                <StatusPanel icon="pi pi-exclamation-triangle" :tone="warningCount >= UtilSanction.WARNING_LIMIT ? 'error' : 'warn'" :title="warningHeading.title">
                    {{ warningHeading.text }}
                </StatusPanel>

                <SanctionSummary class="mt-4" :rows="summaryRows" />

                <div
                    class="mt-3 h-2 overflow-hidden rounded-full bg-surface-200 dark:bg-surface-700"
                    role="progressbar"
                    :aria-label="t('sanction.warningProgress')"
                    :aria-valuenow="warningCount"
                    :aria-valuemin="0"
                    :aria-valuemax="UtilSanction.WARNING_LIMIT"
                    :aria-valuetext="UtilSanctionMessages.warningCountText(t, warningCount)"
                    data-testid="sanction-warning-progress"
                >
                    <i class="block h-full rounded-full" :class="warningCount >= UtilSanction.WARNING_LIMIT ? 'bg-red-500' : 'bg-amber-500'" :style="{ width: `${UtilSanction.warningPercent(warningCount)}%` }" />
                </div>

                <SanctionNotice class="mt-3" :tone="warningNotice.tone" :title="warningNotice.title" :text="warningNotice.text" data-testid="sanction-warning-notice" />

                <div class="mt-4 flex items-start gap-2.5">
                    <Checkbox v-model="confirmed" binary :input-id="ackId" :disabled="acknowledging" data-testid="sanction-ack" />
                    <label :for="ackId" class="cursor-pointer text-sm leading-snug">{{ t('sanction.ackLabel') }}</label>
                </div>
                <p v-if="ackError" role="alert" class="mb-0 mt-2 text-sm text-red-600 dark:text-red-400" data-testid="sanction-ack-error">{{ ackError }}</p>

                <Button
                    type="button"
                    class="mt-3"
                    fluid
                    size="large"
                    :label="acknowledging ? t('sanction.ackSaving') : t('sanction.ackContinue')"
                    :loading="acknowledging"
                    :disabled="!canAcknowledge"
                    data-testid="sanction-continue"
                    @click="onContinue"
                />
                <p v-if="focus && UtilSanction.canAppeal(focus)" class="mb-0 mt-3 text-center text-sm">
                    <NuxtLink :to="UtilSanction.appealTo(focus.id)" class="text-primary hover:underline focus-visible:outline-2 focus-visible:outline-primary" data-testid="sanction-appeal-link">
                        {{ t('sanction.appealButton') }}
                    </NuxtLink>
                </p>
            </section>

            <!-- ②③ 기간 정지 · 영구 정지 — 읽기와 이의제기만 -->
            <section v-else-if="screen === 'SUSPENDED' || screen === 'BANNED'" :aria-label="headerTitle" :data-screen="screen" data-testid="sanction-restricted">
                <h1 class="m-0 text-lg font-bold">{{ headerTitle }}</h1>

                <SanctionNotice class="mt-3" :tone="restrictedNotice.tone" :title="restrictedNotice.title" :text="restrictedNotice.text" live data-testid="sanction-restricted-notice" />

                <SanctionSummary class="mt-3" :rows="summaryRows" />

                <div class="mt-4" data-testid="sanction-rules">
                    <p class="m-0 text-xs font-semibold text-muted-color">{{ screen === 'BANNED' ? t('sanction.rulesBanned') : t('sanction.rulesSuspended') }}</p>
                    <ul class="m-0 mt-1.5 list-none p-0 text-sm">
                        <li v-for="item in [t('sanction.ruleRead'), t('sanction.ruleAppeal')]" :key="item" class="flex gap-2 py-0.5 text-green-700 dark:text-green-400">
                            <span aria-hidden="true">✓</span><span>{{ item }}</span>
                        </li>
                        <li class="flex gap-2 py-0.5 text-red-600 dark:text-red-400"><span aria-hidden="true">✕</span><span>{{ t('sanction.ruleWrite') }}</span></li>
                    </ul>
                </div>

                <div class="mt-5 flex flex-col gap-2">
                    <Button
                        v-if="focus && UtilSanction.canAppeal(focus)"
                        as="router-link"
                        :to="UtilSanction.appealTo(focus.id)"
                        :label="t('sanction.appealButton')"
                        size="large"
                        fluid
                        data-testid="sanction-appeal-link"
                    />
                    <!-- 이미 접수했다 — 처리 상태를 보여 주는 이의제기 화면으로 -->
                    <Button
                        v-else-if="focus?.appeal"
                        as="router-link"
                        :to="UtilSanction.appealTo(focus.id)"
                        :label="t('sanction.appealViewStatus', { status: UtilSanctionMessages.appealStatusLabel(t, focus.appeal.status) })"
                        size="large"
                        fluid
                        data-testid="sanction-appeal-view"
                    />
                    <Button type="button" :label="continueLabel" size="large" severity="secondary" variant="outlined" fluid data-testid="sanction-home" @click="goContinue" />
                    <!-- 영구 정지 계정은 로그아웃 길을 남긴다 -->
                    <Button v-if="screen === 'BANNED'" type="button" :label="t('common.logout')" variant="text" severity="secondary" fluid :loading="logoutBusy" data-testid="sanction-logout" @click="logout" />
                </div>
            </section>

            <!-- ④ 제한 없음 — 이력만 -->
            <section v-else :aria-label="headerTitle" data-testid="sanction-clean">
                <h1 class="m-0 text-lg font-bold">{{ headerTitle }}</h1>
                <EmptyState :title="t('sanction.cleanTitle')" :description="t('sanction.cleanText')" icon="pi pi-check-circle">
                    <Button type="button" :label="continueLabel" severity="secondary" variant="outlined" data-testid="sanction-home" @click="goContinue" />
                </EmptyState>
            </section>

            <!-- 내 제재 이력 — 최근 것이 위. 무효가 된 제재도 「무효」 표시와 함께 남고, 이의제기 상태와 운영자 답변이 붙는다(글자로만 그린다) -->
            <section class="mt-8" :aria-labelledby="historyId" data-testid="sanction-history">
                <h2 :id="historyId" class="m-0 text-base font-semibold">{{ t('sanction.historyTitle') }}</h2>
                <p class="mb-0 mt-0.5 text-xs text-muted-color">{{ t('sanction.historyHint') }}</p>

                <EmptyState v-if="rows.length === 0" :title="t('sanction.historyEmptyTitle')" :description="t('sanction.historyEmptyText')" icon="pi pi-shield" data-testid="sanction-history-empty" />

                <ul v-else class="m-0 mt-2 list-none border-t border-surface p-0">
                    <li v-for="row in rows" :key="row.id" class="border-b border-surface py-3" :data-voided="row.voided ? 'true' : 'false'" data-testid="sanction-row">
                        <p class="m-0 flex flex-wrap items-center gap-x-2 gap-y-1">
                            <span :class="[TAG, UtilSanction.toneClass(row.voided ? 'gray' : row.kindTone)]"><span class="relative top-[-0.09em]">{{ row.title }}</span></span>
                            <span :class="[TAG, UtilSanction.toneClass(row.statusTone)]"><span class="relative top-[-0.09em]">{{ row.statusLabel }}</span></span>
                            <!-- 「확인 필요」 표시는 경고 안내 화면에서만 — 정지 중에는 그 자리에서 확인할 길이 없다 -->
                            <span v-if="screen === 'WARNING' && row.needsConfirm" :class="[TAG, UtilSanction.toneClass('amber')]"><span class="relative top-[-0.09em]">{{ t('sanction.needsConfirm') }}</span></span>
                            <time class="ml-auto text-xs text-muted-color">{{ row.dateText }}</time>
                        </p>

                        <p class="mb-0 mt-1.5 whitespace-pre-line break-words text-sm leading-snug [overflow-wrap:anywhere]" :class="row.voided ? 'text-muted-color line-through decoration-surface-400' : ''">
                            {{ row.reason }}
                        </p>
                        <p v-if="row.periodText" class="mb-0 mt-1 text-xs text-muted-color">{{ t('sanction.periodLine', { period: row.periodText }) }}</p>
                        <p v-if="row.voided" class="mb-0 mt-1 text-xs text-muted-color">{{ t('sanction.voidedNote') }}</p>

                        <div v-if="row.appeal" class="mt-2 rounded-lg bg-surface-100 p-2.5 text-xs dark:bg-surface-800/60" data-testid="sanction-row-appeal">
                            <p class="m-0 flex flex-wrap items-center gap-x-2 gap-y-1">
                                <span class="font-medium">{{ t('sanction.appealLabel', { no: row.appeal.no }) }}</span>
                                <span :class="[TAG, UtilSanction.toneClass(row.appeal.tone)]"><span class="relative top-[-0.09em]">{{ row.appeal.statusLabel }}</span></span>
                            </p>
                            <template v-if="row.appeal.answer">
                                <p class="mb-0 mt-2 font-medium">{{ t('sanction.answerLabel') }}</p>
                                <p class="m-0 mt-0.5 whitespace-pre-line break-words leading-relaxed [overflow-wrap:anywhere]">{{ row.appeal.answer }}</p>
                            </template>
                        </div>

                        <div v-if="row.canAppeal || row.appeal" class="mt-2">
                            <Button
                                as="router-link"
                                :to="UtilSanction.appealTo(row.id)"
                                :label="row.canAppeal ? t('sanction.appealButton') : t('sanction.appealView')"
                                size="small"
                                severity="secondary"
                                variant="outlined"
                                :data-testid="row.canAppeal ? 'sanction-row-appeal-link' : 'sanction-row-appeal-view'"
                            />
                        </div>
                    </li>
                </ul>
            </section>
        </template>
    </div>
</template>

<script setup lang="ts">
import UtilSanction from '~/utils/util-sanction';
import UtilSanctionMessages from '~/utils/util-sanction-messages';

/**
 * 제재 안내 — `/sanction`(community `pages/sanction/index.vue`, `docs/api-sanction.md` 2절). 로그인한 회원이면 정지·영구 정지 중에도 쓴다(읽기 전용 계정).
 * 계정 상태에 따라 안내가 바뀐다 — ① 확인하지 않은 경고(누적 N/2회 · 「안내를 확인했어요」 체크 뒤 「확인하고 계속」) ② 기간 정지(사유·기간·해제 예정·단계 · 「정지 중에는」 · 이의제기하기)
 * ③ 영구 정지(로그아웃 길) ④ 정상이면 이력만. 아래에는 늘 **내 제재 이력**.
 * 로그인 직후 이동(`UtilSanction.afterLoginPath`)이 이곳으로 올 때 원래 가려던 곳을 `?next=` 로 들고 온다 — 「확인하고 계속」·「홈으로」 뒤에 그곳으로 간다(같은 사이트 안의 경로만).
 * 흐름은 `useMySanctions`, 문구는 `UtilSanctionMessages`, 계산은 `UtilSanction`. ⚠ 목업 API(`useSanctionApi`) — `?mock=sanction-warning` 등으로 갈래를 고른다.
 * 원본과 달라진 점: 게시판 한정 제재 구획(게시판 지킴이)은 로보얌에 없어 뺐다. 모바일 ‹ 머리(`InnerHeader`)가 없어 제목을 그대로 보인다.
 */
definePageMeta({ middleware: 'auth' });
const { t } = useI18n();
const { siteLabel } = useSiteName();
useSeoMeta({ title: () => t('sanction.seoTitle'), description: () => t('sanction.seoDescription', { site: siteLabel.value }), robots: 'noindex' });

const route = useRoute();
const ackId = useId();
const historyId = useId();
const time = useDisplayTime();
const { busy: logoutBusy, run: logout } = useLogoutFlow();
const { status, errorMessage, screen, focus, warningCount, rows, confirmed, acknowledging, ackError, canAcknowledge, load, cancel, acknowledge } = useMySanctions();

const TAG = 'inline-flex items-center justify-center rounded px-1.5 py-1 text-[0.6875rem] font-semibold leading-none';

const hasNext = computed(() => UtilSanction.continuePath(route.query.next) !== '/');
const headerTitle = computed(() => UtilSanctionMessages.headerTitle(t, screen.value ?? 'CLEAN'));
const continueLabel = computed(() => UtilSanctionMessages.continueLabel(t, screen.value ?? 'CLEAN', hasNext.value));
const warningHeading = computed(() => UtilSanctionMessages.warningHeading(t, warningCount.value));
const warningNotice = computed(() => UtilSanctionMessages.warningNotice(t, warningCount.value));
const restrictedNotice = computed(() => UtilSanctionMessages.restrictedNotice(t, screen.value ?? 'SUSPENDED', focus.value ? UtilSanction.daysOf(focus.value.kind) : null));
const summaryRows = computed(() => (screen.value ? UtilSanctionMessages.summaryRows(t, screen.value, focus.value, warningCount.value, time.value) : []));

/** 「확인하고 계속」 — 확인을 저장하면 원래 가려던 곳으로 */
async function onContinue() {
    if ((await acknowledge()) === 'DONE') await goContinue();
}

/** 원래 가려던 곳(`next`)으로, 없으면 홈 */
async function goContinue() {
    await navigateTo(UtilSanction.continuePath(route.query.next));
}

void load();
onBeforeUnmount(cancel);
</script>
