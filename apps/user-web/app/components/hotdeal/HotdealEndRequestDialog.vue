<template>
    <!-- 품절·가격 변경 알리기 — 사유 3개 중 하나(자유 글 없음 — 악용 방지). 보내기는 부르는 쪽(딜 카드)이 한다 -->
    <Dialog :visible="visible" modal :header="t('hotdeal.endDialogTitle')" :style="{ width: 'min(24rem, calc(100vw - 2rem))' }" :draggable="false" @update:visible="close">
        <form class="flex flex-col gap-3" data-testid="hotdeal-end-dialog" @submit.prevent="submit">
            <p class="m-0 text-sm text-muted-color">{{ t('hotdeal.endDialogText') }}</p>
            <fieldset class="m-0 flex flex-col gap-2 border-0 p-0">
                <legend class="sr-only">{{ t('hotdeal.endDialogTitle') }}</legend>
                <label v-for="value in UtilHotdeal.END_REASONS" :key="value" class="flex cursor-pointer items-center gap-2 text-sm">
                    <RadioButton v-model="reason" :value="value" :input-id="`${baseId}-${value}`" :name="`${baseId}-reason`" />
                    <span>{{ UtilHotdealMessages.endReason(t, value) }}</span>
                </label>
            </fieldset>
            <Message v-if="error" severity="error" variant="simple" size="small">{{ error }}</Message>
            <div class="flex justify-end gap-2">
                <Button type="button" :label="t('common.cancel')" severity="secondary" variant="text" @click="close(false)" />
                <Button type="submit" :label="t('hotdeal.endDialogSubmit')" :loading="loading" :disabled="!reason" data-testid="hotdeal-end-submit" />
            </div>
        </form>
    </Dialog>
</template>

<script setup lang="ts">
import type { DealEndReason } from '~/composables/hotdeal/api/useHotdealApi.types';
import UtilHotdeal from '~/utils/util-hotdeal';
import UtilHotdealMessages from '~/utils/util-hotdeal-messages';

interface Props {
    visible: boolean;
    loading?: boolean;
    /** 칸에 붙지 않는 서버 문구 */
    error?: string;
}

const props = withDefaults(defineProps<Props>(), { loading: false, error: '' });
const emit = defineEmits<{
    'update:visible': [value: boolean];
    submit: [reason: DealEndReason];
}>();

const { t } = useI18n();
const baseId = useId();
const reason = ref<DealEndReason | null>(null);

function close(value: boolean): void {
    if (!value) reason.value = null;
    emit('update:visible', value);
}

function submit(): void {
    if (!reason.value || props.loading) return;
    emit('submit', reason.value);
}

watch(
    () => props.visible,
    (open) => {
        if (!open) reason.value = null;
    },
);
</script>
