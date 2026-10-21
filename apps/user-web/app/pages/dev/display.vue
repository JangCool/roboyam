<template>
    <!--
        표시 값 쇼케이스 (개발 모드 전용 — `/dev/display`). 운영 빌드·`generate` 에서는 404 다(아래 라우트 미들웨어).
        표시 값(언어·시간대·시간 형식·날짜 형식)을 바꾸면 화면 문구(i18n)·날짜 고르개(`AppDatePicker`·`AppDateTimePicker`)·같은 순간을 여러 모양으로 그린 표(`UtilTime`)·동적 이름(`nameEn`)이
        한꺼번에 바뀌는지 눈으로 확인하는 화면이다. 이 화면의 문구도 i18n 키(`dev.*`)로 쓴다.
        폼의 값은 공유 레이어 상태(`useDisplay`)에 곧바로 채운다 — 이 화면을 벗어나도 새로고침 전까지는 바뀐 값이 남는다("기본으로" 단추로 시스템 폴백으로 되돌린다).
    -->
    <div class="mx-auto flex max-w-4xl flex-col gap-8" data-testid="dev-display">
        <header>
            <p class="m-0 text-xs font-semibold text-muted-color">{{ t('dev.badge') }}</p>
            <h1 class="m-0 mt-0.5 text-lg font-semibold">{{ t('dev.title') }}</h1>
            <p class="m-0 mt-1 text-sm leading-relaxed text-muted-color">{{ t('dev.intro') }}</p>
        </header>

        <!-- 1. 표시 값 바꾸기 -->
        <section aria-labelledby="dev-display-form" class="rounded-xl border border-surface bg-surface-0 p-4 dark:bg-surface-900">
            <h2 id="dev-display-form" class="m-0 mb-3 text-base font-semibold">{{ t('dev.formTitle') }}</h2>
            <DisplayValueForm v-model="form" />
            <div class="mt-3 flex flex-wrap items-center gap-3 text-xs text-muted-color">
                <span>{{ t('dev.currentValue') }}: <code data-testid="dev-display-json">{{ JSON.stringify(values) }}</code></span>
                <span>{{ t('dev.serviceZone') }}: <code>{{ serviceZoneId }}</code></span>
                <Button type="button" :label="t('dev.resetDefaults')" size="small" severity="secondary" variant="outlined" data-testid="dev-display-reset" @click="resetDisplay" />
            </div>
        </section>

        <!-- 2. 화면 문구 (i18n) -->
        <section aria-labelledby="dev-display-i18n" class="rounded-xl border border-surface bg-surface-0 p-4 dark:bg-surface-900">
            <h2 id="dev-display-i18n" class="m-0 mb-1 text-base font-semibold">{{ t('dev.i18nTitle') }}</h2>
            <p class="m-0 mb-3 text-xs leading-relaxed text-muted-color">{{ t('dev.i18nIntro') }}</p>
            <table class="w-full border-collapse text-sm" data-testid="dev-i18n-table">
                <thead>
                    <tr class="border-b border-surface text-left text-xs text-muted-color">
                        <th class="py-1.5 pr-4 font-medium">{{ t('dev.keyHeader') }}</th>
                        <th class="py-1.5 font-medium">{{ t('dev.textHeader') }}</th>
                    </tr>
                </thead>
                <tbody>
                    <tr v-for="row in keyRows" :key="row.key" class="border-b border-surface last:border-0">
                        <td class="py-1.5 pr-4 align-top"><code>{{ row.key }}</code></td>
                        <td class="py-1.5 align-top" :data-testid="`dev-key-${row.key}`">{{ row.text }}</td>
                    </tr>
                </tbody>
            </table>

            <h3 class="m-0 mb-1 mt-5 text-sm font-semibold">{{ t('dev.namesTitle') }}</h3>
            <p class="m-0 mb-3 text-xs leading-relaxed text-muted-color">{{ t('dev.namesIntro') }}</p>
            <table class="w-full border-collapse text-sm" data-testid="dev-names-table">
                <thead>
                    <tr class="border-b border-surface text-left text-xs text-muted-color">
                        <th class="py-1.5 pr-4 font-medium">{{ t('dev.nameDefault') }}</th>
                        <th class="py-1.5 pr-4 font-medium">{{ t('dev.nameEnglish') }}</th>
                        <th class="py-1.5 font-medium">{{ t('dev.nameShown') }}</th>
                    </tr>
                </thead>
                <tbody>
                    <tr v-for="item in nameSamples" :key="item.name" class="border-b border-surface last:border-0">
                        <td class="py-1.5 pr-4">{{ item.name }}</td>
                        <td class="py-1.5 pr-4 text-muted-color">{{ item.nameEn ?? t('dev.nameEnMissing') }}</td>
                        <td class="py-1.5 font-medium" data-testid="dev-name-shown">{{ localizedName(item.name, item.nameEn) }}</td>
                    </tr>
                </tbody>
            </table>
        </section>

        <!-- 3. 날짜 고르개 -->
        <section aria-labelledby="dev-display-pickers" class="rounded-xl border border-surface bg-surface-0 p-4 dark:bg-surface-900">
            <h2 id="dev-display-pickers" class="m-0 mb-1 text-base font-semibold">{{ t('dev.pickersTitle') }}</h2>
            <p class="m-0 mb-4 text-xs leading-relaxed text-muted-color">{{ t('dev.pickersIntro') }}</p>
            <div class="grid grid-cols-1 gap-x-6 gap-y-5 md:grid-cols-2">
                <div class="flex flex-col gap-1.5">
                    <label :for="`${uid}-at`" class="text-sm font-medium">{{ t('dev.dateTimeBasic') }}</label>
                    <AppDateTimePicker v-model="at" :input-id="`${uid}-at`" />
                    <p class="m-0 text-xs text-muted-color">{{ t('dev.valueInstant') }}: <code data-testid="dev-at-value">{{ at || t('dev.valueEmpty') }}</code></p>
                    <p class="m-0 text-xs text-muted-color">{{ t('dev.shown') }}: <b data-testid="dev-at-text">{{ time.full(at) || '—' }}</b></p>
                </div>

                <div class="flex flex-col gap-1.5">
                    <label :for="`${uid}-range`" class="text-sm font-medium">{{ t('dev.dateTimeRange') }}</label>
                    <AppDateTimePicker v-model="ranged" :input-id="`${uid}-range`" :min="rangeMin" :max="rangeMax" :minute-step="10" />
                    <p class="m-0 text-xs text-muted-color">{{ t('dev.valueInstant') }}: <code>{{ ranged || t('dev.valueEmpty') }}</code></p>
                </div>

                <div class="flex flex-col gap-1.5">
                    <label :for="`${uid}-readonly`" class="text-sm font-medium">{{ t('dev.dateTimeReadonly') }}</label>
                    <AppDateTimePicker :model-value="sample" :input-id="`${uid}-readonly`" readonly :show-zone="false" />
                </div>

                <div class="flex flex-col gap-1.5">
                    <label :for="`${uid}-disabled`" class="text-sm font-medium">{{ t('dev.dateTimeDisabled') }}</label>
                    <AppDateTimePicker :model-value="sample" :input-id="`${uid}-disabled`" disabled :show-zone="false" />
                </div>

                <div class="flex flex-col gap-1.5">
                    <label :for="`${uid}-day`" class="text-sm font-medium">{{ t('dev.dateOnly') }}</label>
                    <AppDatePicker v-model="day" :input-id="`${uid}-day`" />
                    <p class="m-0 text-xs text-muted-color">{{ t('dev.valueDate') }}: <code data-testid="dev-day-value">{{ day || t('dev.valueEmpty') }}</code> · {{ t('dev.shown') }}: <b>{{ time.serviceDate(day) || '—' }}</b></p>
                </div>

                <div class="flex flex-col gap-1.5">
                    <label :for="`${uid}-dayrange`" class="text-sm font-medium">{{ t('dev.dateRange') }}</label>
                    <AppDatePicker v-model="dayRanged" :input-id="`${uid}-dayrange`" :min="dayMin" :max="dayMax" />
                    <p class="m-0 text-xs text-muted-color">{{ t('dev.valueDate') }}: <code>{{ dayRanged || t('dev.valueEmpty') }}</code></p>
                </div>
            </div>
        </section>

        <!-- 4. 같은 순간, 여러 모양 -->
        <section aria-labelledby="dev-display-table" class="rounded-xl border border-surface bg-surface-0 p-4 dark:bg-surface-900">
            <h2 id="dev-display-table" class="m-0 mb-1 text-base font-semibold">{{ t('dev.tableTitle') }}</h2>
            <p class="m-0 text-xs leading-relaxed text-muted-color">{{ t('dev.tableNote') }}</p>
            <div class="mb-3 mt-2 flex flex-wrap items-center gap-2">
                <label :for="`${uid}-sample`" class="text-sm font-medium">{{ t('dev.instant') }}</label>
                <Select
                    v-model="sampleKey"
                    :input-id="`${uid}-sample`"
                    :options="sampleOptions"
                    option-label="label"
                    option-value="key"
                    class="w-64"
                    data-testid="dev-sample-select"
                />
                <code class="text-xs text-muted-color">{{ sample }}</code>
            </div>
            <table class="w-full border-collapse text-sm" data-testid="dev-time-table">
                <thead>
                    <tr class="border-b border-surface text-left text-xs text-muted-color">
                        <th class="py-1.5 pr-4 font-medium">{{ t('dev.funcHeader') }}</th>
                        <th class="py-1.5 pr-4 font-medium">{{ t('dev.descHeader') }}</th>
                        <th class="py-1.5 font-medium">{{ t('dev.resultHeader') }}</th>
                    </tr>
                </thead>
                <tbody>
                    <tr v-for="row in rows" :key="row.name" class="border-b border-surface last:border-0">
                        <td class="py-1.5 pr-4 align-top"><code>{{ row.name }}</code></td>
                        <td class="py-1.5 pr-4 align-top text-xs text-muted-color">{{ row.note }}</td>
                        <td class="py-1.5 align-top tabular-nums" :data-testid="`dev-row-${row.name}`">{{ row.value }}</td>
                    </tr>
                </tbody>
            </table>
        </section>
    </div>
