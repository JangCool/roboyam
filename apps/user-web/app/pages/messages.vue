<template>
    <!--
        쪽지함 틀 — 목록 칸(쪽지함)과 오른쪽 칸(`<NuxtPage />`: 대화방 · 새 쪽지 · 안내)을 **한 번만** 만든다(community `pages/messages.vue`).
        대화를 눌러 `/messages/{id}` 로 가도 이 틀과 목록은 그대로 있고 오른쪽 칸만 바뀐다. 데스크톱(lg)은 왼쪽 300px 목록 + 오른쪽 2단, 모바일은 한 칸만.
    -->
    <div :class="frameClass" data-testid="dm-frame">
        <!-- ── 쪽지함(대화 목록) ── 맨 위에 법적 고지가 늘 보이고, 수신 거부 중·정지 중이면 안내가 붙는다. 읽음 처리는 여기서 하지 않는다 -->
        <section class="flex min-h-0 flex-col lg:border-r lg:border-surface" :class="atList ? '' : 'max-lg:hidden'" :aria-label="t('dm.inboxTitle')" data-testid="dm-inbox">
            <header class="flex h-12 shrink-0 items-center gap-2 border-b border-surface px-4">
                <h1 class="m-0 min-w-0 flex-1 text-base font-bold leading-tight">{{ t('dm.inboxTitle') }}</h1>
                <NuxtLink
                    :to="UtilDm.newPath()"
                    class="inline-flex min-h-10 shrink-0 items-center gap-1 whitespace-nowrap rounded-lg px-2 text-sm font-medium text-primary hover:bg-surface-100 focus-visible:outline-2 focus-visible:outline-primary dark:hover:bg-surface-800"
                    :class="restricted ? 'opacity-50' : ''"
                    :title="restricted ? t('dm.restrictedTitle') : undefined"
                    data-testid="dm-new"
                >
                    <i class="pi pi-pencil text-xs" aria-hidden="true" />
                    {{ t('dm.newLabel') }}
                </NuxtLink>
            </header>

            <div class="min-h-0 flex-1 lg:overflow-y-auto" :aria-busy="status === 'loading'">
                <div class="space-y-2 p-3">
                    <!-- 법적 고지 — 늘 보인다 -->
                    <DmNotice data-testid="dm-inbox-notice">{{ t('dm.inboxNotice') }}</DmNotice>
                    <DmNotice v-if="restricted" tone="red" :title="t('dm.restrictedTitle')" :text="t('dm.restrictedText')" data-testid="dm-inbox-restricted" />
                    <DmNotice v-if="me && !me.acceptDm" tone="muted" :text="t('dm.refusingText')" :link="{ label: t('dm.refusingLink'), to: UtilDm.SETTINGS_PATH }" data-testid="dm-inbox-refusing" />
                </div>

                <div v-if="status === 'loading' || status === 'idle'" role="status" :aria-label="t('dm.inboxLoading')" data-testid="dm-inbox-loading">
                    <div v-for="n in 5" :key="n" class="flex items-center gap-3 border-b border-surface px-4 py-3.5" aria-hidden="true">
                        <div class="flex-1 space-y-2">
                            <div class="h-3.5 animate-pulse rounded bg-surface-200 dark:bg-surface-800" :class="n % 2 ? 'w-1/3' : 'w-1/4'" />
                            <div class="h-3 animate-pulse rounded bg-surface-200 dark:bg-surface-800" :class="n % 2 ? 'w-4/5' : 'w-3/5'" />
                        </div>
                        <div class="h-3 w-9 animate-pulse rounded bg-surface-200 dark:bg-surface-800" />
                    </div>
                </div>

                <ErrorState v-else-if="status === 'error'" :title="t('dm.inboxLoadFailed')" :description="errorMessage" retry data-testid="dm-inbox-error" @retry="load" />

                <template v-else>
                    <EmptyState v-if="items.length === 0" icon="pi pi-envelope" :title="t('dm.inboxEmptyTitle')" :description="t('dm.inboxEmptyText')" data-testid="dm-inbox-empty">
                        <Button as="router-link" :to="UtilDm.newPath()" :label="t('dm.newWrite')" />
                    </EmptyState>

                    <template v-else>
                        <ul class="m-0 list-none border-t border-surface p-0" :aria-label="t('dm.threadList')" data-testid="dm-thread-list">
                            <DmThreadRow v-for="item in items" :key="item.id" :item="item" :active="item.id === activeId" :now="now" />
                        </ul>
                        <div v-if="hasNext" class="py-4 text-center">
                            <Button type="button" :label="t('dm.moreThreads')" severity="secondary" variant="outlined" class="min-h-11" :loading="loadingMore" data-testid="dm-more" @click="loadMore" />
                            <p v-if="moreError" class="mt-2 px-4 text-xs text-red-600 dark:text-red-400" role="alert">{{ moreError }}</p>
                        </div>
                    </template>
                </template>
            </div>
        </section>

        <!-- ── 오른쪽 칸 — 대화방 · 새 쪽지 · 안내 ── -->
        <div class="flex min-h-0 min-w-0 flex-1 flex-col" :class="atList ? 'max-lg:hidden' : ''">
            <NuxtPage />
        </div>
    </div>
