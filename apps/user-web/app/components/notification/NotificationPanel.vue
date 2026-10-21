<template>
    <!-- 데스크톱 알림 패널(드롭다운)의 안쪽 — 최근 알림 + 알림 모두 보기. `NotificationBell` 의 팝오버가 열릴 때 만들어져 목록을 새로 받는다.
         ⚠ 단순 버전 — 원본(community)의 패널은 알림 종류별 행(`NotificationRow`)·모두 읽음·읽음 처리가 있다. 알림 화면을 만들 때 원본으로 바꾼다 -->
    <section class="flex max-h-[min(34rem,calc(100dvh-5rem))] w-full flex-col" aria-labelledby="notification-panel-title" data-testid="notification-panel">
        <header class="flex items-center gap-1 border-b border-surface py-1.5 pl-4 pr-2">
            <!-- 팝오버가 열리면 이 제목에 초점이 간다(autofocus) — 키보드 사용자가 바로 패널 안에서 탭을 시작한다 -->
            <h2 id="notification-panel-title" tabindex="-1" autofocus class="flex-1 text-sm font-semibold">{{ t('notification.title') }}</h2>
            <NuxtLink
                to="/notifications/settings"
                class="grid size-9 place-items-center rounded-full text-muted-color hover:bg-surface-100 focus-visible:outline-2 focus-visible:outline-primary dark:hover:bg-surface-800"
                :aria-label="t('notification.settings')"
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

            <!-- 최근 알림 -->
            <ul v-else class="m-0 list-none p-0" :aria-label="t('notification.recentList')" data-testid="panel-list">
                <li v-for="item in items" :key="item.id" class="flex items-start gap-3 border-b border-surface px-4 py-3 text-sm">
                    <i class="pi pi-comment mt-0.5 text-muted-color" aria-hidden="true" />
                    <span class="min-w-0 flex-1" :class="item.unread ? 'font-semibold' : 'text-muted-color'">
                        {{ item.text }}
                        <small class="mt-0.5 block text-xs font-normal text-muted-color">{{ item.time }}</small>
                    </span>
                    <span v-if="item.unread" class="mt-1.5 size-2 shrink-0 rounded-full bg-primary" :aria-label="t('notification.unread')" />
                </li>
            </ul>
        </div>

        <footer class="flex items-center justify-between gap-2 border-t border-surface bg-surface-50 px-4 py-1 text-sm dark:bg-surface-950/50">
            <NuxtLink
                to="/notifications"
                class="inline-flex min-h-10 items-center rounded px-1 font-semibold text-primary hover:underline focus-visible:outline-2 focus-visible:outline-primary"
                data-testid="panel-all"
                @click="$emit('close')"
            >
                {{ t('notification.viewAll') }}
            </NuxtLink>
        </footer>
    </section>
</template>

<script setup lang="ts">
import type { MockNotification } from '~/composables/notification/api/useNotificationApi.types';

defineEmits<{ close: [] }>();

const { t } = useI18n();
const api = useNotificationApi();

const status = ref<'loading' | 'ready' | 'error'>('loading');
const items = ref<MockNotification[]>([]);

const summary = computed(() => {
    if (status.value === 'loading') return t('notification.loadingSummary');
    if (status.value === 'error') return t('notification.loadFailed');
    if (items.value.length === 0) return t('notification.noneSummary');
    return t('notification.summary', { total: items.value.length, unread: items.value.filter((i) => i.unread).length });
});

async function load() {
    status.value = 'loading';
    try {
        items.value = await api.recent();
        status.value = 'ready';
    } catch {
        status.value = 'error';
    }
}

// 패널은 열릴 때 만들어진다 — 열 때마다 목록을 새로 받는다
void load();
</script>
