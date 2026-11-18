<template>
    <!--
        기념일 규칙 한 줄을 더하거나 고치는 창(보이기 규칙) — 이름 · 날짜 정하는 법 · 보일 묶음.
          날짜 정하는 법 ① 날짜 직접: 시작 ~ 끝(끝 날 포함). 「매년 반복」을 켜면 연 없이 월·일만 고른다(끝이 시작보다 앞이면 해를 넘기는 기간).
                        ② 쉬는 날 표에서 고르기: 휴일 관리(`/settings/holidays`)의 표에 있는 이름(설날·추석 …)을 고른다 — 그 이름의 날마다 해마다 걸린다.
        여기서 고친 값은 보이기 규칙의 초안에만 들어간다(서버를 부르지 않는다) — 저장은 구획의 [저장]이 한다. 사용 켬·끔과 순서는 목록에서 바꾼다.
    -->
    <Dialog :visible="visible" modal :draggable="false" :header="rule ? t('siteLogo.ruleEditTitle') : t('siteLogo.ruleAddTitle')" :style="{ width: 'min(34rem, calc(100vw - 2rem))' }" @update:visible="(v: boolean) => emit('update:visible', v)">
        <form :id="formId" class="flex flex-col gap-4" novalidate @submit.prevent="submit">
            <LabeledField :label="t('siteLogo.ruleNameLabel')" :hint="t('siteLogo.ruleNameHint', { max: SITE_LOGO_RULE_NAME_MAX })" :error="tried || problems.name === 'LENGTH' ? UtilSiteLogoRuleMessages.nameProblem(t, problems.name) : ''">
                <template #default="{ id, describedBy, invalid }">
                    <InputText :id="id" v-model="form.name" name="ruleName" autocomplete="off" size="small" :maxlength="SITE_LOGO_RULE_NAME_MAX" :placeholder="t('siteLogo.ruleNamePlaceholder')" :invalid="invalid" :aria-describedby="describedBy" aria-required="true" class="w-full max-w-sm" />
                </template>
            </LabeledField>

            <div class="flex flex-col gap-1.5">
                <span :id="`${uid}-mode`" class="text-sm font-medium">{{ t('siteLogo.ruleModeLabel') }}</span>
                <SelectButton v-model="form.mode" :options="modeOptions" option-label="label" option-value="value" :allow-empty="false" size="small" :aria-labelledby="`${uid}-mode`" class="self-start" />
            </div>

            <!-- ① 날짜 직접 -->
            <template v-if="form.mode === 'DATES'">
                <div class="flex items-center gap-3">
                    <ToggleSwitch :model-value="form.yearly" :input-id="`${uid}-yearly`" :aria-describedby="`${uid}-yearly-help`" @update:model-value="onYearlyChange" />
                    <div>
                        <label :for="`${uid}-yearly`" class="text-sm">{{ t('siteLogo.ruleYearly') }}</label>
                        <p :id="`${uid}-yearly-help`" class="text-xs text-muted-color">{{ t('siteLogo.ruleYearlyHelp') }}</p>
                    </div>
                </div>

                <div role="group" :aria-labelledby="`${uid}-dates`" class="flex flex-col gap-1.5">
                    <span :id="`${uid}-dates`" class="text-sm font-medium">{{ t('siteLogo.ruleDatesLabel') }}</span>

                    <div v-if="form.yearly" class="flex flex-wrap items-center gap-x-2 gap-y-2">
                        <Select v-model="form.startMonth" :options="monthOptions" option-label="label" option-value="value" size="small" class="w-24" :aria-label="t('siteLogo.ruleStartMonth')" @update:model-value="clampDays" />
                        <Select v-model="form.startDay" :options="dayOptions(form.startMonth)" option-label="label" option-value="value" size="small" class="w-24" :aria-label="t('siteLogo.ruleStartDay')" />
                        <span class="text-muted-color" aria-hidden="true">~</span>
                        <Select v-model="form.endMonth" :options="monthOptions" option-label="label" option-value="value" size="small" class="w-24" :aria-label="t('siteLogo.ruleEndMonth')" @update:model-value="clampDays" />
                        <Select v-model="form.endDay" :options="dayOptions(form.endMonth)" option-label="label" option-value="value" size="small" class="w-24" :aria-label="t('siteLogo.ruleEndDay')" />
                    </div>
                    <DateRangeField v-else v-model:from="form.from" v-model:to="form.to" :from-label="t('siteLogo.ruleStartDate')" :to-label="t('siteLogo.ruleEndDate')" />

                    <p v-if="tried && problems.dates" role="alert" class="text-xs text-red-600 dark:text-red-400">{{ UtilSiteLogoRuleMessages.datesProblem(t, problems.dates) }}</p>
                    <p v-else class="text-xs text-muted-color">{{ form.yearly ? t('siteLogo.ruleDatesYearlyHelp') : t('siteLogo.ruleDatesHelp') }}</p>
                </div>
            </template>

            <!-- ② 쉬는 날 표에서 고르기 -->
            <template v-else>
                <LabeledField :label="t('siteLogo.ruleHolidayLabel')" :help="t('siteLogo.ruleHolidayHelp')" :error="tried && problems.holiday ? t('siteLogo.ruleHolidayRequired') : ''">
                    <template #default="{ id, describedBy, invalid }">
                        <Select v-model="form.holidayName" :input-id="id" :options="holidaySelectOptions" option-label="label" option-value="value" :placeholder="t('siteLogo.ruleHolidayPlaceholder')" :invalid="invalid" :aria-describedby="describedBy" size="small" class="w-full max-w-sm" />
                    </template>
                </LabeledField>
                <Message v-if="holidayFallback" severity="warn" :closable="false" size="small">{{ t('siteLogo.ruleHolidayFallback') }}</Message>
            </template>

            <!-- 기념일 로고는 날 단위로 고른다(두 벌 가운데 보이는 쪽은 평소 로고를 따른다) -->
            <LabeledField :label="t('siteLogo.ruleBundleLabel')" :help="t('siteLogo.anniversaryStyleNote')" :error="tried && problems.bundle ? t('siteLogo.ruleBundleRequired') : ''">
                <template #default="{ id, describedBy, invalid }">
                    <SiteLogoBundleSelect v-model="form.bundleId" :bundles="bundles" day-unit :input-id="id" :invalid="invalid" :described-by="describedBy" />
                </template>
            </LabeledField>
        </form>
        <template #footer>
            <Button type="button" :label="t('common.cancel')" severity="secondary" variant="text" @click="emit('update:visible', false)" />
            <Button type="submit" :form="formId" :label="rule ? t('common.apply') : t('common.add')" />
        </template>
    </Dialog>
