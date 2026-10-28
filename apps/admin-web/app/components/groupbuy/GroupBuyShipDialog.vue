<template>
    <!--
        발송 처리 — 고른 주문(최대 200건)을 화면 안 표에서 택배사·송장번호를 넣고 한 번에 보낸다(계약 「화면 목록」, community A 120). 결과는 줄 단위(부분 성공)로 부르는 쪽이 보인다.
        「모두 같은 택배사」로 한 번에 채울 수 있다. 송장번호는 공백·하이픈을 떼고 숫자·영문 6~30자인지 미리 본다(판정은 서버 — 결정 11).
        서버는 부르지 않는다 — 채운 줄을 `submit` 으로 올린다.
    -->
    <Dialog :visible="visible" modal :draggable="false" :closable="!busy" :close-on-escape="!busy" :header="t('groupBuy.shipTitle', { n: rows.length })" :style="{ width: 'min(52rem, calc(100vw - 2rem))' }" @update:visible="(v: boolean) => emit('update:visible', v)">
        <form :id="formId" class="flex flex-col gap-3" novalidate @submit.prevent="submit">
            <p class="m-0 text-sm text-muted-color">{{ t('groupBuy.shipBody', { max: UtilGroupBuy.BULK_MAX }) }}</p>
            <div class="flex flex-wrap items-center gap-2">
                <label :for="allId" class="text-sm">{{ t('groupBuy.shipAllCarrier') }}</label>
                <Select :input-id="allId" v-model="allCarrier" :options="carriers" option-label="name" option-value="code" :placeholder="t('groupBuy.carrierPlaceholder')" class="w-48" :disabled="busy" />
                <Button type="button" :label="t('common.apply')" size="small" severity="secondary" variant="outlined" :disabled="!allCarrier || busy" @click="applyAll" />
            </div>
            <div class="max-h-[50vh] overflow-auto rounded-lg border border-(--line)">
                <table class="w-full min-w-[40rem] border-collapse text-[13px]">
                    <thead class="sticky top-0 bg-(--p-content-background)">
                        <tr class="border-b border-(--line) text-left text-xs text-muted-color">
                            <th class="px-2 py-1.5 font-normal">{{ t('groupBuy.col.orderNo') }}</th>
                            <th class="px-2 py-1.5 font-normal">{{ t('groupBuy.col.items') }}</th>
                            <th class="w-44 px-2 py-1.5 font-normal">{{ t('groupBuy.carrier') }}</th>
                            <th class="w-52 px-2 py-1.5 font-normal">{{ t('groupBuy.trackingNo') }}</th>
                        </tr>
                    </thead>
                    <tbody>
                        <tr v-for="(row, index) in rows" :key="row.orderId" class="border-b border-(--line) align-top last:border-b-0">
                            <td class="px-2 py-1.5"><code class="text-[12px]">{{ row.orderNo }}</code><div class="text-[11px] text-muted-color">{{ row.member }}</div></td>
                            <td class="px-2 py-1.5 text-xs">{{ row.items }}</td>
                            <td class="px-2 py-1.5">
                                <Select v-model="row.carrierCode" :options="carriers" option-label="name" option-value="code" :placeholder="t('groupBuy.carrierPlaceholder')" :aria-label="t('groupBuy.carrierN', { n: index + 1 })" :invalid="submitted && !row.carrierCode" :disabled="busy" fluid />
                            </td>
                            <td class="px-2 py-1.5">
                                <InputText v-model="row.trackingNo" :aria-label="t('groupBuy.trackingN', { n: index + 1 })" maxlength="40" autocomplete="off" :invalid="submitted && !UtilGroupBuy.trackingOk(row.trackingNo)" :disabled="busy" fluid />
                                <p v-if="submitted && row.trackingNo && !UtilGroupBuy.trackingOk(row.trackingNo)" class="m-0 mt-0.5 text-[11px] text-red-600 dark:text-red-400">{{ t('groupBuy.trackingInvalid') }}</p>
                            </td>
                        </tr>
                    </tbody>
                </table>
            </div>
            <Message v-if="rows.length > UtilGroupBuy.BULK_MAX" severity="error" variant="simple" size="small" :closable="false">{{ t('groupBuy.bulkTooMany', { max: UtilGroupBuy.BULK_MAX }) }}</Message>
            <Message v-if="submitted && invalidCount > 0" severity="error" variant="simple" size="small" :closable="false">{{ t('groupBuy.shipInvalidRows', { n: invalidCount }) }}</Message>
            <Message v-if="error" severity="error" variant="simple" size="small" :closable="false" role="alert">{{ error }}</Message>
        </form>
        <template #footer>
            <Button type="button" :label="t('common.cancel')" severity="secondary" variant="text" :disabled="busy" @click="emit('update:visible', false)" />
            <Button type="submit" :form="formId" :label="t('groupBuy.shipSubmit', { n: rows.length })" icon="pi pi-send" :loading="busy" :disabled="rows.length === 0 || rows.length > UtilGroupBuy.BULK_MAX" />
        </template>
    </Dialog>
</template>

<script setup lang="ts">
import type { GroupBuyCarrierChoice, GroupBuyShipRow } from '~/composables/groupbuy/api/useGroupBuyOrderApi.types';
import type { GroupBuyShipTarget } from '~/components/groupbuy/GroupBuyShipDialog.types';
import UtilGroupBuy from '~/utils/util-groupbuy';

interface Props {
    visible: boolean;
    orders: GroupBuyShipTarget[];
    carriers: GroupBuyCarrierChoice[];
    busy?: boolean;
    error?: string;
}

const props = withDefaults(defineProps<Props>(), { busy: false, error: '' });
const emit = defineEmits<{ 'update:visible': [visible: boolean]; submit: [rows: GroupBuyShipRow[]] }>();

const { t } = useI18n();
const formId = useId();
const allId = useId();

interface EditRow {
    orderId: string;
    orderNo: string;
    member: string;
    items: string;
    carrierCode: string;
    trackingNo: string;
}

const rows = ref<EditRow[]>([]);
const allCarrier = ref('');
const submitted = ref(false);

watch(
    () => props.visible,
    (open) => {
        if (!open) return;
        rows.value = props.orders.map((o) => ({ orderId: o.orderId, orderNo: o.orderNo, member: o.member, items: o.items, carrierCode: o.carrierCode ?? '', trackingNo: o.trackingNo ?? '' }));
        allCarrier.value = '';
        submitted.value = false;
    },
    { immediate: true },
);

const invalidCount = computed(() => rows.value.filter((r) => !r.carrierCode || !UtilGroupBuy.trackingOk(r.trackingNo)).length);

function applyAll(): void {
    rows.value = rows.value.map((r) => ({ ...r, carrierCode: allCarrier.value }));
}

function submit(): void {
    submitted.value = true;
    if (props.busy || invalidCount.value > 0 || rows.value.length > UtilGroupBuy.BULK_MAX) return;
    emit(
        'submit',
        rows.value.map((r) => ({ orderId: r.orderId, carrierCode: r.carrierCode, trackingNo: UtilGroupBuy.trackingText(r.trackingNo) })),
    );
}
</script>