</template>

<script setup lang="ts">
import UtilTime, { type DisplayValues } from '#layers/display-time/utils/util-time';

/**
 * 개발 전용 쇼케이스 — 운영 빌드에서는 열리지 않는다. `import.meta.dev` 는 빌드 때 상수로 바뀌어, 운영 빌드의 이 미들웨어는 늘 404 로 막는다.
 */
definePageMeta({
    middleware: () => {
        if (!import.meta.dev) return abortNavigation(createError({ statusCode: 404, statusMessage: 'Page Not Found' }));
    },
});
useSeoMeta({ title: '표시 값 확인', robots: 'noindex' });

const { t } = useI18n();
const { localizedName } = useLocalizedName();
const uid = useId();
const { values, serviceZoneId, setDisplay, resetDisplay } = useDisplay();
const time = useDisplayTime();

/** 폼의 값은 공유 레이어 상태 그대로 — 고르는 즉시 사이트 전체(이 화면)의 표시 값이 바뀐다 */
const form = computed<DisplayValues>({
    get: () => values.value,
    set: (next) => {
        setDisplay(next);
    },
});

/* ── 화면 문구 (i18n) ── */
/** 같은 키를 지금 언어로 — 언어가 바뀌면 다시 계산된다. 마지막 줄은 자리표시자 예 */
const KEYS = ['common.save', 'common.cancel', 'common.retry', 'common.loading', 'display.zoneLabel', 'display.previewTitle', 'site.nameLabel', 'site.publicUrlHelp'];
const keyRows = computed(() => [
    ...KEYS.map((key) => ({ key, text: t(key) })),
    { key: 'common.totalCount', text: `${t('dev.sampleCount')}: ${t('common.totalCount', { count: 3 })}` },
]);

