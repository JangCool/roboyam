<template>
    <!--
        알림 서랍 — 종이 연다. 열리는 쪽은 설정의 "서랍 위치"를 따른다. 목록(안 읽음 표시) · 모두 읽음 · 비었을 때 안내 · 불러오기 실패 · 「더 보기」(처음 30건, 끝이면 숨김).
        항목을 누르면 읽음 처리하고 이동 경로(`link`)가 있으면 그 화면으로 간다(그 화면을 열 권한이 없으면 읽음만 — `useNotification().follow`).
        서랍이 열려 있는 동안은 새 알림을 묻지 않고(폴링 멈춤) 열 때마다 목록을 새로 받는다. 안 읽은 수(`unread`)는 상단 종과 같은 상태(서버가 센 값)다.
        알림 제목·본문은 서버가 요청 언어로 준다. 심각도 4단계(안내·완료·주의·긴급)는 아이콘 모양과 색이 다르고, 항목 아래 줄에 영역(이벤트) 아이콘과 이름을 보인다.
        서랍은 동시에 하나만 연다(PrimeVue Drawer 의 초점·Esc 처리를 그대로 쓴다).
    -->
    <Drawer v-model:visible="open" :position="prefs.drawerPosition" :aria-labelledby="titleId" :style="{ width: '26rem', maxWidth: '94vw' }" :pt="{ content: { class: 'p-0!' } }">
        <template #header>
            <div class="flex min-w-0 flex-1 items-center gap-2.5">
                <span class="grid size-8 shrink-0 place-items-center rounded-full bg-surface-100 text-muted-color dark:bg-surface-800" aria-hidden="true">
                    <i class="pi pi-bell" />
                </span>
                <div class="min-w-0">
                    <h2 :id="titleId" class="text-base font-semibold">{{ t('notification.title') }}</h2>
                    <p v-if="items" class="truncate text-xs text-muted-color">{{ unread > 0 ? t('notification.unreadSummary', { count: unread }) : t('notification.allReadSummary') }}</p>
                </div>
            </div>
            <Button type="button" :label="t('notification.markAllRead')" icon="pi pi-check" size="small" severity="secondary" variant="text" :disabled="unread === 0" @click="markAllRead()" />
        </template>

        <div class="px-2 pb-3">
            <div v-if="loading && !items" class="flex justify-center py-12">
                <ProgressSpinner style="width: 2rem; height: 2rem" :aria-label="t('common.loading')" />
            </div>

            <EmptyState v-else-if="failed && !items" icon="pi pi-exclamation-circle" :title="t('notification.loadFailed')">
                <Button type="button" :label="t('common.retry')" icon="pi pi-refresh" size="small" severity="secondary" variant="outlined" @click="reload()" />
            </EmptyState>

            <EmptyState v-else-if="!items?.length" icon="pi pi-bell-slash" :title="t('notification.emptyTitle')" :description="t('notification.emptyBody')" />

            <template v-else>
                <ul class="m-0 flex list-none flex-col gap-0.5 p-0">
                    <li v-for="item in items" :key="item.id">
                        <!-- 누르면 읽음으로 바뀌고, 이동 경로가 있으면 그 화면으로 간다 -->
                        <button type="button" class="notification-item" :class="{ 'notification-item--unread': !item.read }" @click="follow(item)">
                            <span class="mt-0.5 grid size-8 shrink-0 place-items-center rounded-full" :class="TONE[item.kind]" aria-hidden="true">
                                <i :class="UtilNotification.kindIcon(item.kind)" />
                            </span>
                            <span class="flex min-w-0 flex-1 flex-col gap-0.5 text-left">
                                <span class="flex items-center gap-2">
                                    <span class="min-w-0 flex-1 truncate text-sm" :class="item.read ? 'font-normal' : 'font-semibold'">{{ item.title }}</span>
                                    <span v-if="!item.read" class="size-2 shrink-0 rounded-full bg-primary" aria-hidden="true" />
                                    <span v-if="!item.read" class="sr-only">{{ t('notification.unreadMark') }}</span>
                                </span>
                                <span v-if="item.body" class="line-clamp-2 text-xs text-muted-color">{{ item.body }}</span>
                                <span class="flex min-w-0 items-center gap-1.5 text-xs text-muted-color">
                                    <!-- 심각도는 색·아이콘 말고 글로도 — 안내(info)는 흔해서 적지 않는다 -->
                                    <span v-if="item.kind !== 'info'" class="sr-only">{{ t(`notification.severity.${item.kind}`) }}</span>
                                    <i v-if="UtilNotification.iconOf(item.eventCode, item.area)" :class="UtilNotification.iconOf(item.eventCode, item.area)" class="text-[11px]" aria-hidden="true" />
                                    <span class="truncate">{{ UtilNotificationMessages.areaLabel(t, item.area) }}</span>
                                    <span aria-hidden="true">·</span>
                                    <span class="shrink-0">{{ time.listLabel(item.createdAt) }}</span>
                                </span>
                            </span>
                            <i v-if="item.link" class="pi pi-angle-right mt-2 shrink-0 text-xs text-muted-color" aria-hidden="true" />
                        </button>
                    </li>
                </ul>

                <!-- 다음 쪽이 있을 때만 -->
                <div v-if="nextCursor" class="flex justify-center pt-3">
                    <Button type="button" :label="t('notification.loadMore')" icon="pi pi-chevron-down" size="small" severity="secondary" variant="outlined" :loading="loadingMore" @click="loadMore()" />
                </div>
            </template>
        </div>
    </Drawer>
</template>

<script setup lang="ts">
import type { NotificationKind } from '~/composables/shell/api/useNotificationApi.types';
import UtilNotification from '~/utils/util-notification';
import UtilNotificationMessages from '~/utils/util-notification-messages';

const { t } = useI18n();
const time = useDisplayTime();
const { prefs } = useLayoutPrefs();
const titleId = useId();
const { items, nextCursor, open, loading, loadingMore, failed, unread, reload, loadMore, markAllRead, follow } = useNotification();

/** 종류별 아이콘 바탕 — 연한 바탕 + 진한 글자(라이트·다크 모두 읽힌다) */
const TONE: Record<NotificationKind, string> = {
    info: 'bg-sky-100 text-sky-700 dark:bg-sky-950 dark:text-sky-300',
    success: 'bg-green-100 text-green-700 dark:bg-green-950 dark:text-green-300',
    warning: 'bg-amber-100 text-amber-700 dark:bg-amber-950 dark:text-amber-300',
    critical: 'bg-red-100 text-red-700 dark:bg-red-950 dark:text-red-300',
};

// 서랍을 열 때마다 새로 받는다(받는 중이면 건너뛴다 — 목록이 이미 있으면 그대로 보이고 뒤에서 갱신된다)
watch(open, (isOpen) => {
    if (isOpen) void reload();
});
</script>
