<template>
    <!-- 토스트 자리 — 항상 떠 있는 안내 영역(aria-live)이라 스크린리더가 새 토스트를 읽는다. 화면 아래 가운데 -->
    <div
        class="pointer-events-none fixed inset-x-0 bottom-[calc(1.5rem+env(safe-area-inset-bottom))] z-[2000] flex flex-col items-center gap-2 px-4"
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
            <button
                type="button"
                class="-mr-1 shrink-0 rounded p-1 text-xs opacity-80 hover:opacity-100 focus-visible:outline-2 focus-visible:outline-offset-2"
                :aria-label="t('common.closeNotice')"
                @click="dismiss(toast.id)"
            >
                <i class="pi pi-times text-xs" aria-hidden="true" />
            </button>
        </div>
    </div>
</template>

<script setup lang="ts">
import type { ToastTone } from '~/composables/common/useAppToast';

/** 토스트를 그리는 곳 — 레이아웃에 한 번만 놓는다. 띄우는 것은 `useAppToast().show` */
const { t } = useI18n();
const { toasts, dismiss } = useAppToast();

const TONE: Record<ToastTone, string> = {
    default: 'bg-surface-900 text-surface-0 dark:bg-surface-100 dark:text-surface-900',
    success: 'bg-green-700 text-white dark:bg-green-600',
    error: 'bg-red-700 text-white dark:bg-red-600',
};
</script>