/** 동적 이름 견본 — 영문명이 있는 것/없는 것 */
const nameSamples: { name: string; nameEn: string | null }[] = [
    { name: 'AI·테크', nameEn: 'AI & Tech' },
    { name: '로봇', nameEn: 'Robots' },
    { name: '스포츠', nameEn: null },
];

/* ── 고르개 ── */
const at = ref(new Date().toISOString());
const ranged = ref('');
const day = ref('');
const dayRanged = ref('');
const DAY_MS = 86_400_000;
const nowMs = Date.now();
const rangeMin = new Date(nowMs - 3 * DAY_MS).toISOString();
const rangeMax = new Date(nowMs + 3 * DAY_MS).toISOString();
/** 달력 날짜 글자는 시간대와 무관한 글자이므로 서비스 하루(`UtilTime.serviceDayOf`)로 만든다 */
const dayMin = UtilTime.serviceDayOf(nowMs) ?? '';
const dayMax = UtilTime.serviceDayOf(nowMs + 14 * DAY_MS) ?? '';

/* ── 같은 순간 표 ── */
const SAMPLES = [
    { key: 'now', label: 'dev.sampleNow', offset: 0 },
    { key: 'min3', label: 'dev.sampleMin3', offset: -3 * 60_000 },
    { key: 'hour5', label: 'dev.sampleHour5', offset: -5 * 3_600_000 },
    { key: 'yesterday', label: 'dev.sampleYesterday', offset: -DAY_MS },
    { key: 'day3', label: 'dev.sampleDay3', offset: -3 * DAY_MS },
    { key: 'year1', label: 'dev.sampleYear1', offset: -365 * DAY_MS },
    { key: 'future', label: 'dev.sampleFuture', offset: DAY_MS },
];
const sampleOptions = computed(() => SAMPLES.map((s) => ({ key: s.key, label: t(s.label) })));
const sampleKey = ref('hour5');
const base = Date.now();
const sample = computed(() => new Date(base + (SAMPLES.find((o) => o.key === sampleKey.value)?.offset ?? 0)).toISOString());

