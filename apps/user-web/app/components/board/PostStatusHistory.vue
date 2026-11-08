<template>
    <!--
        처리 이력(운영 게시판 글 보기 — 상태 딱지 아래) — 접기, 기본 접힘. 펼치면 부른 화면이 이력을 받는다(처음 한 번 · 상태를 바꾼 뒤 다시).
        한 줄 = 시각 · 「접수 → 확인 중」(처음 정해진 줄은 상태만) · 바꾼 쪽(운영자 / 게시판 운영진 / 소모임 운영진) · 메모(있으면 다음 줄). 비면 「아직 바뀐 적이 없어요」.
        서버 호출은 부른 화면 몫 — 이 부품은 그리기만 한다.
    -->
    <div class="mt-1.5" data-testid="post-status-history">
        <button
            type="button"
            class="inline-flex items-center gap-1 rounded text-xs font-medium text-muted-color hover:text-color focus-visible:outline-2 focus-visible:outline-primary"
            :aria-expanded="open"
            :aria-controls="listId"
            data-testid="post-status-history-toggle"
            @click="emit('update:open', !open)"
        >
            <i class="pi text-[0.625rem]" :class="open ? 'pi-chevron-up' : 'pi-chevron-down'" aria-hidden="true" />
            {{ t('post.statusHistory') }}
        </button>
        <div v-if="open" :id="listId" class="mt-1.5 rounded-lg border border-surface px-3 py-2" :aria-busy="state === 'loading'">
            <div v-if="state === 'loading' || state === 'idle'" role="status" :aria-label="t('common.loading')" class="flex flex-col gap-2 py-1">
                <div v-for="n in 2" :key="n" class="h-3 animate-pulse rounded bg-surface-200 dark:bg-surface-800" :class="n === 1 ? 'w-3/4' : 'w-1/2'" />
            </div>
            <p v-else-if="state === 'error'" class="m-0 flex flex-wrap items-center gap-2 text-xs text-red-600 dark:text-red-400" data-testid="post-status-history-error">
                {{ errorMessage }}
                <Button type="button" :label="t('common.retry')" size="small" severity="secondary" variant="text" @click="emit('retry')" />
            </p>
            <p v-else-if="items.length === 0" class="m-0 text-xs text-muted-color" data-testid="post-status-history-empty">{{ t('post.statusHistoryEmpty') }}</p>
            <ol v-else class="m-0 flex list-none flex-col divide-y divide-surface p-0">
                <li v-for="(item, i) in items" :key="`${item.changedAt}-${i}`" class="py-1.5 text-xs leading-relaxed" data-testid="post-status-history-item">
                    <div class="flex flex-wrap items-center gap-x-1.5 gap-y-0.5">
                        <time :datetime="item.changedAt" :title="time.full(item.changedAt)" class="tabular-nums text-muted-color">{{ time.detail(item.changedAt) }}</time>
                        <span aria-hidden="true" class="text-muted-color">·</span>
                        <span class="font-medium">
                            <template v-if="item.fromStatus">{{ t(UtilPostOps.labelKey(item.fromStatus)) }} <span aria-hidden="true">→</span><span class="sr-only">{{ t('post.statusHistoryTo') }}</span> </template>{{ t(UtilPostOps.labelKey(item.toStatus)) }}
                        </span>
                        <span aria-hidden="true" class="text-muted-color">·</span>
                        <span class="text-muted-color">{{ t(UtilPostOps.actorKey(item.actor)) }}</span>
                    </div>
                    <p v-if="item.note" class="m-0 mt-0.5 whitespace-pre-line break-words text-color [overflow-wrap:anywhere]">{{ item.note }}</p>
                </li>
            </ol>
        </div>
    </div>
</template>

<script setup lang="ts">
import type { PostProcessStatusHistoryItem } from '~/composables/board/api/usePostApi.types';
import UtilPostOps from '~/utils/util-post-ops';

interface Props {
    /** 펼쳤는가(`v-model:open`) */
    open: boolean;
    /** 받기 상태 — `idle` 은 아직 부르지 않음 */
    state: 'idle' | 'loading' | 'ready' | 'error';
    items?: PostProcessStatusHistoryItem[];
    errorMessage?: string;
}

withDefaults(defineProps<Props>(), {
    items: () => [],
    errorMessage: '',
});

const emit = defineEmits<{
    'update:open': [value: boolean];
    retry: [];
}>();

const { t } = useI18n();
const time = useDisplayTime();
const listId = useId();
</script>
