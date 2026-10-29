<template>
    <div class="mx-auto flex max-w-2xl flex-col gap-3">
        <div class="flex items-center gap-2">
            <h1 class="flex-1 text-lg font-bold">{{ t('notification.title') }}</h1>
            <Button
                v-if="status === 'ready' && hasUnread"
                type="button"
                :label="filter === 'all' ? t('notification.readAll') : t('notification.readAllFiltered')"
                size="small"
                severity="secondary"
                variant="outlined"
                :loading="readingAll"
                data-testid="notifications-read-all"
                @click="onReadAll"
            />
            <Button
                as="router-link"
                to="/settings/notifications"
                icon="pi pi-cog"
                size="small"
                severity="secondary"
                variant="text"
                rounded
                :aria-label="t('notification.settingsLink')"
                data-testid="notifications-settings"
            />
        </div>

        <!-- 거르기 — 묶음은 서버(`filter=all|comment|system`), 「안 읽은 것만」은 서버에 거르기가 없어 불러온 알림 안에서만 거른다 -->
        <div class="flex flex-wrap items-center justify-between gap-2">
            <nav class="flex gap-1" :aria-label="t('notification.filterLabel')">
                <button
                    v-for="option in filterOptions"
                    :key="option.value"
                    type="button"
                    class="min-h-9 rounded-full px-3 text-sm focus-visible:outline-2 focus-visible:outline-primary"
                    :class="filter === option.value ? 'bg-surface-900 font-semibold text-surface-0 dark:bg-surface-100 dark:text-surface-900' : 'bg-surface-0 text-muted-color hover:text-color dark:bg-surface-900'"
                    :aria-pressed="filter === option.value"
                    :data-testid="`notifications-filter-${option.value}`"
                    @click="filter = option.value"
                >
                    {{ option.label }}
                </button>
            </nav>
            <label class="flex items-center gap-2 text-sm">
                <ToggleSwitch v-model="unreadOnly" :input-id="`${uid}-unread`" data-testid="notifications-unread-only" />
                <span>{{ t('notification.unreadOnly') }}</span>
            </label>
        </div>

        <!-- 상태를 스크린리더가 읽는다 -->
        <p role="status" aria-live="polite" class="sr-only">{{ summaryText }}</p>

        <section class="panel-card" :aria-busy="status === 'loading'" :aria-label="t('notification.listLabel')">
            <div v-if="status === 'loading'" aria-hidden="true" data-testid="notifications-loading">
                <div v-for="n in 6" :key="n" class="flex items-start gap-3 border-b border-surface px-4 py-3.5 last:border-b-0">
                    <div class="size-5 shrink-0 animate-pulse rounded-full bg-surface-200 dark:bg-surface-800" />
                    <div class="flex-1">
                        <div class="h-3.5 animate-pulse rounded bg-surface-200 dark:bg-surface-800" :class="n % 2 ? 'w-11/12' : 'w-2/3'" />
                        <div class="mt-2 h-3 w-2/5 animate-pulse rounded bg-surface-200 dark:bg-surface-800" />
                    </div>
                </div>
            </div>

            <ErrorState v-else-if="status === 'error'" :title="t('notification.loadFailed')" :description="loadError" retry data-testid="notifications-error" @retry="load(true)" />

            <EmptyState
                v-else-if="items.length === 0"
                :title="t('notification.emptyTitle')"
                :description="filter === 'all' ? t('notification.emptyDescription') : t('notification.emptyFiltered')"
                icon="pi pi-bell"
                data-testid="notifications-empty"
            />

            <!-- 불러온 알림 중 안 읽은 것이 없다 — 더 불러올 것이 있으면 더 보기 -->
            <EmptyState
                v-else-if="shown.length === 0"
                :title="t('notification.unreadNone')"
                :description="hasNext ? t('notification.unreadNoneMore') : ''"
                icon="pi pi-check-circle"
                data-testid="notifications-unread-empty"
            />

            <!-- 한 줄 전체가 링크(갈 곳이 있을 때) 또는 단추 — 링크는 `<a href>` 다(라우터 링크는 누르자마자 이동해 「읽음 후 이동」 순서를 지킬 수 없다) -->
            <ul v-else class="m-0 list-none p-0" data-testid="notifications-list">
                <li v-for="item in shown" :key="item.id" class="border-b border-surface last:border-b-0">
                    <a v-if="linkOf(item)" :href="linkOf(item) ?? undefined" :class="ROW_CLASS" :aria-label="rowLabel(item)" data-testid="notifications-item" @click="onItemClick($event, item)">
                        <i :class="[UtilNotification.icon(item.type), 'mt-0.5 text-muted-color']" aria-hidden="true" />
                        <span class="min-w-0 flex-1" :class="item.read ? 'text-muted-color' : 'font-semibold'">
                            <span class="block break-words">{{ item.title }}</span>
                            <span v-if="item.body" class="mt-0.5 block break-words text-xs font-normal text-muted-color">{{ item.body }}</span>
                            <time class="mt-1 block text-xs font-normal text-muted-color" :datetime="item.createdAt" :title="time.full(item.createdAt)">{{ time.ago(item.createdAt) }}</time>
                        </span>
                        <span v-if="!item.read" class="mt-1.5 size-2 shrink-0 rounded-full bg-primary" aria-hidden="true" />
                    </a>
                    <button v-else type="button" :class="ROW_CLASS" :aria-label="rowLabel(item)" data-testid="notifications-item" @click="onItemClick($event, item)">
                        <i :class="[UtilNotification.icon(item.type), 'mt-0.5 text-muted-color']" aria-hidden="true" />
                        <span class="min-w-0 flex-1" :class="item.read ? 'text-muted-color' : 'font-semibold'">
                            <span class="block break-words">{{ item.title }}</span>
                            <span v-if="item.body" class="mt-0.5 block break-words text-xs font-normal text-muted-color">{{ item.body }}</span>
                            <time class="mt-1 block text-xs font-normal text-muted-color" :datetime="item.createdAt" :title="time.full(item.createdAt)">{{ time.ago(item.createdAt) }}</time>
                        </span>
                        <span v-if="!item.read" class="mt-1.5 size-2 shrink-0 rounded-full bg-primary" aria-hidden="true" />
                    </button>
                </li>
            </ul>

            <div v-if="status === 'ready' && hasNext" class="border-t border-surface p-3 text-center">
                <Button type="button" :label="t('common.more')" size="small" severity="secondary" variant="outlined" :loading="loadingMore" data-testid="notifications-more" @click="load(false)" />
            </div>
        </section>

        <p v-if="retentionDays" class="px-1 text-xs text-muted-color" data-testid="notifications-retention">{{ t('notification.retention', { days: retentionDays }) }}</p>
    </div>
