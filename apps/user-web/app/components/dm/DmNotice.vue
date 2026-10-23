<template>
    <!-- 쪽지 화면의 안내 상자 — 법적 고지·수신 거부·한도·정지·링크 경고·전송 실패 (community DmNotice).
         색: info 회색 · warn 노랑(한도류) · red 붉음(정지·전송 실패) · muted 진한 회색(수신 거부·차단) · ok 초록 -->
    <div :class="[BASE, TONE[tone]]" :role="isLive ? 'alert' : 'note'">
        <div class="flex items-start gap-2">
            <p class="m-0 min-w-0 flex-1 leading-relaxed">
                <b v-if="title" class="font-semibold">{{ title }}</b>
                <template v-if="title && (text || $slots.default)">{{ ' ' }}</template>
                <slot>{{ text }}</slot>
                <NuxtLink v-if="link" :to="link.to" class="ml-1 whitespace-nowrap underline focus-visible:outline-2 focus-visible:outline-primary">{{ link.label }}</NuxtLink>
            </p>
            <button
                v-if="dismissible"
                type="button"
                class="-m-1 shrink-0 rounded p-1 opacity-70 hover:opacity-100 focus-visible:outline-2 focus-visible:outline-primary"
                :aria-label="t('dm.noticeClose')"
                @click="$emit('dismiss')"
            >
                <i class="pi pi-times text-xs" aria-hidden="true" />
            </button>
        </div>
        <slot name="extra" />
    </div>
</template>

<script setup lang="ts">
import type { DmNoticeLink, DmNoticeTone } from '~/utils/util-dm-messages';

/**
 * 안내 상자 한 개 — 굵은 첫 문장(`title`) + 설명(`text` 또는 기본 슬롯) + 링크 한 줄.
 * 붉은 안내는 스크린리더가 바로 읽도록 `role="alert"` 이고, `live` 로 다른 색에도 켤 수 있다(보내기를 거절당한 직후처럼).
 */
interface Props {
    tone?: DmNoticeTone;
    title?: string;
    text?: string;
    link?: DmNoticeLink | null;
    /** 붉은 안내가 아니어도 바로 읽어 준다 */
    live?: boolean;
    /** 닫기 단추 */
    dismissible?: boolean;
}

const props = withDefaults(defineProps<Props>(), { tone: 'info', title: '', text: '', link: null, live: false, dismissible: false });

defineEmits<{ dismiss: [] }>();

const { t } = useI18n();
const isLive = computed(() => props.live || props.tone === 'red');

const BASE = 'rounded-lg px-3 py-2.5 text-xs';
const TONE: Record<DmNoticeTone, string> = {
    info: 'bg-surface-100 text-muted-color dark:bg-surface-800/60',
    warn: 'border border-amber-200 bg-amber-50 text-amber-950 dark:border-amber-900 dark:bg-amber-950/40 dark:text-amber-100',
    red: 'border border-red-200 bg-red-50 text-red-950 dark:border-red-900 dark:bg-red-950/40 dark:text-red-100',
    muted: 'bg-surface-100 text-color dark:bg-surface-800',
    ok: 'border border-green-200 bg-green-50 text-green-950 dark:border-green-900 dark:bg-green-950/40 dark:text-green-100',
};
</script>
