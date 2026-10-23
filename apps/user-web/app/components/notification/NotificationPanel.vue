<template>
    <!-- 데스크톱 알림 패널(드롭다운)의 안쪽 — 최근 알림 8개 + 「모두 읽음」. `NotificationBell` 의 팝오버가 열릴 때 만들어져 목록을 새로 받는다(`GET /api/notifications?size=8`).
         한 줄을 누르면 읽음으로 바꾼 뒤(`POST …/{id}/read`) 서버가 준 경로(`link`)로 간다 — 갈 곳이 없는 알림(포인트 회수·글 숨김)은 읽음만 하고 패널에 남는다.
         문장(`title`)·둘째 줄(`body`)은 서버가 요청 언어로 푼 그대로다(묶인 댓글·탈퇴한 회원·임시 숨김 문구 포함).
         머리 줄의 톱니는 알림 설정(`/settings/notifications`), 바닥 줄의 「알림 모두 보기」는 알림 화면(`/notifications`)으로 간다(이동하면 종이 패널을 닫는다) -->
    <section class="flex max-h-[min(34rem,calc(100dvh-5rem))] w-full flex-col" aria-labelledby="notification-panel-title" data-testid="notification-panel">
        <header class="flex items-center gap-1 border-b border-surface py-1.5 pl-4 pr-2">
            <!-- 팝오버가 열리면 이 제목에 초점이 간다(autofocus) — 키보드 사용자가 바로 패널 안에서 탭을 시작한다 -->
            <h2 id="notification-panel-title" tabindex="-1" autofocus class="flex-1 text-sm font-semibold">{{ t('notification.title') }}</h2>
            <NuxtLink
                to="/settings/notifications"
                class="grid size-9 place-items-center rounded-full text-muted-color hover:bg-surface-100 focus-visible:outline-2 focus-visible:outline-primary dark:hover:bg-surface-800"
                :aria-label="t('notification.settingsLink')"
                data-testid="panel-settings"
                @click="$emit('close')"
            >
                <i class="pi pi-cog text-sm" aria-hidden="true" />
            </NuxtLink>
            <button
                type="button"
                class="grid size-9 place-items-center rounded-full text-muted-color hover:bg-surface-100 focus-visible:outline-2 focus-visible:outline-primary dark:hover:bg-surface-800"
                :aria-label="t('notification.panelClose')"
                data-testid="panel-close"
                @click="$emit('close')"
            >
                <i class="pi pi-times text-sm" aria-hidden="true" />
            </button>
        </header>

        <!-- 상태를 스크린리더가 읽는다 -->
        <p role="status" aria-live="polite" class="sr-only">{{ summary }}</p>

        <div class="min-h-0 flex-1 overflow-y-auto" :aria-busy="status === 'loading'">
            <!-- 로딩 -->
            <div v-if="status === 'loading'" aria-hidden="true" data-testid="panel-loading">
                <div v-for="n in 4" :key="n" class="flex items-start gap-3 border-b border-surface px-4 py-3">
                    <div class="size-9 shrink-0 animate-pulse rounded-full bg-surface-200 dark:bg-surface-800" />
                    <div class="flex-1 pt-1">
                        <div class="h-3.5 animate-pulse rounded bg-surface-200 dark:bg-surface-800" :class="n % 2 ? 'w-11/12' : 'w-2/3'" />
                        <div class="mt-2 h-3 w-2/5 animate-pulse rounded bg-surface-200 dark:bg-surface-800" />
                    </div>
                </div>
            </div>

            <!-- 오류 -->
            <div v-else-if="status === 'error'" class="px-5 py-8 text-center" role="alert" data-testid="panel-error">
                <p class="text-sm font-semibold">{{ t('notification.loadFailed') }}</p>
                <Button type="button" :label="t('common.retry')" size="small" class="mt-3" @click="load" />
            </div>

            <!-- 비어 있음 -->
            <EmptyState v-else-if="items.length === 0" :title="t('notification.emptyTitle')" :description="t('notification.emptyDescription')" icon="pi pi-bell" data-testid="panel-empty" />

            <!-- 최근 알림 — 한 줄 전체가 링크(갈 곳이 있을 때) 또는 단추다. 링크는 `NuxtLink` 가 아니라 `<a href>` 다 — 라우터 링크는 누르자마자 이동해 「읽음 후 이동」 순서를 지킬 수 없다.
                 (`<component is="button">` 은 쓰지 않는다 — 전역 PrimeVue `Button` 으로 풀려 버튼 모양이 된다) -->
            <ul v-else class="m-0 list-none p-0" :aria-label="t('notification.recentList')" data-testid="panel-list">
                <li v-for="item in items" :key="item.id" class="border-b border-surface last:border-b-0">
                    <a v-if="linkOf(item)" :href="linkOf(item) ?? undefined" :class="ROW_CLASS" :aria-label="rowLabel(item)" data-testid="panel-item" @click="onItemClick($event, item)">
                        <i :class="[UtilNotification.icon(item.type), 'mt-0.5 text-muted-color']" aria-hidden="true" />
                        <span class="min-w-0 flex-1" :class="item.read ? 'text-muted-color' : 'font-semibold'">
                            <span class="block break-words">{{ item.title }}</span>
                            <span v-if="item.body" class="mt-0.5 block truncate text-xs font-normal text-muted-color">{{ item.body }}</span>
                            <time class="mt-0.5 block text-xs font-normal text-muted-color" :datetime="item.createdAt" :title="time.full(item.createdAt)">{{ time.ago(item.createdAt) }}</time>
                        </span>
                        <span v-if="!item.read" class="mt-1.5 size-2 shrink-0 rounded-full bg-primary" aria-hidden="true" />
                    </a>
                    <button v-else type="button" :class="ROW_CLASS" :aria-label="rowLabel(item)" data-testid="panel-item" @click="onItemClick($event, item)">
                        <i :class="[UtilNotification.icon(item.type), 'mt-0.5 text-muted-color']" aria-hidden="true" />
                        <span class="min-w-0 flex-1" :class="item.read ? 'text-muted-color' : 'font-semibold'">
                            <span class="block break-words">{{ item.title }}</span>
                            <span v-if="item.body" class="mt-0.5 block truncate text-xs font-normal text-muted-color">{{ item.body }}</span>
                            <time class="mt-0.5 block text-xs font-normal text-muted-color" :datetime="item.createdAt" :title="time.full(item.createdAt)">{{ time.ago(item.createdAt) }}</time>
                        </span>
                        <span v-if="!item.read" class="mt-1.5 size-2 shrink-0 rounded-full bg-primary" aria-hidden="true" />
                    </button>
                </li>
            </ul>
        </div>

        <!-- 바닥 줄 — 「알림 모두 보기」는 늘, 「모두 읽음」은 안 읽은 알림이 있을 때만 -->
        <footer class="flex items-center justify-between gap-2 border-t border-surface bg-surface-50 px-4 py-1 text-sm dark:bg-surface-950/50">
            <NuxtLink
                to="/notifications"
                class="inline-flex min-h-10 items-center rounded px-1 text-muted-color hover:text-color hover:underline focus-visible:outline-2 focus-visible:outline-primary"
                data-testid="panel-view-all"
                @click="$emit('close')"
            >
                {{ t('notification.viewAll') }}
            </NuxtLink>
            <button
                v-if="status === 'ready' && hasUnread"
                type="button"
                class="inline-flex min-h-10 items-center rounded px-1 font-semibold text-primary hover:underline focus-visible:outline-2 focus-visible:outline-primary disabled:opacity-60"
                :disabled="readingAll"
                data-testid="panel-read-all"
                @click="onReadAll"
            >
                {{ t('notification.readAll') }}
            </button>
        </footer>
    </section>
