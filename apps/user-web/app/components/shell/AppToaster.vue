<template>
    <!-- 토스트 자리 — 항상 떠 있는 안내 영역(aria-live)이라 스크린리더가 새 토스트를 읽는다. 하단 탭 위(모바일, 안쪽 화면은 아래 붙는 입력 바 위)·아래 가운데(데스크톱) -->
    <div
        class="pointer-events-none fixed inset-x-0 z-[2000] flex flex-col items-center gap-2 px-4 md:bottom-6"
        :class="withTabs ? 'bottom-[calc(4.75rem+env(safe-area-inset-bottom))]' : 'bottom-[calc(7rem+env(safe-area-inset-bottom))]'"
        role="status"
        aria-live="polite"
        aria-atomic="false"
    >
        <div
            v-for="toast in toasts"
            :key="toast.id"
            class="pointer-events-auto flex w-full max-w-md items-center justify-between gap-3 rounded-xl px-4 py-2.5 text-sm shadow-lg"
            :class="TONE[toast.tone]"
        >
            <span class="min-w-0 break-words">{{ toast.message }}</span>
            <span class="flex shrink-0 items-center gap-1">
                <!-- 행동 단추(차단·차단 해제의 "실행 취소") — 있을 때만 -->
                <button
                    v-if="toast.actionLabel"
                    type="button"
                    class="rounded px-1.5 py-1 text-xs font-bold underline underline-offset-2 hover:opacity-90 focus-visible:outline-2 focus-visible:outline-offset-2"
                    data-testid="toast-action"
                    @click="act(toast.id)"
                >
                    {{ toast.actionLabel }}
                </button>
                <button
                    type="button"
                    class="-mr-1 rounded p-1 text-xs opacity-80 hover:opacity-100 focus-visible:outline-2 focus-visible:outline-offset-2"
                    :aria-label="t('common.closeNotice')"
                    @click="dismiss(toast.id)"
                >
                    <i class="pi pi-times text-xs" aria-hidden="true" />
                </button>
            </span>
        </div>
    </div>
</template>

<script setup lang="ts">
import type { ToastTone } from '~/composables/common/useAppToast';

/** 토스트를 그리는 곳 — 레이아웃에 한 번만 놓는다. 띄우는 것은 `useAppToast().show` */
interface Props {
    /** 하단 탭이 있는 화면이면 그 위에, 없는 안쪽 화면이면 아래에 붙는 입력 바 위에 띄운다 */
    withTabs?: boolean;
}

const props = withDefaults(defineProps<Props>(), {
    withTabs: false,
});

const { t } = useI18n();
const { toasts, dismiss, act } = useAppToast();

const TONE: Record<ToastTone, string> = {
    default: 'bg-surface-900 text-surface-0 dark:bg-surface-100 dark:text-surface-900',
    success: 'bg-green-700 text-white dark:bg-green-600',
    error: 'bg-red-700 text-white dark:bg-red-600',
};
</script>
