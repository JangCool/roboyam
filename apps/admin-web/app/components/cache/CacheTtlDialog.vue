<template>
    <!--
        캐시 TTL 변경 — 지금 TTL·기본값·허용 범위를 보이고 숫자 + 단위(초/분)로 새 값을 받는다. 서버 호출은 부르는 쪽(페이지)이 하고 여기는 `submit` 으로 초를 내보낸다.
        범위 안인지는 미리 알리기만 한다(저장 단추를 잠근다) — 판정은 서버다(400 `fields.ttlSeconds` 는 입력칸 아래에 서버 문구 그대로). 열 때마다 지금 TTL 로 채운다.
        바꾼 값은 각 서버가 세대 번호를 확인할 때(몇 초 안에) 모든 서버에 적용된다 — 그 안내를 대화상자 아래에 둔다.
    -->
    <Dialog :visible="visible" modal :draggable="false" :header="t('cache.ttlDialogTitle')" :style="{ width: 'min(32rem, calc(100vw - 2rem))' }" @update:visible="(v: boolean) => emit('update:visible', v)">
        <form v-if="cache" :id="formId" class="flex flex-col gap-4" novalidate @submit.prevent="submit">
            <div class="flex flex-col gap-1.5">
                <div class="flex flex-wrap items-center gap-2">
                    <span class="text-sm font-medium">{{ t('cache.ttlDialogName', { name: cache.displayName }) }}</span>
                    <code class="text-[12px] text-muted-color">{{ cache.cacheKey }}</code>
                </div>
                <dl class="flex flex-wrap gap-x-4 gap-y-0.5 text-[12px] text-muted-color">
                    <div class="flex gap-1.5">
                        <dt>{{ t('cache.ttlCurrent') }}</dt>
                        <dd class="text-color tabular-nums">{{ UtilCacheMessages.ttlText(t, cache.ttlSeconds) }}</dd>
                    </div>
                    <div class="flex gap-1.5">
                        <dt>{{ t('cache.ttlDefault') }}</dt>
                        <dd class="tabular-nums">{{ UtilCacheMessages.ttlText(t, cache.defaultTtlSeconds) }}</dd>
                    </div>
                    <div class="flex gap-1.5">
                        <dt>{{ t('cache.ttlAllowed') }}</dt>
                        <dd class="tabular-nums">{{ UtilCacheMessages.rangeText(t, cache.minTtlSeconds, cache.maxTtlSeconds) }}</dd>
                    </div>
                </dl>
            </div>

            <LabeledField :label="t('cache.ttlNew')" :help="help" :error="fieldErrors.ttlSeconds || rangeError">
                <template #default="{ id, describedBy, invalid }">
                    <div class="flex items-center gap-2">
                        <InputNumber
                            v-model="amount"
                            :input-id="id"
                            :min="1"
                            :max="99999"
                            :use-grouping="false"
                            :disabled="busy"
                            :invalid="invalid"
                            :aria-describedby="describedBy"
                            class="w-36"
                            fluid
                        />
                        <SelectButton v-model="unit" :options="unitOptions" option-label="label" option-value="value" :allow-empty="false" size="small" :disabled="busy" :aria-label="t('cache.ttlUnit')" />
                    </div>
                </template>
            </LabeledField>

            <div v-if="canResetToDefault" class="-mt-2">
                <Button type="button" :label="t('cache.ttlResetDefault')" icon="pi pi-replay" size="small" severity="secondary" variant="text" :disabled="busy" @click="fill(cache.defaultTtlSeconds)" />
            </div>

            <Message severity="info" size="small" variant="simple" :closable="false">{{ t('cache.ttlApplyNote', { s: pollSeconds }) }}</Message>
            <Message v-if="error" severity="error" size="small" variant="simple" :closable="false">{{ error }}</Message>
        </form>

        <template #footer>
            <Button type="button" :label="t('common.cancel')" severity="secondary" variant="text" :disabled="busy" @click="emit('update:visible', false)" />
            <Button type="submit" :form="formId" :label="t('common.save')" :loading="busy" :disabled="!canSubmit" />
        </template>
    </Dialog>
</template>

<script setup lang="ts">
import type { CacheView } from '~/composables/cache/api/useCacheAdminApi.types';
import UtilCache, { type CacheTtlUnit } from '~/utils/util-cache';
import UtilCacheMessages from '~/utils/util-cache-messages';

interface Props {
    visible: boolean;
    /** TTL 을 바꿀 캐시 — 열려 있는 동안 바뀌지 않는 값(열 때의 목록 한 줄) */
    cache?: CacheView | null;
    /** 서버가 세대 번호를 확인하는 주기(초) — 적용 안내에 쓴다 */
    pollSeconds?: number;
    busy?: boolean;
    /** 칸에 붙지 않는 오류 */
    error?: string;
    /** 서버가 준 칸별 오류 — `ttlSeconds` */
    fieldErrors?: Record<string, string>;
}

const props = withDefaults(defineProps<Props>(), { cache: null, pollSeconds: 3, busy: false, error: '', fieldErrors: () => ({}) });
const emit = defineEmits<{ 'update:visible': [visible: boolean]; submit: [ttlSeconds: number] }>();

const { t } = useI18n();
const formId = useId();

const amount = ref<number | null>(null);
const unit = ref<CacheTtlUnit>('SECONDS');

const unitOptions = computed(() => [
    { value: 'SECONDS' as const, label: t('cache.unitSeconds') },
    { value: 'MINUTES' as const, label: t('cache.unitMinutes') },
]);

function fill(seconds: number): void {
    const split = UtilCache.toUnit(seconds);
    amount.value = split.amount;
    unit.value = split.unit;
}

/** 열 때마다 지금 TTL 로 채운다 — 앞 캐시에 적던 값이 다음 캐시에 딸려 가면 안 된다 */
watch(
    () => props.visible,
    (open) => {
        if (open && props.cache) fill(props.cache.ttlSeconds);
    },
    { immediate: true },
);

/** 입력한 값(초) — 비었으면 null */
const seconds = computed(() => (amount.value == null ? null : UtilCache.toSeconds(amount.value, unit.value)));
const inRange = computed(() => !!props.cache && seconds.value != null && UtilCache.inRange(seconds.value, props.cache));
/** 범위 밖이면 미리 알린다(서버 문구가 있으면 그것이 먼저) */
const rangeError = computed(() => (props.cache && seconds.value != null && !inRange.value ? t('cache.ttlOutOfRange', { range: UtilCacheMessages.rangeText(t, props.cache.minTtlSeconds, props.cache.maxTtlSeconds) }) : ''));
/** 입력한 값을 사람 글로 — 「= 1분 30초」 */
const help = computed(() => (seconds.value != null && unit.value === 'SECONDS' && seconds.value >= 60 ? t('cache.ttlEquals', { text: UtilCacheMessages.ttlText(t, seconds.value) }) : ''));
const canSubmit = computed(() => !props.busy && inRange.value && seconds.value !== props.cache?.ttlSeconds);
const canResetToDefault = computed(() => !!props.cache && seconds.value !== props.cache.defaultTtlSeconds);

function submit(): void {
    if (!canSubmit.value || seconds.value == null) return;
    emit('submit', seconds.value);
}
</script>