</template>

<script setup lang="ts">
import type { MemberNotificationFilter, MemberNotificationItem } from '~/composables/notification/api/useNotificationApi.types';
import UtilAuthMessages from '~/utils/util-auth-messages';
import UtilNotification from '~/utils/util-notification';

/**
 * 알림 전체 `/notifications` — 로그인 필요(`auth` 미들웨어). 하단 탭 「알림」·알림 패널의 「알림 모두 보기」·내 정보의 「알림」이 온다.
 * 실제 API(`member-notification-contract.md`): 목록(커서 더 보기, 20개씩) · 묶음 거르기(전체·댓글·계정 — 서버 `filter`) · 한 줄 누르면 읽음 + `link` 로 이동(앱 안 경로만) · 모두 읽음(거르기 중이면 그 묶음만) · 빈 상태.
 * 「안 읽은 것만」은 서버에 거르기가 없어 **불러온 알림 안에서만** 거른다(더 보기로 더 불러온다) — 서버에 `unread` 거르기가 생기면 그것으로 바꾼다.
 * 문장(`title`·`body`)은 서버가 요청 언어로 푼 그대로다. 안 읽은 수 배지는 목록 응답의 `unreadCount` 로 맞춘다.
 */
definePageMeta({ middleware: 'auth' });
const { t } = useI18n();
const { siteLabel } = useSiteName();
useSeoMeta({ title: () => t('notification.title'), description: () => t('notification.seoDescription', { site: siteLabel.value }), robots: 'noindex' });

/** 한 줄(링크·단추 같은 모양) */
const ROW_CLASS =
    'flex w-full items-start gap-3 px-4 py-3.5 text-left text-sm hover:bg-surface-100 focus-visible:outline-2 focus-visible:-outline-offset-2 focus-visible:outline-primary dark:hover:bg-surface-800';

const uid = useId();
const api = useNotificationApi();
const badge = useNotificationBadge();
const toast = useAppToast();
const time = useDisplayTime();

const filter = ref<MemberNotificationFilter>('all');
const unreadOnly = ref(false);
const filterOptions = computed<{ value: MemberNotificationFilter; label: string }[]>(() => [
    { value: 'all', label: t('common.all') },
    { value: 'comment', label: t('notification.filterComment') },
    { value: 'system', label: t('notification.filterSystem') },
]);

const status = ref<'loading' | 'ready' | 'error'>('loading');
const loadError = ref('');
const items = ref<MemberNotificationItem[]>([]);
const hasNext = ref(false);
const nextCursor = ref<string | null>(null);
const loadingMore = ref(false);
const readingAll = ref(false);
const retentionDays = ref(0);
/** 서버가 알려 준 전체 안 읽은 수(거르기와 무관) */
const unreadTotal = ref(0);

