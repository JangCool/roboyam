<template>
    <!--
        가격 고르기 대화상자 — 심사 승인(`mode="approve"`)과 팩 관리의 [가격 바꾸기](`mode="change"`)가 같이 쓴다(카카오 방식 — 가격은 운영자가 설정의 가격 단계에서 고른다).
        승인: 작가가 유료 희망이면 단계 하나를 꼭 고른다(새 판이면 지금 가격이 처음 값). 「무료로 승인」도 고를 수 있고, 작가 희망과 다르면 경고 한 줄. 작가가 무료로 냈으면 무료 승인만.
          규격 어긋남이 있으면 그 수를 한 줄로 알린다(막지는 않는다). 확인 단추 위에 「2,500원으로 판매를 시작해요」/「무료로 공개해요」.
        가격 바꾸기(판매 중 작가 팩): 지금과 다른 값 하나 — 유료 → 다른 단계 · 유료 → 「무료로 바꾸기」 · 무료 → 단계(상품화 계약·정산 정보·유료 판매 켜짐일 때만, 아니면 단계 잠금 + 까닭) + 사유(필수 5~200자 — 작가에게 알림 후보).
          고른 방향에 따라 안내가 바뀐다(무료로: 산 사람·환불 규칙·지난 정산 그대로 · 이제부터 받기 보상 / 유료로: 이미 받은 사람 그대로 · 받기 보상 멈춤). 결과 줄 「2,500원 → 무료」.
        서버는 부르지 않는다 — `submit` 으로 `{ price, reason }`(승인은 reason '') 을 올린다. 열 때마다 처음 값으로 돌린다.
    -->
    <Dialog :visible="visible" modal :draggable="false" :closable="!busy" :close-on-escape="!busy" :header="title" :style="{ width: 'min(34rem, calc(100vw - 2rem))' }" @update:visible="(v: boolean) => emit('update:visible', v)">
        <form :id="formId" class="flex flex-col gap-4" novalidate @submit.prevent="submit">
            <p v-if="description" class="m-0 text-sm leading-relaxed text-muted-color">{{ description }}</p>

            <p v-if="mode === 'approve'" class="m-0 inline-flex flex-wrap items-center gap-2 text-[13px]">
                <span class="text-muted-color">{{ t('emoticon.price.wish') }}</span>
                <Tag :value="t(`emoticon.saleWish.${saleWish}`)" :severity="saleWish === 'PAID' ? 'info' : 'secondary'" />
                <span v-if="mode === 'approve' && currentPrice > 0" class="text-muted-color">· {{ t('emoticon.price.current', { price: won(currentPrice) }) }}</span>
            </p>
            <p v-else class="m-0 text-[13px]">{{ t('emoticon.price.current', { price: priceText(currentPrice) }) }}</p>

            <div class="flex flex-col gap-1.5">
                <span :id="groupLabelId" class="text-sm font-medium">{{ t('emoticon.price.pick') }}</span>
                <div class="flex flex-col gap-1.5" role="radiogroup" :aria-labelledby="groupLabelId">
                    <label v-for="opt in options" :key="opt.value" class="flex cursor-pointer items-center gap-2 rounded-lg border px-3 py-2 text-[13px]" :class="[picked === opt.value ? 'border-primary bg-(--p-content-hover-background)' : 'border-(--line)', opt.disabled ? 'cursor-not-allowed opacity-60' : '']">
                        <RadioButton v-model="picked" :value="opt.value" :name="formId" :disabled="busy || opt.disabled" />
                        <span class="flex-1">{{ opt.label }}</span>
                        <span v-if="opt.note" class="text-[11px] text-muted-color">{{ opt.note }}</span>
                    </label>
                </div>
                <p v-if="submitted && picked === null" role="alert" class="m-0 text-xs text-red-600 dark:text-red-400">{{ t('emoticon.price.required') }}</p>
                <p v-else-if="fieldErrors.price" role="alert" class="m-0 text-xs text-red-600 dark:text-red-400">{{ fieldErrors.price }}</p>
                <p v-if="!tiers.length" class="m-0 text-xs text-amber-700 dark:text-amber-400">{{ t('emoticon.price.noTiers') }}</p>
                <p v-else-if="mode === 'approve' && saleWish === 'PAID' && !paidSalesEnabled" class="m-0 text-xs text-amber-700 dark:text-amber-400">{{ t('emoticon.price.paidOff') }}</p>
            </div>

            <Message v-if="mismatch" severity="warn" :closable="false"><span class="text-[13px]">{{ t('emoticon.price.mismatch') }}</span></Message>
            <Message v-if="mode === 'approve' && specErrors" severity="warn" :closable="false"><span class="text-[13px]">{{ t('emoticon.approveSpecWarn', { count: specErrors }) }}</span></Message>
            <Message v-if="blockers.length" severity="error" :closable="false">
                <span class="text-[13px]">{{ blockers.join(' ') }}</span>
            </Message>

            <template v-if="mode === 'change'">
                <LabeledField :label="t('emoticon.price.reason')" :hint="t('emoticon.reason.hint', { min: REASON.min, max: REASON.max })" :help="t('emoticon.price.reasonHelp')" :error="reasonError">
                    <template #default="{ id, describedBy, invalid }">
                        <Textarea :id="id" v-model="reason" rows="3" auto-resize :disabled="busy" :invalid="invalid" :aria-describedby="describedBy" :aria-required="true" fluid />
                    </template>
                </LabeledField>
                <ul class="m-0 flex list-none flex-col gap-1 p-0 text-xs leading-relaxed text-muted-color">
                    <li v-for="note in changeNotes" :key="note.key" class="flex items-start gap-2"><i class="pi mt-0.5 text-xs" :class="note.icon" aria-hidden="true" /><span>{{ note.text }}</span></li>
                </ul>
            </template>

            <p v-if="resultLine" class="m-0 rounded-lg bg-(--p-content-hover-background) px-3 py-2 text-[13px] font-medium">{{ resultLine }}</p>

            <Message v-if="error" severity="error" size="small" variant="simple" :closable="false" role="alert">{{ error }}</Message>
        </form>

        <template #footer>
            <Button type="button" :label="t('common.cancel')" severity="secondary" variant="text" :disabled="busy" @click="emit('update:visible', false)" />
            <Button type="submit" :form="formId" :label="mode === 'approve' ? t('emoticon.actApprove') : t('emoticon.actChangePrice')" icon="pi pi-check" :loading="busy" :disabled="mode === 'approve' && blockers.length > 0 && picked !== 0" />
        </template>
    </Dialog>