const rows = computed(() => {
    const f = time.value;
    const iso = sample.value;
    return [
        { name: 'full', note: t('dev.rowFull'), value: f.full(iso) },
        { name: 'date', note: t('dev.rowDate'), value: f.date(iso) },
        { name: 'dateDay', note: t('dev.rowDateDay'), value: f.dateDay(iso) },
        { name: 'clock', note: t('dev.rowClock'), value: f.clock(iso) },
        { name: 'listLabel', note: t('dev.rowListLabel'), value: f.listLabel(iso, { now: base }) },
        { name: 'detail', note: t('dev.rowDetail'), value: f.detail(iso, base) },
        { name: 'ago', note: t('dev.rowAgo'), value: f.ago(iso, base) },
        { name: 'shortDate', note: t('dev.rowShortDate'), value: f.shortDate(iso) },
        { name: 'shortDateTime', note: t('dev.rowShortDateTime'), value: f.shortDateTime(iso) },
        { name: 'slot', note: t('dev.rowSlot'), value: f.slot(iso, base) },
        { name: 'reserved', note: t('dev.rowReserved'), value: f.reserved(iso, base) },
        { name: 'zoneLabel', note: t('dev.rowZoneLabel'), value: f.zoneLabel(base) },
        { name: 'zoneTag', note: t('dev.rowZoneTag'), value: f.zoneTag(base) },
        { name: 'serviceDate', note: t('dev.rowServiceDate'), value: f.serviceDate('2026-10-02') },
    ];
});
</script>