</template>

<script setup lang="ts">
import type { MemberNotificationItem } from '~/composables/notification/api/useNotificationApi.types';
import UtilNotification from '~/utils/util-notification';

const emit = defineEmits<{ close: [] }>();

/** 한 줄(링크·단추 같은 모양) */
const ROW_CLASS =
    'flex w-full items-start gap-3 px-4 py-3 text-left text-sm hover:bg-surface-100 focus-visible:outline-2 focus-visible:-outline-offset-2 focus-visible:outline-primary dark:hover:bg-surface-800';

const { t } = useI18n();
const api = useNotificationApi();
const badge = useNotificationBadge();
const toast = useAppToast();
const time = useDisplayTime();

const status = ref<'loading' | 'ready' | 'error'>('loading');
const items = ref<MemberNotificationItem[]>([]);
const readingAll = ref(false);

/** 화면에 불러온 안 읽은 알림 수 — 배지가 아니라 이 목록 기준(「모두 읽음」 낙관적 갱신에 쓴다) */
const unreadShown = computed(() => items.value.filter((i) => !i.read).length);
/** 「모두 읽음」을 보일까 — 목록에 안 읽은 것이 있거나, 목록 밖(9번째 이후)에 안 읽은 것이 남았을 때 */
const hasUnread = computed(() => unreadShown.value > 0 || badge.count.value > 0);

