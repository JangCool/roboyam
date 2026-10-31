<template>
    <!--
        신청 건 심사 왼쪽 칸 — 지금 탭(큐)의 신청 건 목록. 심사 대기는 오래된 것이 위, 결제·입금 대기는 기한이 가까운 것이 위(서버 정렬).
        한 줄: 번호 · 「운영자 등록」(source=OPERATOR) · 종류 · 사업자 · 기간 · 금액 · 기한(남음/지남) · 「시작 지남」(F6) · 환불 요청. 누르면 `select`.
    -->
    <div class="flex h-full min-h-0 flex-col">
        <ErrorState v-if="error" :title="t('bizApplication.loadFailed')" :description="error.message" :retry="true" :retrying="loading" @retry="emit('retry')" />
        <div v-else-if="loading && !items.length" class="flex flex-col gap-3 p-3" role="status" :aria-label="t('common.loading')">
            <Skeleton v-for="n in 5" :key="n" height="3.5rem" />
        </div>
        <EmptyState v-else-if="!items.length" icon="pi pi-check-circle" :title="t(`bizApplication.empty.${queue}`)" />
        <ul v-else class="m-0 flex min-h-0 list-none flex-col overflow-y-auto p-0" :aria-label="t('bizApplication.listLabel')">
            <li v-for="row in items" :key="row.id" class="border-b border-(--line)">
                <button
                    type="button"
                    class="flex w-full flex-col gap-1 px-3 py-2.5 text-left transition-colors hover:bg-(--p-content-hover-background)"
                    :class="row.id === selectedId ? 'bg-(--p-highlight-background) text-(--p-highlight-color)' : ''"
                    :aria-current="row.id === selectedId ? 'true' : undefined"
                    @click="emit('select', row.id)"
                >
                    <span class="flex items-center justify-between gap-2">
                        <code class="text-[11px] text-muted-color">{{ row.no }}</code>
                        <span class="flex items-center gap-1">
                            <Tag v-if="row.source === 'OPERATOR'" :value="t('bizApplication.sourceOperator')" severity="info" />
                            <Tag :value="t(`bizApplication.kindShort.${row.kind}`)" severity="secondary" />
                        </span>
                    </span>
                    <span class="truncate text-[13px] font-medium">{{ row.business.name }}<span v-if="row.adType" class="font-normal text-muted-color"> · {{ t(`bizApplication.adType.${row.adType}`) }}</span></span>
                    <span class="text-[11px] tabular-nums text-muted-color">{{ UtilBizApplicationMessages.period(t, time, row.period) }}</span>
                    <span class="flex flex-wrap items-center gap-1">
                        <span class="text-[12px] tabular-nums">{{ UtilBizApplicationMessages.won(t, row.total, locale) }}</span>
                        <Tag v-if="row.deadlineAt && (queue === 'PAYMENT' || queue === 'DEPOSIT' || queue === 'DEPOSIT_OVERDUE')" :value="UtilBizApplicationMessages.remain(t, row.deadlineAt, now)" :severity="UtilBizApplicationMessages.remainSeverity(row.deadlineAt, now)" icon="pi pi-clock" />
                        <Tag v-if="queue === 'REVIEW' && UtilBizApplication.started(row.period, now)" :value="t('bizApplication.startPassed')" severity="warn" />
                        <Tag v-if="row.hasRefundRequest" :value="t('bizApplication.refundRequested')" severity="danger" />
                        <Tag v-if="queue === 'LIVE' || queue === 'CLOSED'" :value="t(`bizApplication.status.${row.status}`)" :severity="UtilBizApplication.statusSeverity(row.status)" />
                    </span>
                </button>
            </li>
        </ul>
    </div>
</template>

<script setup lang="ts">
import type { ApiError } from '~/composables/common/useApiClient';
import type { BizApplicationQueue, BizApplicationRow } from '~/composables/bizapplication/api/useBizApplicationApi.types';
import UtilBizApplication from '~/utils/util-biz-application';
import UtilBizApplicationMessages from '~/utils/util-biz-application-messages';

interface Props {
    items: BizApplicationRow[];
    queue: BizApplicationQueue;
    selectedId?: string | null;
    loading?: boolean;
    error?: ApiError | null;
    /** 기한 글자를 다시 그릴 지금 시각(부모가 1분마다 바꾼다) */
    now?: number;
}

withDefaults(defineProps<Props>(), { selectedId: null, loading: false, error: null, now: () => Date.now() });
const emit = defineEmits<{ select: [id: string]; retry: [] }>();

const { t, locale } = useI18n();
const time = useDisplayTime();
</script>