</template>

<script setup lang="ts">
import type { EmoticonSaleWish } from '~/composables/emoticon/api/useEmoticonAdminApi.types';
import UtilEmoticon from '~/utils/util-emoticon';
import UtilEmoticonMessages from '~/utils/util-emoticon-messages';

interface Props {
    visible: boolean;
    mode: 'approve' | 'change';
    title: string;
    description?: string;
    /** 작가의 판매 희망(승인) — 가격 바꾸기는 늘 유료 */
    saleWish?: EmoticonSaleWish;
    /** 지금 가격(원, 0 = 무료 / 처음 신청) */
    currentPrice?: number;
    /** 설정의 가격 단계(원) */
    tiers?: number[];
    paidSalesEnabled?: boolean;
    /** 규격 어긋남 수(승인 안내) */
    specErrors?: number;
    /** 유료로 승인하면 안 되는 까닭(상품화 계약 미동의 · 정산 정보 없음 — 부모가 문구로 준다). 있으면 무료 승인만 된다 */
    paidBlockers?: string[];
    busy?: boolean;
    error?: string;
    fieldErrors?: Record<string, string>;
}

const props = withDefaults(defineProps<Props>(), {
    description: '',
    saleWish: 'PAID',
    currentPrice: 0,
    tiers: () => [],
    paidSalesEnabled: true,
    specErrors: 0,
    paidBlockers: () => [],
    busy: false,
    error: '',
    fieldErrors: () => ({}),
});
const emit = defineEmits<{ 'update:visible': [visible: boolean]; submit: [body: { price: number; reason: string }] }>();

const { t, locale } = useI18n();
const formId = useId();
const groupLabelId = useId();
const REASON = UtilEmoticon.REASON;

/** 고른 가격 — null = 아직 안 고름, 0 = 무료 */
const picked = ref<number | null>(null);
const reason = ref('');
const submitted = ref(false);

function won(value: number): string {
    return t('emoticon.wonValue', { amount: value.toLocaleString(locale.value) });
}

/** 가격 글자 — 0 은 「무료」 */
function priceText(value: number): string {
    return value > 0 ? won(value) : t('emoticon.free');
}

/**
 * 유료로 고를 수 있나 — 유료 판매 켜짐이 먼저.
 * 승인: 유료 희망 · 막는 까닭 없음. 가격 바꾸기: 이미 유료면 다른 단계로 언제나, 무료 → 유료면 막는 까닭(상품화 계약·정산 정보)이 없을 때만.
 */
const paidAllowed = computed(() => {
    if (!props.paidSalesEnabled) return false;
    if (props.mode === 'change') return props.currentPrice > 0 || !props.paidBlockers.length;
    return props.saleWish === 'PAID' && !props.paidBlockers.length;
});

