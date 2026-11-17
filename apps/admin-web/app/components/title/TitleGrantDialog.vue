<template>
    <!--
        칭호 주기 대화상자(회원 상세 「칭호」 구획의 [칭호 주기], member-title-contract 「구현 결정」 7 — `POST /api/admin/titles/{key}/grants { memberId, period?, tier?, reason }`).
        칸: 칭호(켜진 칭호 — 그림 칩으로) · 기간(이달의 작가 = 달, 올해의 작가 = 해 — 그 밖은 칸 없음) · 단계(단계 칭호만) · 사유(필수). AI 회원·운영팀 계정은 서버가 거절한다.
        서버를 부르지 않는다 — `submit` 으로 값을 올리고 부모가 부른다. 서버가 거절한 칸(`fields`)은 칸 아래, 그 밖은 아래 한 줄.
    -->
    <Dialog :visible="visible" modal :draggable="false" :closable="!busy" :header="t('memberTitle.grantDialog.title')" :style="{ width: 'min(30rem, calc(100vw - 2rem))' }" @update:visible="(v: boolean) => emit('update:visible', v)">
        <form :id="formId" class="flex flex-col gap-4" novalidate @submit.prevent="submit">
            <p v-if="memberName" class="m-0 text-sm">{{ t('memberTitle.grantDialog.lead', { name: memberName }) }}</p>
            <LabeledField :label="t('memberTitle.grantDialog.titleLabel')" :error="fieldOf('key')">
                <template #default="{ id, describedBy, invalid }">
                    <Select :input-id="id" v-model="key" :options="options" option-label="name" option-value="key" :placeholder="t('memberTitle.grantDialog.pick')" :disabled="busy" class="w-full" :invalid="invalid" :aria-describedby="describedBy">
                        <template #option="{ option }"><TitleChip :title="lookOf(option)" /></template>
                        <template #value="{ value }">
                            <TitleChip v-if="selected && value" :title="lookOf(selected)" />
                            <span v-else class="text-muted-color">{{ t('memberTitle.grantDialog.pick') }}</span>
                        </template>
                    </Select>
                </template>
            </LabeledField>
            <LabeledField v-if="periodKind" :label="periodKind === 'MONTH' ? t('memberTitle.grantDialog.month') : t('memberTitle.grantDialog.year')" :help="periodKind === 'MONTH' ? t('memberTitle.grantDialog.monthHelp') : t('memberTitle.grantDialog.yearHelp')" :error="fieldOf('period')">
                <template #default="{ id, describedBy, invalid }">
                    <InputText :id="id" v-model="period" :placeholder="periodKind === 'MONTH' ? 'YYYY-MM' : 'YYYY'" maxlength="7" class="w-36 font-mono" :disabled="busy" :invalid="invalid" :aria-describedby="describedBy" />
                </template>
            </LabeledField>
            <div v-if="selected?.tierMetric" class="flex flex-col gap-2">
                <span :id="`${formId}-tier`" class="text-sm font-medium">{{ t('memberTitle.grantDialog.tier') }}</span>
                <SelectButton v-model="tier" :options="tierOptions" option-label="label" option-value="value" :allow-empty="false" size="small" :disabled="busy" :aria-labelledby="`${formId}-tier`" />
            </div>
            <LabeledField :label="t('memberTitle.grantDialog.reason')" :hint="t('reason.count', { count: reason.trim().length, max: UtilTitle.REASON_MAX })" :error="reasonError">
                <template #default="{ id, describedBy, invalid }">
                    <Textarea :id="id" v-model="reason" rows="3" fluid :maxlength="UtilTitle.REASON_MAX + 50" :disabled="busy" :invalid="invalid" :aria-describedby="describedBy" />
                </template>
            </LabeledField>
            <p class="m-0 flex items-start gap-2 text-xs leading-relaxed text-muted-color">
                <i class="pi pi-info-circle mt-0.5" aria-hidden="true" />
                <span>{{ t('memberTitle.grantDialog.notice') }}</span>
            </p>
            <Message v-if="error" severity="error" size="small" variant="simple" :closable="false">{{ error }}</Message>
        </form>
        <template #footer>
            <Button type="button" :label="t('common.cancel')" severity="secondary" variant="text" :disabled="busy" @click="emit('update:visible', false)" />
            <Button type="submit" :form="formId" :label="t('memberTitle.grantDialog.submit')" icon="pi pi-star" :loading="busy" />
        </template>
    </Dialog>
