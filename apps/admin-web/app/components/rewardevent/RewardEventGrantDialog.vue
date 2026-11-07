<template>
    <!--
        지급 실행 확인 — 대상 N명 · 등수별 1인당 보상 · 총 포인트 · 아이템 수 · 직접 전달 수 · 「되돌릴 수 없고 회수만 가능」.
        고액 여부는 서버 판정(`preview.largeRequired` — 1인 포인트 합이 문턱 초과, 또는 일괄 방식에 포인트 보상이 있으면 늘)이고, 그때 「고액 권한(point.adjust.large) 필요」를 보이며
        권한이 없으면 실행 단추를 끈다(막는 것은 서버 — `403 POINT_ADJUST_LARGE_REQUIRED`).
        확인 체크를 해야 실행한다. 미리 보기는 부모가 받아 준다(`preview` — 서버 `GET …/grants/preview`), 실행도 부모 몫이다(`submit`).
    -->
    <Dialog :visible="visible" modal :draggable="false" :closable="!busy" :close-on-escape="!busy" :header="t('rewardEvent.grant.title')" :style="{ width: 'min(38rem, calc(100vw - 2rem))' }" @update:visible="(v: boolean) => emit('update:visible', v)">
        <div v-if="!preview" class="flex flex-col gap-2" role="status" :aria-label="t('common.loading')">
            <Skeleton height="3rem" />
            <Skeleton height="6rem" />
        </div>
        <div v-else class="flex flex-col gap-4">
            <p class="m-0 text-sm">{{ t('rewardEvent.grant.lead', { name: eventName }) }}</p>
            <dl class="m-0 grid grid-cols-2 gap-3 rounded-lg border border-(--line) p-3 sm:grid-cols-4">
                <div>
                    <dt class="text-xs text-muted-color">{{ t('rewardEvent.grant.targets') }}</dt>
                    <dd class="m-0 text-base font-semibold tabular-nums">{{ t('rewardEvent.peopleValue', { n: preview.targetCount.toLocaleString(locale) }) }}</dd>
                </div>
                <div>
                    <dt class="text-xs text-muted-color">{{ t('rewardEvent.grant.totalPoints') }}</dt>
                    <dd class="m-0 text-base font-semibold tabular-nums">{{ UtilRewardEventMessages.points(t, preview.totalPoints, locale) }}</dd>
                </div>
                <div>
                    <dt class="text-xs text-muted-color">{{ t('rewardEvent.grant.items') }}</dt>
                    <dd class="m-0 text-base font-semibold tabular-nums">{{ preview.itemCount.toLocaleString(locale) }}</dd>
                </div>
                <div>
                    <dt class="text-xs text-muted-color">{{ t('rewardEvent.grant.manual') }}</dt>
                    <dd class="m-0 text-base font-semibold tabular-nums">{{ preview.manualCount.toLocaleString(locale) }}</dd>
                </div>
            </dl>

            <div class="flex flex-col gap-1">
                <span class="text-xs font-medium">{{ t('rewardEvent.grant.perPerson') }}</span>
                <ul class="m-0 flex list-none flex-col gap-1 p-0 text-xs">
                    <li v-for="row in tierRows" :key="row.tierId" class="flex flex-wrap items-baseline gap-x-2 rounded border border-(--line) px-2 py-1.5" :class="row.count ? '' : 'opacity-60'">
                        <span class="font-medium">{{ row.tierName }}</span>
                        <span class="tabular-nums text-muted-color">{{ t('rewardEvent.peopleValue', { n: row.count.toLocaleString(locale) }) }}</span>
                        <span>{{ row.rewards }}</span>
                    </li>
                </ul>
            </div>

            <Message v-if="preview.manualCount" severity="info" :closable="false" size="small">{{ t('rewardEvent.grant.manualNote') }}</Message>
            <Message v-if="isBulk" severity="secondary" :closable="false" size="small">{{ t('rewardEvent.grant.bulkNote') }}</Message>
            <Message v-if="preview.largeRequired" :severity="canLarge ? 'warn' : 'error'" :closable="false" size="small">
                {{ largeText }}
            </Message>
            <Message severity="warn" :closable="false" size="small">{{ t('rewardEvent.grant.irreversible') }}</Message>

            <label class="flex cursor-pointer items-start gap-2 text-sm">
                <Checkbox v-model="confirmed" binary :input-id="checkId" :disabled="busy || blocked" class="mt-0.5" />
                {{ t('rewardEvent.grant.confirmCheck', { n: preview.targetCount.toLocaleString(locale) }) }}
            </label>

            <Message v-if="error" severity="error" size="small" variant="simple" :closable="false" role="alert">{{ error }}</Message>
        </div>

        <template #footer>
            <Button type="button" :label="t('common.cancel')" severity="secondary" variant="text" :disabled="busy" @click="emit('update:visible', false)" />
            <Button type="button" :label="t('rewardEvent.grant.submit')" icon="pi pi-send" :loading="busy" :disabled="!preview || !confirmed || blocked" @click="emit('submit')" />
        </template>
    </Dialog>
</template>

<script setup lang="ts">
import type { RewardGrantPreview, RewardTier } from '~/composables/rewardevent/api/useRewardEventAdminApi.types';
import UtilRewardEventMessages from '~/utils/util-reward-event-messages';

interface Props {
    visible: boolean;
    eventName: string;
    tiers: RewardTier[];
    preview: RewardGrantPreview | null;
    /** `point.adjust.large` 를 가졌다 */
    canLarge?: boolean;
    isBulk?: boolean;
    busy?: boolean;
    error?: string;
}

const props = withDefaults(defineProps<Props>(), { canLarge: false, isBulk: false, busy: false, error: '' });
const emit = defineEmits<{ 'update:visible': [visible: boolean]; submit: [] }>();

const { t, locale } = useI18n();
const checkId = useId();
const confirmed = ref(false);

// 열 때마다 확인 체크를 푼다
watch(
    () => props.visible,
    (open) => {
        if (open) confirmed.value = false;
    },
);

/** 대상이 없거나 고액 권한이 없으면 실행할 수 없다 */
const blocked = computed(() => !props.preview || props.preview.targetCount === 0 || (props.preview.largeRequired && !props.canLarge));

/** 고액 안내 — 일괄은 금액과 상관없이(포인트 보상이 있어서), 그 밖은 1인 최대 포인트가 문턱을 넘어서 */
const largeText = computed(() => {
    const p = props.preview;
    if (!p) return '';
    if (props.isBulk) return props.canLarge ? t('rewardEvent.grant.largeBulkOk') : t('rewardEvent.grant.largeBulkMissing');
    const params = { n: p.largeThreshold.toLocaleString(locale.value), max: p.maxPointsPerPerson.toLocaleString(locale.value) };
    return props.canLarge ? t('rewardEvent.grant.largeOk', params) : t('rewardEvent.grant.largeMissing', params);
});

const tierRows = computed(() =>
    (props.preview?.tiers ?? []).map((row) => ({
        ...row,
        rewards: UtilRewardEventMessages.summary(t, props.tiers.find((tier) => tier.id === row.tierId)?.rewards ?? [], locale.value),
    })),
);
</script>