const options = computed(() => {
    const out: { value: number; label: string; note: string; disabled: boolean }[] = [];
    if (props.mode === 'approve') out.push({ value: 0, label: t('emoticon.price.freeApprove'), note: props.saleWish === 'FREE' ? t('emoticon.price.asWished') : '', disabled: false });
    // 가격 바꾸기: 유료 팩이면 「무료로 바꾸기」, 무료 팩이면 지금 값(무료)을 잠근 한 줄
    if (props.mode === 'change') out.push({ value: 0, label: props.currentPrice > 0 ? t('emoticon.price.toFree') : t('emoticon.free'), note: props.currentPrice === 0 ? t('emoticon.price.currentTag') : '', disabled: props.currentPrice === 0 });
    if (props.mode === 'change' || props.saleWish === 'PAID') {
        for (const tier of props.tiers) {
            const isCurrent = tier === props.currentPrice;
            out.push({ value: tier, label: won(tier), note: isCurrent ? t('emoticon.price.currentTag') : '', disabled: !paidAllowed.value || (props.mode === 'change' && isCurrent) });
        }
    }
    return out;
});

/** 유료로 못 가는 까닭 — 승인(유료 희망) · 가격 바꾸기(무료 → 유료)일 때만 보인다 */
const blockers = computed(() => {
    if (props.mode === 'approve') return props.saleWish === 'PAID' ? props.paidBlockers : [];
    return props.currentPrice === 0 ? props.paidBlockers : [];
});
const mismatch = computed(() => props.mode === 'approve' && props.saleWish === 'PAID' && picked.value === 0);

/** 가격 바꾸기 안내 — 고른 방향(무료로 · 무료에서 유료로 · 유료 단계끼리)에 따라 */
const changeNotes = computed(() => {
    const p = picked.value;
    if (p === 0 && props.currentPrice > 0) {
        return [
            { key: 'keep', icon: 'pi-lock', text: t('emoticon.price.toFreeKeep') },
            { key: 'settled', icon: 'pi-wallet', text: t('emoticon.price.toFreeSettled') },
            { key: 'reward', icon: 'pi-gift', text: t('emoticon.price.toFreeReward') },
            { key: 'notify', icon: 'pi-bell', text: t('emoticon.price.notify') },
        ];
    }
    if (p !== null && p > 0 && props.currentPrice === 0) {
        return [
            { key: 'kept', icon: 'pi-lock', text: t('emoticon.price.toPaidKeep') },
            { key: 'reward', icon: 'pi-gift', text: t('emoticon.price.toPaidReward') },
            { key: 'notify', icon: 'pi-bell', text: t('emoticon.price.notify') },
        ];
    }
    return [
        { key: 'keep', icon: 'pi-lock', text: t('emoticon.price.keepPaid') },
        { key: 'notify', icon: 'pi-bell', text: t('emoticon.price.notify') },
    ];
});

const resultLine = computed(() => {
    if (picked.value === null) return '';
    if (props.mode === 'change') return t('emoticon.price.changeResult', { before: priceText(props.currentPrice), after: priceText(picked.value) });
    return picked.value > 0 ? t('emoticon.price.approvePaid', { price: won(picked.value) }) : t('emoticon.price.approveFree');
});

watch(
    () => props.visible,
    (open) => {
        if (!open) return;
        submitted.value = false;
        reason.value = '';
        // 처음 값 — 승인: 무료 희망이면 무료, 새 판이면 지금 가격(단계에 있으면), 그 밖은 안 고름 · 가격 바꾸기: 안 고름
        if (props.mode === 'approve') {
            if (props.saleWish === 'FREE' || !paidAllowed.value) picked.value = props.saleWish === 'FREE' ? 0 : null;
            else picked.value = props.currentPrice > 0 && props.tiers.includes(props.currentPrice) ? props.currentPrice : null;
        } else {
            picked.value = null;
        }
    },
    { immediate: true },
);

const reasonError = computed(() => {
    const problem = UtilEmoticon.reasonProblem(reason.value);
    if (problem && (submitted.value || problem === 'TOO_LONG')) return UtilEmoticonMessages.reasonProblem(t, problem);
    return props.fieldErrors.reason ?? '';
});

function submit(): void {
    submitted.value = true;
    if (props.busy || picked.value === null) return;
    if (picked.value > 0 && !paidAllowed.value) return;
    if (props.mode === 'change' && picked.value === props.currentPrice) return;
    if (props.mode === 'change' && UtilEmoticon.reasonProblem(reason.value)) return;
    emit('submit', { price: picked.value, reason: props.mode === 'change' ? reason.value.trim() : '' });
}
</script>
