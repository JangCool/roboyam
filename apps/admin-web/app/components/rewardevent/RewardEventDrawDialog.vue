<template>
    <!--
        댓글 추첨 — 등수 고르기 · 뽑을 수(기본 = 남은 자리) · 후보 요약(부모가 미리 보기로 받아 준다). 추첨은 서버가 하고(추첨 번호 seed 로 다시 낼 수 있다) 기록을 남긴다.
        이미 이 이벤트에 있는 회원은 후보에서 빠진다(같은 회원은 이벤트당 한 번 — 등수끼리도). 서버는 부르지 않는다 — `submit` 으로 올린다.
    -->
    <Dialog :visible="visible" modal :draggable="false" :closable="!busy" :close-on-escape="!busy" :header="t('rewardEvent.draw.title')" :style="{ width: 'min(34rem, calc(100vw - 2rem))' }" @update:visible="(v: boolean) => emit('update:visible', v)">
        <form :id="formId" class="flex flex-col gap-4" novalidate @submit.prevent="submit">
            <p class="m-0 text-sm">
                {{ t('rewardEvent.draw.source', { title: postTitle || '—' }) }}
            </p>
            <p class="m-0 text-xs text-muted-color">
                <template v-if="candidateCount === null">{{ t('rewardEvent.draw.candidatesLoading') }}</template>
                <template v-else>{{ t('rewardEvent.draw.candidates', { n: candidateCount.toLocaleString(locale), already: alreadyIn.toLocaleString(locale) }) }}</template>
            </p>

            <LabeledField :label="t('rewardEvent.draw.tier')" :error="fieldErrors.tierId">
                <template #default="{ id, invalid }">
                    <Select v-model="tierId" :input-id="id" :options="tierOptions" option-label="label" option-value="value" option-disabled="disabled" :disabled="busy" :invalid="invalid" fluid @update:model-value="onTier" />
                </template>
            </LabeledField>
            <LabeledField :label="t('rewardEvent.draw.count')" :hint="room ? t('rewardEvent.draw.roomHint', { n: room }) : ''" :error="fieldErrors.count">
                <template #default="{ id, invalid }">
                    <InputNumber v-model="count" :input-id="id" :min="1" :max="Math.max(room, 1)" show-buttons :suffix="t('rewardEvent.peopleSuffix')" :disabled="busy || !room" :invalid="invalid" class="max-w-48" fluid />
                </template>
            </LabeledField>
            <Message severity="secondary" :closable="false" size="small">{{ t('rewardEvent.draw.serverNote') }}</Message>
            <Message v-if="error" severity="error" size="small" variant="simple" :closable="false" role="alert">{{ error }}</Message>
        </form>
        <template #footer>
            <Button type="button" :label="t('common.cancel')" severity="secondary" variant="text" :disabled="busy" @click="emit('update:visible', false)" />
            <Button type="submit" :form="formId" :label="t('rewardEvent.draw.submit')" icon="pi pi-sparkles" :loading="busy" :disabled="!tierId || !room || !count" />
        </template>
    </Dialog>
</template>

<script setup lang="ts">
import type { RewardDrawTierChoice } from '~/components/rewardevent/RewardEventDrawDialog.types';
import type { RewardDrawRequest } from '~/composables/rewardevent/api/useRewardEventAdminApi.types';

interface Props {
    visible: boolean;
    postTitle: string;
    tiers: RewardDrawTierChoice[];
    /** 후보 수(미리 보기) — 모르면 null */
    candidateCount?: number | null;
    /** 이미 이 이벤트에 있는 회원 수(후보에서 빠진다) */
    alreadyIn?: number;
    busy?: boolean;
    error?: string;
    fieldErrors?: Record<string, string>;
}

const props = withDefaults(defineProps<Props>(), { candidateCount: null, alreadyIn: 0, busy: false, error: '', fieldErrors: () => ({}) });
const emit = defineEmits<{ 'update:visible': [visible: boolean]; submit: [body: RewardDrawRequest] }>();

const { t, locale } = useI18n();
const formId = useId();
const tierId = ref('');
const count = ref<number | null>(null);

const tierOptions = computed(() => props.tiers.map((tier) => ({ value: tier.id, disabled: tier.current >= tier.quota, label: t('rewardEvent.draw.tierOption', { name: tier.name, current: tier.current, quota: tier.quota }) })));
const room = computed(() => {
    const tier = props.tiers.find((x) => x.id === tierId.value);
    return tier ? Math.max(0, tier.quota - tier.current) : 0;
});

function onTier(): void {
    count.value = room.value || null;
}

// 열 때마다 자리가 남은 첫 등수로
watch(
    () => props.visible,
    (open) => {
        if (!open) return;
        tierId.value = props.tiers.find((x) => x.current < x.quota)?.id ?? '';
        count.value = room.value || null;
    },
);

function submit(): void {
    if (props.busy || !tierId.value || !count.value) return;
    emit('submit', { tierId: tierId.value, count: count.value });
}
</script>