const summary = computed(() => {
    if (status.value === 'loading') return t('notification.loadingSummary');
    if (status.value === 'error') return t('notification.loadFailed');
    if (items.value.length === 0) return t('notification.noneSummary');
    return t('notification.summary', { total: items.value.length, unread: unreadShown.value });
});

/** 한 줄의 이름(스크린리더용) — 문장 + 안 읽음 여부(점은 눈으로만 보는 것이라 이름에 넣는다) */
function rowLabel(item: MemberNotificationItem): string {
    const parts = [item.title, item.body, time.value.ago(item.createdAt)].filter(Boolean);
    return item.read ? parts.join(', ') : `${t('notification.unread')}, ${parts.join(', ')}`;
}

/** 앱 안 경로만 링크로 그린다(서버 경로가 아니면 읽음 처리만) */
function linkOf(item: MemberNotificationItem): string | null {
    return UtilNotification.internalLink(item.link);
}

async function load() {
    status.value = 'loading';
    try {
        items.value = await api.recent();
        status.value = 'ready';
    } catch {
        status.value = 'error';
    }
}

/** 읽음으로 바꾼다 — 낙관적으로 점을 지우고 배지를 줄인 뒤 서버에 알린다. 실패는 조용히 넘기고(`404` = 이미 정리된 알림) 배지를 서버 값에 맞춘다 */
async function markRead(item: MemberNotificationItem): Promise<void> {
    if (item.read) return;
    item.read = true;
    badge.markedRead(true);
    try {
        await api.markRead(item.id);
    } catch {
        /* 다음 조회가 서버 값으로 맞춘다 */
    } finally {
        void badge.refresh();
    }
}

/**
 * 한 줄을 눌렀다 — 읽음 처리 후 `link` 로 간다(이동하면 종이 패널을 닫는다). 새 탭으로 여는 누르기(Ctrl·⌘·Shift·가운데 단추)는 브라우저에 맡기고 읽음만 한다.
 * 갈 곳이 없으면 읽음만 하고 패널에 남는다.
 */
async function onItemClick(event: MouseEvent, item: MemberNotificationItem) {
    const link = linkOf(item);
    if (link && (event.ctrlKey || event.metaKey || event.shiftKey || event.button !== 0)) {
        void markRead(item);
        return;
    }
    event.preventDefault();
    await markRead(item);
    if (link) {
        emit('close');
        await navigateTo(link);
    }
}

/** 「모두 읽음」 — 목록의 점을 바로 지우고 배지를 줄인 뒤 서버에 알린다. 실패하면 알리고 목록·배지를 서버 값으로 다시 받는다 */
async function onReadAll() {
    if (readingAll.value) return;
    readingAll.value = true;
    for (const item of items.value) item.read = true;
    // 서버는 목록 밖의 것까지 모두 읽음으로 바꾼다 — 배지를 지금 숫자만큼 줄여 0 으로
    badge.markedAllRead(badge.count.value);
    try {
        await api.markAllRead();
    } catch {
        toast.error(t('notification.readAllFailed'));
        void load();
    } finally {
        readingAll.value = false;
        void badge.refresh();
    }
}

// 패널은 열릴 때 만들어진다 — 열 때마다 목록을 새로 받는다
void load();
</script>