</template>

<script setup lang="ts">
import type {
    SiteLogoAnniversaryRule,
    SiteLogoBundle,
    SiteLogoHolidayOption,
    SiteLogoRuleDateMode,
} from '~/composables/site/api/useSiteLogoLibraryApi.types';
import { SITE_LOGO_RULE_NAME_MAX } from '~/composables/site/api/useSiteLogoLibraryApi.types';
import UtilSiteLogoRule from '~/utils/util-site-logo-rule';
import UtilSiteLogoRuleMessages from '~/utils/util-site-logo-rule-messages';

interface Props {
    visible: boolean;
    /** 고칠 줄 — null 이면 새 줄 */
    rule?: SiteLogoAnniversaryRule | null;
    /** 보관함의 묶음 */
    bundles: SiteLogoBundle[];
    /** 쉬는 날 표에서 고를 수 있는 이름과 날짜들 */
    holidays?: SiteLogoHolidayOption[];
    /** 쉬는 날 표를 못 받았거나 비어 있다(고를 것이 없다) */
    holidayFallback?: boolean;
    /** 새 줄의 묶음 기본값 */
    defaultBundleId?: string;
    /** 오늘(`YYYY-MM-DD`, 표시 시간대) — 새 줄의 날짜 기본값 */
    today?: string;
}

const props = withDefaults(defineProps<Props>(), { rule: null, holidays: () => [], holidayFallback: false, defaultBundleId: '', today: '' });
const emit = defineEmits<{ 'update:visible': [visible: boolean]; submit: [rule: SiteLogoAnniversaryRule] }>();

const { t } = useI18n();
const time = useDisplayTime();
const uid = useId();
const formId = `${uid}-form`;

interface RuleForm {
    name: string;
    mode: SiteLogoRuleDateMode;
    yearly: boolean;
    /** 매년 반복이 꺼져 있을 때 — `YYYY-MM-DD`, 비면 '' */
    from: string;
    to: string;
    /** 매년 반복일 때 — 월·일 숫자 */
    startMonth: number;
    startDay: number;
    endMonth: number;
    endDay: number;
    holidayName: string | null;
    bundleId: string | null;
}

const form = reactive<RuleForm>({ name: '', mode: 'DATES', yearly: true, from: '', to: '', startMonth: 1, startDay: 1, endMonth: 1, endDay: 1, holidayName: null, bundleId: null });
const tried = ref(false);

function todayMonthDay(): { month: number; day: number } {
    return UtilSiteLogoRule.parseMonthDay(UtilSiteLogoRule.monthDayOf(props.today)) ?? { month: 1, day: 1 };
}

function fill(): void {
    const rule = props.rule;
    const today = todayMonthDay();
    const start = (rule?.yearly ? UtilSiteLogoRule.parseMonthDay(rule.startDate) : null) ?? today;
    const end = (rule?.yearly ? UtilSiteLogoRule.parseMonthDay(rule.endDate) : null) ?? start;
    Object.assign(form, {
        name: rule?.name ?? '',
        mode: rule?.mode ?? 'DATES',
        yearly: rule ? rule.yearly : true,
        from: rule && !rule.yearly ? (rule.startDate ?? '') : '',
        to: rule && !rule.yearly ? (rule.endDate ?? '') : '',
        startMonth: start.month,
        startDay: start.day,
        endMonth: end.month,
        endDay: end.day,
        holidayName: rule?.holidayName ?? null,
        bundleId: rule?.bundleId ?? (props.defaultBundleId || null),
    } satisfies RuleForm);
    tried.value = false;
}