/** 보일 줄 — 「안 읽은 것만」이면 불러온 것 중 안 읽은 것 */
const shown = computed(() => (unreadOnly.value ? items.value.filter((i) => !i.read) : items.value));
const unreadShown = computed(() => items.value.filter((i) => !i.read).length);
/** 「모두 읽음」을 보일까 — 불러온 것에 안 읽은 것이 있거나, 전체 보기에서 서버가 안 읽은 것이 남았다고 할 때 */
const hasUnread = computed(() => unreadShown.value > 0 || (filter.value === 'all' && unreadTotal.value > 0) || (filter.value !== 'all' && hasNext.value && unreadTotal.value > 0));

const summaryText = computed(() => {
    if (status.value === 'loading') return t('notification.loadingSummary');
    if (status.value === 'error') return t('notification.loadFailed');
    if (items.value.length === 0) return t('notification.noneSummary');
    return t('notification.pageSummary', { total: shown.value.length, unread: unreadShown.value });
});

function rowLabel(item: MemberNotificationItem): string {
    const parts = [item.title, item.body, time.value.ago(item.createdAt)].filter(Boolean);
    return item.read ? parts.join(', ') : `${t('notification.unread')}, ${parts.join(', ')}`;
}

function linkOf(item: MemberNotificationItem): string | null {
    return UtilNotification.internalLink(item.link);
}

let seq = 0;
/** 첫 쪽부터(`reset`) 또는 다음 쪽. 거르기를 바꾸면 앞 요청의 늦은 응답은 버린다. 다음 쪽 실패는 토스트로 알리고 받은 줄은 그대로 둔다 */
async function load(reset: boolean) {
    const mine = ++seq;
    if (reset) {
        status.value = 'loading';
        loadError.value = '';
        nextCursor.value = null;
    } else {
        loadingMore.value = true;
    }
    try {
        const page = await api.list({ filter: filter.value, cursor: reset ? undefined : (nextCursor.value ?? undefined) });
        if (mine !== seq) return;
        items.value = reset ? page.items : [...items.value, ...page.items.filter((n) => !items.value.some((o) => o.id === n.id))];
        hasNext.value = page.hasNext;
        nextCursor.value = page.nextCursor;
        retentionDays.value = page.retentionDays;
        unreadTotal.value = page.unreadCount;
        badge.sync(page.unreadCount);
        status.value = 'ready';
    } catch (e) {
        if (mine !== seq) return;
        const message = e instanceof ApiError ? e.message : UtilAuthMessages.fallback(t, 0, 'NETWORK');
        if (reset) {
            loadError.value = message;
            status.value = 'error';
        } else {
            toast.error(message, 8000);
        }
    } finally {
        if (mine === seq) loadingMore.value = false;
    }
}

watch(filter, () => void load(true));
void load(true);
onBeforeUnmount(() => seq++);

/** 읽음으로 — 낙관적으로 점을 지우고 배지를 줄인 뒤 서버에 알린다. 실패는 조용히 넘기고(`404` = 이미 정리된 알림) 배지를 서버 값에 맞춘다 */
async function markRead(item: MemberNotificationItem): Promise<void> {
    if (item.read) return;
    item.read = true;
    unreadTotal.value = Math.max(0, unreadTotal.value - 1);
    badge.markedRead(true);
    try {
        await api.markRead(item.id);
    } catch {
        /* 다음 조회가 서버 값으로 맞춘다 */
    } finally {
        void badge.refresh();
    }
}

/** 한 줄을 눌렀다 — 읽음 후 `link` 로 간다. 새 탭으로 여는 누르기는 브라우저에 맡기고 읽음만 한다. 갈 곳이 없으면 읽음만 */
async function onItemClick(event: MouseEvent, item: MemberNotificationItem) {
    const link = linkOf(item);
    if (link && (event.ctrlKey || event.metaKey || event.shiftKey || event.button !== 0)) {
        void markRead(item);
        return;
    }
    event.preventDefault();
    await markRead(item);
    if (link) await navigateTo(link);
}

/** 모두 읽음 — 거르기 중이면 그 묶음만(서버 `read-all?filter=`). 목록의 점을 바로 지우고, 실패하면 알리고 다시 받는다 */
async function onReadAll() {
    if (readingAll.value) return;
    readingAll.value = true;
    const unread = unreadShown.value;
    for (const item of items.value) item.read = true;
    if (filter.value === 'all') badge.markedAllRead(badge.count.value);
    else badge.markedAllRead(unread);
    try {
        await api.markAllRead(filter.value);
        if (filter.value === 'all') unreadTotal.value = 0;
        toast.success(t('notification.readAllDone'));
    } catch {
        toast.error(t('notification.readAllFailed'), 8000);
        void load(true);
    } finally {
        readingAll.value = false;
        void badge.refresh();
    }
}
</script>
