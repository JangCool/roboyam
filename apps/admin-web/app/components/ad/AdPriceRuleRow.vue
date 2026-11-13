<template>
    <!--
        상품 가격 한 줄(광고 기본 설정 「상품 가격」 탭 — `pages/ads/placements.vue`) — 보기 전용 카드. 칸을 줄에서 직접 고치지 않는다.
          머리: 상품 이름 · 단위 딱지(하루 단위/건당) · 예약 딱지(「예약 2건 · 다음 11월 22일 00:00」) · 신청 받기 스위치(바로 적용 — 끌 때 확인은 부모) · [가격 변경]
          본문 두 칸 — 왼쪽: 하루/1건 단가(크게 · 부가세 별도) → 「신청 조건」 칸마다 값 + 한 줄 뜻(동시 한도 · 최대 일수 · 시작일 선택 범위 「60일 뒤까지 — 오늘 신청하면 내일 ~ 60일 뒤 중에서 시작일을 골라요」) → 예약 목록
                       · 오른쪽: 사업자 화면 미리보기 `AdPriceProductPreview`(때 고르기 Select — 좁으면 아래로)
          예약 목록(적용 시각 차례 · 예약마다 카드): 「11월 22일 00:00부터」 딱지 · [예약 수정]·[예약 취소] · 바뀌는 칸 줄마다 「단가 50,000원 → 60,000원」(바로 앞 값 — 앞 예약이 적용된 뒤 값 기준) · [고치기]·[취소]
          [변경 내역]: 최근 적용된 변경 몇 건(적용 시각이 지난 예약 · 받기 켬/끔) — 접어 둔다.
        서버는 부르지 않는다 — 동작은 `change-price`·`edit-schedule`·`cancel-schedule`·`set-accepting` 으로 올린다. 단추·스위치는 `manageable`(저장 권한)일 때만.
    -->
    <section
        class="@container flex min-w-0 flex-col gap-3 rounded-lg border p-3 transition-colors sm:p-4"
        :class="highlighted ? 'border-primary-400 bg-primary-50/40 dark:border-primary-600 dark:bg-primary-950/30' : 'border-(--line)'"
        :aria-labelledby="titleId"
        :data-testid="`ad-price-row-${rule.product}`"
    >
        <div class="flex flex-wrap items-center gap-x-3 gap-y-2">
            <div class="flex min-w-0 flex-1 flex-wrap items-center gap-2">
                <h4 :id="titleId" class="m-0 text-sm font-medium">{{ t(UtilAdPrice.nameKey(rule.product)) }}</h4>
                <Tag severity="secondary" :value="t(`ad.priceUnit.${rule.unit}`)" class="shrink-0" />
                <Tag v-if="chain.length" severity="warn" icon="pi pi-clock" :value="t('ad.priceRowScheduleTag', { n: chain.length, at: time.full(chain[0]!.schedule.effectiveAt) })" class="shrink-0" data-testid="ad-price-row-schedule-tag" />
            </div>
            <div class="flex shrink-0 flex-wrap items-center gap-x-4 gap-y-2">
                <div class="flex items-center gap-2">
                    <ToggleSwitch :model-value="rule.accepting" :input-id="`${idBase}-accepting`" :disabled="!manageable || busy" @update:model-value="(v: boolean) => emit('set-accepting', v)" />
                    <label :for="`${idBase}-accepting`" class="text-sm">{{ t('ad.priceAccepting') }}</label>
                </div>
                <Button v-if="manageable" :label="t('ad.priceChange')" icon="pi pi-calendar-plus" size="small" :disabled="busy" data-testid="ad-price-row-change" @click="emit('change-price')" />
            </div>
        </div>

        <!-- 본문 두 칸 — 왼쪽: 하루 단가 → 신청 조건 → 이 상품의 예약 · 오른쪽: 사업자 화면 미리보기(좁으면 아래로) -->
        <div class="grid items-start gap-4 @2xl:grid-cols-[minmax(0,1fr)_minmax(18rem,26rem)]" data-testid="ad-price-row-values">
            <div class="flex min-w-0 flex-col gap-3">
                <!-- 지금 가격 — 테두리·여백 없이(아래 구분선으로 신청 조건과 나눈다) · 사업자 화면 상품 카드 글자(이름은 카드 제목에 있어 뺌 · 위치·크기·동시 한도 · 가격 크게(부가세 별도) — 예약 안내는 미리보기 카드에만) -->
                <div class="m-0 flex flex-col gap-1 p-0" :class="{ 'opacity-70': !rule.accepting }" data-testid="ad-price-row-price-card">
                    <span class="text-xs text-muted-color [overflow-wrap:anywhere]">{{ UtilAdMessages.priceCardWhere(t, rule.product, rule.unit === 'POST' ? null : rule.capacity) }}</span>
                    <span class="flex flex-wrap items-baseline gap-x-2">
                        <span class="text-lg font-bold tabular-nums" data-testid="ad-price-row-unit-price">{{ UtilAdMessages.priceCardPrice(t, rule.unit, rule.unitPrice, locale) }}</span>
                        <span class="text-xs text-muted-color">{{ t('ad.priceVatExcluded') }}</span>
                    </span>
                </div>

                <Divider class="m-0!" />

                <!-- 신청 조건 — 칸마다 값 + 한 줄 뜻 -->
                <div class="flex min-w-0 flex-col gap-1" :class="{ 'opacity-70': !rule.accepting }">
                    <h5 class="m-0 text-xs font-semibold text-muted-color">{{ t('ad.priceConditions') }}</h5>
                    <dl class="m-0 flex flex-col divide-y divide-(--line)">
                        <div v-for="row in conditionRows" :key="row.field" class="grid grid-cols-[7.5rem_minmax(0,1fr)] items-baseline gap-x-3 py-1.5" :data-testid="`ad-price-row-${row.field}`">
                            <dt class="text-xs text-muted-color">{{ t(`ad.priceField.${row.field}`) }}</dt>
                            <dd class="m-0 flex min-w-0 flex-wrap items-baseline gap-x-2">
                                <span class="text-sm font-semibold tabular-nums">{{ row.value }}</span>
                                <span class="text-xs text-muted-color [overflow-wrap:anywhere]">{{ row.help }}</span>
                            </dd>
                        </div>
                    </dl>
                </div>
                <p v-if="!rule.accepting" class="m-0 text-xs text-muted-color">{{ t('ad.priceClosed') }}</p>

                <!-- 이 상품의 예약 — 예약마다 카드 한 장(오른쪽 「다가오는 예약」 카드와 같은 모양 · 상품 이름 대신 [예약 수정]·[예약 취소]) -->
                <div v-if="chain.length" class="flex flex-col gap-1.5" data-testid="ad-price-row-schedules">
                    <h5 class="m-0 text-xs font-semibold">{{ t('ad.priceRowSchedules', { n: chain.length }) }}</h5>
                    <ul class="m-0 flex list-none flex-col gap-2 p-0">
                        <li v-for="item in chain" :key="item.schedule.id" class="flex flex-col gap-2 rounded-lg border border-(--line) bg-surface-50 p-3 dark:bg-surface-950" data-testid="ad-price-row-schedule">
                            <div class="flex flex-wrap items-center justify-between gap-x-2 gap-y-1">
                                <span class="rounded-md bg-amber-100 px-1.5 py-0.5 text-xs font-semibold tabular-nums text-amber-800 dark:bg-amber-950/60 dark:text-amber-300">{{ t('ad.priceFrom', { at: time.full(item.schedule.effectiveAt) }) }}</span>
                                <div v-if="manageable" class="flex shrink-0 gap-1">
                                    <Button :label="t('ad.priceScheduleEdit')" icon="pi pi-pencil" size="small" severity="secondary" text :disabled="busy" data-testid="ad-price-row-schedule-edit" @click="emit('edit-schedule', item.schedule)" />
                                    <Button :label="t('ad.priceScheduleCancel')" icon="pi pi-times" size="small" severity="danger" text :disabled="busy" data-testid="ad-price-row-schedule-cancel" @click="emit('cancel-schedule', item.schedule)" />
                                </div>
                            </div>
                            <span v-if="!item.diff.length" class="text-xs text-muted-color">{{ t('ad.priceScheduleNoDiff') }}</span>
                            <span v-else class="flex flex-col gap-1 border-t border-(--line) pt-2">
                                <span v-for="d in item.diff" :key="d.field" class="flex flex-wrap items-baseline justify-between gap-x-2 text-xs">
                                    <span class="text-muted-color">{{ t(`ad.priceField.${d.field}`) }}</span>
                                    <span class="tabular-nums [overflow-wrap:anywhere]">{{ valueText(d.field, d.before) }} → <b class="font-semibold">{{ valueText(d.field, d.after) }}</b></span>
                                </span>
                            </span>
                        </li>
                    </ul>
                </div>
            </div>

            <!-- 사업자 화면 미리보기 -->
            <div class="min-w-0 rounded-md bg-surface-100 p-3 dark:bg-surface-950">
                <AdPriceProductPreview :rule="rule" />
            </div>
        </div>

        <!-- 최근 적용된 변경(접어 둔다) -->
        <div v-if="rule.recentChanges.length" class="flex flex-col gap-1.5">
            <Button
                :label="t('ad.priceRecentToggle', { n: rule.recentChanges.length })"
                :icon="recentOpen ? 'pi pi-chevron-up' : 'pi pi-chevron-down'"
                icon-pos="right"
                size="small"
                severity="secondary"
                text
                class="self-start"
                :aria-expanded="recentOpen"
                :aria-controls="`${idBase}-recent`"
                @click="recentOpen = !recentOpen"
            />
            <ul v-if="recentOpen" :id="`${idBase}-recent`" class="m-0 flex list-none flex-col gap-1 p-0 text-xs" data-testid="ad-price-row-recent">
                <li v-for="change in rule.recentChanges" :key="change.id" class="flex flex-wrap items-baseline gap-x-2 [overflow-wrap:anywhere]">
                    <span class="tabular-nums text-muted-color">{{ time.full(change.changedAt) }}</span>
                    <span>{{ change.operatorName }}</span>
                    <span>{{ recentText(change.changes) }}</span>
                </li>
            </ul>
        </div>
    </section>
