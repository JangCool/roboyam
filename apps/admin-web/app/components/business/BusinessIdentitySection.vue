<template>
    <!--
        사업자 상세 ① 신원 — 기본은 가린 값. [평문 보기](`business.reveal`) → 부모가 사유 대화상자를 열고 서버가 열람을 기록한다(B3).
        평문은 이 블록 안에 10분 카운트다운과 함께 보이고, 끝나면 저절로 가린다([지금 가리기] 도 있다). 복사 단추는 두지 않는다.
        권한이 없으면 단추 대신 짧은 안내.
    -->
    <section class="flex flex-col gap-2" :aria-labelledby="headId">
        <div class="flex flex-wrap items-center justify-between gap-2">
            <h3 :id="headId" class="m-0 text-sm font-semibold">{{ t('business.secIdentity') }}</h3>
            <div v-if="shown" class="inline-flex items-center gap-2">
                <Tag :value="t('business.revealLeft', { time: UtilBusiness.clock(countdown.remaining.value) })" severity="warn" icon="pi pi-clock" />
                <Button type="button" :label="t('business.hideNow')" icon="pi pi-eye-slash" size="small" severity="secondary" variant="text" @click="emit('hide')" />
            </div>
            <Button v-else-if="canReveal" type="button" :label="t('business.reveal')" icon="pi pi-eye" size="small" severity="secondary" variant="outlined" :loading="busy" @click="emit('reveal')" />
            <span v-else class="text-xs text-muted-color">{{ t('business.revealNoPermission') }}</span>
        </div>
        <dl class="m-0 grid grid-cols-[7rem_1fr] gap-x-3 gap-y-1.5 rounded-lg border border-(--line) px-3 py-2.5 text-[13px] select-text" :class="shown ? 'bg-amber-50/60 dark:bg-amber-950/20' : ''">
            <template v-for="row in rows" :key="row.key">
                <dt class="text-muted-color">{{ row.label }}</dt>
                <dd class="m-0 min-w-0 [overflow-wrap:anywhere]" :class="shown ? 'font-medium' : 'tabular-nums'">{{ row.value }}</dd>
            </template>
        </dl>
        <p v-if="shown" class="m-0 text-xs text-muted-color">{{ t('business.revealLogged') }}</p>
    </section>
</template>

<script setup lang="ts">
import type { BusinessDetail, BusinessReveal } from '~/composables/business/api/useBusinessApi.types';
import UtilBusiness from '~/utils/util-business';

interface Props {
    detail: BusinessDetail;
    /** 지금 보이는 평문(부모가 받아 둔다) — null 이면 가린 값 */
    reveal?: BusinessReveal | null;
    canReveal?: boolean;
    busy?: boolean;
}

const props = withDefaults(defineProps<Props>(), { reveal: null, canReveal: false, busy: false });
const emit = defineEmits<{ reveal: []; hide: [] }>();

const { t } = useI18n();
const headId = useId();

const endsAt = ref(0);
const countdown = useCountdown(endsAt, () => emit('hide'));
watch(
    () => props.reveal?.hideAt,
    (hideAt) => {
        if (hideAt) endsAt.value = Date.parse(hideAt);
        else countdown.stop();
    },
    { immediate: true },
);

const shown = computed(() => Boolean(props.reveal) && countdown.active.value);

const rows = computed(() => {
    const r = shown.value ? props.reveal : null;
    const m = props.detail.masked;
    return [
        { key: 'name', label: t('business.col.name'), value: props.detail.name },
        { key: 'regNo', label: t('business.regNo'), value: r?.regNo ?? props.detail.maskedRegNo },
        { key: 'rep', label: t('business.representative'), value: r?.representative ?? m.representative },
        { key: 'addr', label: t('business.address'), value: r?.address ?? m.address },
        { key: 'phone', label: t('business.phone'), value: r?.phone ?? m.phone },
        { key: 'email', label: t('business.email'), value: r?.email ?? m.email },
    ];
});
</script>
