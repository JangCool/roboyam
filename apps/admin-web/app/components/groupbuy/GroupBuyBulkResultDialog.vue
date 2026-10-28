<template>
    <!--
        일괄 처리 결과(준비중·발송·배송완료) — 줄 단위 부분 성공. 성공 N · 실패 M 과 실패 줄의 주문 번호·서버 코드·서버 문구 그대로(가공하지 않는다). 실패가 있으면 실패만 보기가 기본이다.
    -->
    <Dialog :visible="visible" modal :draggable="false" :header="title" :style="{ width: 'min(40rem, calc(100vw - 2rem))' }" @update:visible="(v: boolean) => emit('update:visible', v)">
        <div v-if="result" class="flex flex-col gap-3">
            <div class="grid grid-cols-2 gap-2">
                <div class="rounded-lg border border-(--line) px-3 py-2">
                    <div class="text-[11px] text-muted-color">{{ t('groupBuy.bulkSucceeded') }}</div>
                    <div class="text-lg font-semibold tabular-nums text-green-700 dark:text-green-400">{{ result.succeeded }}</div>
                </div>
                <div class="rounded-lg border border-(--line) px-3 py-2">
                    <div class="text-[11px] text-muted-color">{{ t('groupBuy.bulkFailed') }}</div>
                    <div class="text-lg font-semibold tabular-nums" :class="result.failed ? 'text-red-600 dark:text-red-400' : ''">{{ result.failed }}</div>
                </div>
            </div>
            <div v-if="result.results.length" class="flex items-center gap-2">
                <Checkbox v-model="failedOnly" binary :input-id="onlyId" />
                <label :for="onlyId" class="text-[13px]">{{ t('groupBuy.bulkFailedOnly') }}</label>
            </div>
            <ul class="m-0 flex max-h-[45vh] list-none flex-col overflow-auto rounded-lg border border-(--line) p-0">
                <li v-for="r in shown" :key="r.orderId" class="flex flex-wrap items-baseline gap-x-2 border-b border-(--line) px-3 py-1.5 text-[13px] last:border-b-0">
                    <i :class="r.ok ? 'pi pi-check text-green-600' : 'pi pi-times text-red-600'" class="text-xs" aria-hidden="true" />
                    <code class="text-[12px]">{{ r.orderNo }}</code>
                    <span v-if="r.ok" class="text-xs text-muted-color">{{ t('groupBuy.bulkRowOk') }}</span>
                    <template v-else>
                        <span class="text-xs [overflow-wrap:anywhere]">{{ r.message }}</span>
                        <code v-if="r.code" class="text-[11px] text-muted-color">{{ r.code }}</code>
                    </template>
                </li>
                <li v-if="!shown.length" class="px-3 py-2 text-[13px] text-muted-color">{{ t('groupBuy.bulkNoFailures') }}</li>
            </ul>
        </div>
        <template #footer>
            <Button type="button" :label="t('common.close')" @click="emit('update:visible', false)" />
        </template>
    </Dialog>
</template>

<script setup lang="ts">
import type { GroupBuyBulkResult } from '~/composables/groupbuy/api/useGroupBuyOrderApi.types';

interface Props {
    visible: boolean;
    title: string;
    result: GroupBuyBulkResult | null;
}

const props = withDefaults(defineProps<Props>(), {});
const emit = defineEmits<{ 'update:visible': [visible: boolean] }>();

const { t } = useI18n();
const onlyId = useId();
const failedOnly = ref(true);
watch(
    () => props.result,
    (r) => (failedOnly.value = !!r && r.failed > 0),
);
const shown = computed(() => (props.result?.results ?? []).filter((r) => !failedOnly.value || !r.ok));
</script>