</template>

<script setup lang="ts">
import type { AdPriceField, AdPriceRecentChange, AdPriceRule, AdPriceSchedule } from '~/composables/ad/api/useAdApi.types';
import UtilAdMessages from '~/utils/util-ad-messages';
import UtilAdPrice from '~/utils/util-ad-price';

interface Props {
    /** 요금표 한 줄(지금 값 · 예약들 · 최근 변경) */
    rule: AdPriceRule;
    /** 단추·스위치를 보일까(저장 권한 `ad.settings`) */
    manageable?: boolean;
    /** 이 상품에 요청이 도는 중(받기·취소) */
    busy?: boolean;
    /** 요약 카드에서 눌러 왔을 때 잠깐 강조 */
    highlighted?: boolean;
}

const props = withDefaults(defineProps<Props>(), { manageable: false, busy: false, highlighted: false });
const emit = defineEmits<{ 'change-price': []; 'edit-schedule': [schedule: AdPriceSchedule]; 'cancel-schedule': [schedule: AdPriceSchedule]; 'set-accepting': [accepting: boolean] }>();

const { t, locale } = useI18n();
const time = useDisplayTime();
const titleId = useId();
const idBase = useId();
const recentOpen = ref(false);

/** 신청 조건 세 칸 — 값 글자와 한 줄 뜻(시작일 선택 범위는 그 상품 숫자로 예를 든다) */
const conditionRows = computed(() => {
    const { unit, capacity, maxDays, maxLeadDays } = props.rule;
    return [
        { field: 'capacity' as const, value: valueText('capacity', unit === 'POST' ? null : capacity), help: unit === 'POST' ? t('ad.priceFieldHelp.capacityPost') : t('ad.priceFieldHelp.capacity') },
        { field: 'maxDays' as const, value: valueText('maxDays', maxDays), help: t('ad.priceFieldHelp.maxDays') },
        { field: 'maxLeadDays' as const, value: t('ad.priceLeadDaysValue', { n: maxLeadDays }), help: t('ad.priceLeadDaysExample', { n: maxLeadDays }) },
    ];
});
/** 예약 — 적용 시각 차례, 전→후는 바로 앞 값 기준 */
const chain = computed(() => UtilAdPrice.chain(props.rule));

function valueText(field: AdPriceField, value: number | boolean | null): string {
    return UtilAdMessages.priceRuleValue(t, field, value === null ? null : String(value), locale.value) ?? '';
}

function recentText(changes: AdPriceRecentChange['changes']): string {
    return changes
        .map((c) =>
            t('ad.priceScheduleDiffItem', {
                field: t(`ad.priceField.${c.field}`),
                before: UtilAdMessages.priceRuleValue(t, c.field, c.before, locale.value) ?? '',
                after: UtilAdMessages.priceRuleValue(t, c.field, c.after, locale.value) ?? '',
            }),
        )
        .join(' · ');
}
</script>