// 창이 열릴 때 그 줄 값으로 폼을 맞춘다
watch(
    () => props.visible,
    (visible) => {
        if (visible) fill();
    },
    { immediate: true },
);

const modeOptions = computed<{ label: string; value: SiteLogoRuleDateMode }[]>(() => [
    { label: t('siteLogo.ruleModeDates'), value: 'DATES' },
    { label: t('siteLogo.ruleModeHoliday'), value: 'HOLIDAY' },
]);
const monthOptions = computed(() => Array.from({ length: 12 }, (_, i) => ({ label: t('siteLogo.monthOption', { month: i + 1 }), value: i + 1 })));

function dayOptions(month: number): { label: string; value: number }[] {
    return Array.from({ length: UtilSiteLogoRule.lastDayOf(month) }, (_, i) => ({ label: t('siteLogo.dayOption', { day: i + 1 }), value: i + 1 }));
}

/** 달을 바꾸면 그 달에 없는 날(31일 등)을 마지막 날로 당긴다 */
function clampDays(): void {
    form.startDay = Math.min(form.startDay, UtilSiteLogoRule.lastDayOf(form.startMonth));
    form.endDay = Math.min(form.endDay, UtilSiteLogoRule.lastDayOf(form.endMonth));
}

/** 「매년 반복」을 켜고 끌 때 이미 넣은 날짜를 다른 모양으로 옮겨 준다 */
function onYearlyChange(yearly: boolean): void {
    if (yearly) {
        const start = UtilSiteLogoRule.parseMonthDay(UtilSiteLogoRule.monthDayOf(form.from));
        const end = UtilSiteLogoRule.parseMonthDay(UtilSiteLogoRule.monthDayOf(form.to));
        if (start) {
            form.startMonth = start.month;
            form.startDay = start.day;
        }
        if (end ?? start) {
            form.endMonth = (end ?? start)!.month;
            form.endDay = (end ?? start)!.day;
        }
    } else if (!form.from && !form.to && props.today) {
        // 연은 올해로 — 끝이 시작보다 앞이면 다음 해로 넘긴다
        const year = Number(props.today.slice(0, 4));
        const startMd = UtilSiteLogoRule.monthDay(form.startMonth, form.startDay);
        const endMd = UtilSiteLogoRule.monthDay(form.endMonth, form.endDay);
        form.from = `${year}-${startMd}`;
        form.to = `${endMd < startMd ? year + 1 : year}-${endMd}`;
    }
    form.yearly = yearly;
}

/** 쉬는 날 고르개 — 이름 옆에 가까운 날짜를 붙인다. 저장된 이름이 표에 없어도 고른 값은 남긴다 */
const holidaySelectOptions = computed(() => {
    const list = props.holidays.map((holiday) => {
        const first = holiday.dates[0];
        const last = holiday.dates[holiday.dates.length - 1];
        const dates = !first || !last ? '' : first === last ? time.value.serviceDate(first) : t('siteLogo.whenRange', { from: time.value.serviceDate(first), to: time.value.serviceDate(last) });
        return { label: dates ? t('siteLogo.ruleHolidayOption', { name: holiday.name, dates }) : holiday.name, value: holiday.name };
    });
    if (form.holidayName && !list.some((option) => option.value === form.holidayName)) list.push({ label: form.holidayName, value: form.holidayName });
    return list;
});

/** 폼 → 기념일 줄(새 줄의 id 는 임시 — 저장하면 서버가 정한다) */
const draft = computed<SiteLogoAnniversaryRule>(() => {
    const dates = form.mode === 'DATES';
    return {
        id: props.rule?.id ?? `new-${Date.now().toString(36)}`,
        name: form.name.trim(),
        enabled: props.rule?.enabled ?? true,
        mode: form.mode,
        startDate: !dates ? null : form.yearly ? UtilSiteLogoRule.monthDay(form.startMonth, form.startDay) : form.from || null,
        endDate: !dates ? null : form.yearly ? UtilSiteLogoRule.monthDay(form.endMonth, form.endDay) : form.to || null,
        yearly: dates && form.yearly,
        holidayName: dates ? null : form.holidayName,
        bundleId: form.bundleId ?? '',
    };
});
const problems = computed(() => UtilSiteLogoRule.validate(draft.value, props.bundles));

function submit(): void {
    tried.value = true;
    if (Object.keys(problems.value).length > 0) return;
    emit('submit', { ...draft.value });
}
</script>