</template>

<script setup lang="ts">
import UtilDm from '~/utils/util-dm';

/**
 * 쪽지 — `/messages`(쪽지함) · `/messages/{threadId}`(대화방) · `/messages/new?to=`(새 쪽지)의 **부모 라우트**(community `pages/messages.vue`, `docs/api-dm.md`).
 * 쪽지함 목록을 여기 한 번만 두어 데스크톱에서 대화를 눌러도 목록이 다시 그려지지 않는다. 상태·서버 호출은 `useDmInbox`(대화방이 읽음·보내기·삭제를 바로 적어 두는 공유 상태).
 * 보이는 동안 약 60초마다 첫 쪽을 다시 받아 합친다(`useDmPoll`). 안쪽 화면(`shell: 'inner'`)인지는 자식 페이지가 정한다. 로그인 필요, `noindex,nofollow`.
 * API 는 `useDmApi` — `docs/features/member-social-contract.md`.
 */
definePageMeta({ middleware: 'auth' });
const { t } = useI18n();
const { siteLabel } = useSiteName();
useSeoMeta({ title: () => t('dm.inboxTitle'), description: () => t('dm.inboxSeo', { site: siteLabel.value }), robots: 'noindex,nofollow' });

const route = useRoute();
const { status, items, hasNext, errorMessage, me, loadingMore, moreError, load, refresh, loadMore } = useDmInbox();

/** 쪽지함 목록 화면인가(대화방·새 쪽지가 아니다) */
const atList = computed(() => route.path.replace(/\/+$/, '') === UtilDm.INBOX_PATH);
/** 데스크톱 2단에서 강조할 대화 */
const activeId = computed(() => (typeof route.params.id === 'string' ? route.params.id : null));
/** 정지 중이면 붉은 안내 + 「새 쪽지」를 흐리게(받은 쪽지 읽기는 된다) */
const restricted = computed(() => me.value?.sender.reason === 'ACCOUNT_RESTRICTED');

/**
 * 틀의 모양 — 모바일은 가장자리까지 채운다(`-m-4` 로 본문 여백을 걷는다. 대화방·새 쪽지는 화면 높이 전체), 태블릿(md~lg)은 카드 한 칸,
 * 데스크톱(lg)은 300px + 나머지의 2단(높이는 셸 막대를 뺀 화면 — 목록·대화방이 각자 안에서 스크롤).
 */
const frameClass = computed(() => [
    'flex flex-col bg-surface-0 max-md:-m-4 md:overflow-hidden md:rounded-xl md:border md:border-surface dark:bg-surface-900',
    'lg:grid lg:h-[calc(100dvh-6rem)] lg:min-h-[28rem] lg:grid-cols-[300px_minmax(0,1fr)] lg:grid-rows-[minmax(0,1fr)]',
    atList.value ? '' : 'max-md:h-dvh md:h-[calc(100dvh-6rem)] md:min-h-[28rem]',
]);

/** 상대 시각("5분 전")의 기준 — 조회할 때마다 새로 맞춘다 */
const now = ref(Date.now());
async function poll() {
    now.value = Date.now();
    await refresh();
}
useDmPoll(poll, UtilDm.INBOX_POLL_MS);
void load();

// 쪽지함으로 돌아오면(대화방 ‹ · 새 쪽지 · 대화 삭제) 조용히 새로 맞춘다
watch(atList, (nowAtList, before) => {
    if (nowAtList && before === false) void poll();
});
</script>