</template>

<script setup lang="ts">
import type { TitleTier } from '#layers/member-title/utils/util-title-icon';
import type { AdminTitle, TitleGrantRequest, TitleLook } from '~/composables/title/api/useTitleApi.types';
import UtilTitle from '~/utils/util-title';

interface Props {
    visible: boolean;
    /** 고를 칭호(부모가 받은 목록 — 켜진 것만 보인다) */
    titles?: AdminTitle[];
    /** 받을 회원 이름(안내 한 줄) */
    memberName?: string;
    busy?: boolean;
    error?: string;
    fieldErrors?: Record<string, string>;
}

const props = withDefaults(defineProps<Props>(), { titles: () => [], memberName: '', busy: false, error: '', fieldErrors: () => ({}) });
const emit = defineEmits<{ 'update:visible': [visible: boolean]; submit: [key: string, body: Omit<TitleGrantRequest, 'memberId'>] }>();

const { t } = useI18n();
const formId = useId();

const key = ref<string | null>(null);
const period = ref('');
const tier = ref<TitleTier | null>(null);
const reason = ref('');
const tried = ref(false);

// 열 때마다 비운다
watch(
    () => props.visible,
    (open) => {
        if (!open) return;
        key.value = null;
        period.value = '';
        tier.value = null;
        reason.value = '';
        tried.value = false;
    },
);

const options = computed(() => props.titles.filter((x) => x.enabled));
const selected = computed(() => options.value.find((x) => x.key === key.value) ?? null);
const periodKind = computed(() => (key.value ? UtilTitle.periodKind(key.value) : null));
const TIERS: readonly TitleTier[] = ['BRONZE', 'SILVER', 'GOLD'];
const tierOptions = computed(() => TIERS.map((value) => ({ value, label: t(`memberTitle.tier.${value}`) })));

watch(selected, (title) => {
    tier.value = title ? UtilTitle.defaultTier(title) : null;
    period.value = '';
});

function lookOf(title: AdminTitle): TitleLook {
    return UtilTitle.lookOf(title, tier.value ?? 'BRONZE');
}

function fieldOf(field: string): string {
    if (props.fieldErrors[field]) return props.fieldErrors[field]!;
    if (!tried.value) return '';
    if (field === 'key' && !key.value) return t('memberTitle.grantDialog.pickRequired');
    if (field === 'period' && periodKind.value) {
        const ok = periodKind.value === 'MONTH' ? /^\d{4}-(0[1-9]|1[0-2])$/.test(period.value.trim()) : /^\d{4}$/.test(period.value.trim());
        if (!ok) return periodKind.value === 'MONTH' ? t('memberTitle.grantDialog.monthInvalid') : t('memberTitle.grantDialog.yearInvalid');
    }
    return '';
}

const reasonError = computed(() => {
    if (props.fieldErrors.reason) return props.fieldErrors.reason;
    const n = reason.value.trim().length;
    if (n > UtilTitle.REASON_MAX) return t('memberTitle.reasonTooLong', { max: UtilTitle.REASON_MAX });
    return tried.value && n === 0 ? t('memberTitle.reasonRequired') : '';
});

function submit(): void {
    tried.value = true;
    if (!key.value || fieldOf('period') || reasonError.value) return;
    emit('submit', key.value, { period: periodKind.value ? period.value.trim() : null, tier: selected.value?.tierMetric ? tier.value : null, reason: reason.value.trim() });
}
</script>
