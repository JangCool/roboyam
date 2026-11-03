<template>
    <!--
        가젯 ⚙ 설정(가볍게) — 제목 바꾸기 · 배경색(추천 · 흰색 · 옅은 9색, 2026-11-03) · 자동 새로 고침(끔 · 1분 · 5분, 기본 끔 — 탭이 안 보이면 멈춘다) · 종류별 칸(목록 개수 5/10 · 기간 7/30일 · 메모 본문 · 바로가기 화면).
        값은 그 가젯의 `config` 로 돌려준다(`apply`) — 화면 편집의 다른 변경과 함께 [저장] 해야 남는다. 창을 열 때마다 가젯의 지금 값에서 시작한다.
    -->
    <Dialog :visible="visible" modal :draggable="false" :header="t('dashboard.settingsTitle', { title: kindName })" :style="{ width: 'min(32rem, calc(100vw - 2rem))' }" @update:visible="(v: boolean) => emit('update:visible', v)">
        <form v-if="gadget && spec" :id="formId" class="flex flex-col gap-4" @submit.prevent="apply">
            <LabeledField v-slot="{ id }" :label="t('dashboard.settingsName')" :hint="t('dashboard.settingsNameHint', { n: UtilDashboardLayout.TITLE_MAX })">
                <InputText :id="id" v-model="draft.title" :maxlength="UtilDashboardLayout.TITLE_MAX" :placeholder="kindName" />
            </LabeledField>

            <fieldset class="m-0 flex min-w-0 flex-col gap-1.5 border-0 p-0">
                <legend class="mb-1.5 p-0 text-sm font-medium">{{ t('dashboard.settingsColor') }}</legend>
                <div class="flex flex-wrap gap-2">
                    <label v-for="c in colorChoices" :key="c.value" class="relative cursor-pointer" :title="c.label">
                        <input v-model="draft.color" type="radio" class="peer sr-only" :name="`${formId}-color`" :value="c.value" :aria-label="c.label" />
                        <span
                            class="flex size-8 items-center justify-center rounded-lg border-2 peer-checked:border-primary peer-focus-visible:outline-2 peer-focus-visible:outline-offset-2 peer-focus-visible:outline-primary"
                            :class="draft.color === c.value ? '' : 'border-(--line)'"
                            :style="c.style"
                        >
                            <i v-if="c.value === 'AUTO'" class="pi pi-sparkles text-xs text-muted-color" aria-hidden="true" />
                            <i v-else-if="draft.color === c.value" class="pi pi-check text-xs text-primary" aria-hidden="true" />
                        </span>
                    </label>
                </div>
                <p class="m-0 text-xs text-muted-color">{{ t('dashboard.settingsColorHelp', { color: t(`dashboard.color.${recommended}`) }) }}</p>
            </fieldset>

            <div v-if="spec.refreshable" class="flex flex-col gap-1.5">
                <span :id="`${formId}-refresh`" class="text-sm font-medium">{{ t('dashboard.settingsRefresh') }}</span>
                <SelectButton v-model="draft.refresh" :options="refreshOptions" option-label="label" option-value="value" :allow-empty="false" :aria-labelledby="`${formId}-refresh`" />
                <p class="m-0 text-xs text-muted-color">{{ t('dashboard.settingsRefreshHelp') }}</p>
            </div>

            <div v-if="spec.options.includes('limit')" class="flex flex-col gap-1.5">
                <span :id="`${formId}-limit`" class="text-sm font-medium">{{ t('dashboard.settingsLimit') }}</span>
                <SelectButton v-model="draft.limit" :options="limitOptions" option-label="label" option-value="value" :allow-empty="false" :aria-labelledby="`${formId}-limit`" />
            </div>

            <div v-if="spec.options.includes('days')" class="flex flex-col gap-1.5">
                <span :id="`${formId}-days`" class="text-sm font-medium">{{ t('dashboard.settingsDays') }}</span>
                <SelectButton v-model="draft.days" :options="daysOptions" option-label="label" option-value="value" :allow-empty="false" :aria-labelledby="`${formId}-days`" />
            </div>

            <LabeledField v-if="spec.options.includes('text')" v-slot="{ id }" :label="t('dashboard.settingsMemo')" :help="t('dashboard.settingsMemoCount', { n: draft.text.length, max: UtilDashboardLayout.MEMO_MAX })">
                <Textarea :id="id" v-model="draft.text" :maxlength="UtilDashboardLayout.MEMO_MAX" rows="6" auto-resize class="w-full" />
            </LabeledField>

            <fieldset v-if="spec.options.includes('links')" class="m-0 flex min-w-0 flex-col gap-2 border-0 p-0">
                <legend class="mb-1.5 p-0 text-sm font-medium">{{ t('dashboard.settingsLinks') }}</legend>
                <p class="m-0 text-xs text-muted-color">{{ t('dashboard.settingsLinksHelp', { n: draft.links.length, max: UtilDashboardLayout.LINKS_MAX }) }}</p>
                <div class="flex max-h-72 flex-col gap-3 overflow-y-auto rounded-lg border border-(--line) p-3">
                    <div v-for="group in linkGroups" :key="group.key" class="flex flex-col gap-1.5">
                        <span v-if="group.labelKey" class="text-[12px] font-semibold text-muted-color">{{ t(group.labelKey) }}</span>
                        <label v-for="c in group.items" :key="c.to" class="flex items-center gap-2 text-sm" :class="disabledLink(c.to) ? 'opacity-60' : ''">
                            <Checkbox v-model="draft.links" :value="c.to" :disabled="disabledLink(c.to)" />
                            <span>{{ t(c.labelKey) }}</span>
                        </label>
                    </div>
                </div>
            </fieldset>
        </form>

        <template #footer>
            <Button type="button" :label="t('common.cancel')" severity="secondary" variant="outlined" @click="emit('update:visible', false)" />
            <Button type="submit" :form="formId" :label="t('dashboard.settingsApply')" />
        </template>
    </Dialog>
</template>

<script setup lang="ts">
import type { CSSProperties } from 'vue';
import type { DashboardGadgetColor, DashboardGadgetConfig, DashboardGadgetItem, DashboardListLimit, DashboardRefresh, DashboardTrendDays } from '~/composables/dashboard/api/useDashboardLayoutApi.types';
import { DASHBOARD_GADGET_COLORS } from '~/composables/dashboard/api/useDashboardLayoutApi.types';
import UtilDashboardLayout from '~/utils/util-dashboard-layout';

interface Props {
    visible: boolean;
    /** 설정할 가젯 — 없으면 빈 창 */
    gadget: DashboardGadgetItem | null;
}

const props = withDefaults(defineProps<Props>(), {});

const emit = defineEmits<{
    (e: 'update:visible', value: boolean): void;
    (e: 'apply', config: DashboardGadgetConfig): void;
}>();

interface Draft {
    title: string;
    refresh: DashboardRefresh;
    limit: DashboardListLimit;
    days: DashboardTrendDays;
    text: string;
    links: string[];
    /** `AUTO` = 추천 색(저장하지 않음) */
    color: DashboardGadgetColor | 'AUTO';
}

const { t } = useI18n();
const { can } = useAdminPermission();
const formId = useId();

const spec = computed(() => (props.gadget ? UtilDashboardLayout.spec(props.gadget.type) : null));
const kindName = computed(() => (props.gadget ? t(`dashboard.kind.${props.gadget.type}`) : ''));

const draft = reactive<Draft>({ title: '', refresh: 0, limit: 5, days: 7, text: '', links: [], color: 'AUTO' });

/** 이 종류의 추천 색 */
const recommended = computed<DashboardGadgetColor>(() => (props.gadget ? UtilDashboardLayout.recommendedColor(props.gadget.type) : 'NONE'));

/** 색 칸 — 맨 앞 「추천」, 그다음 흰색과 옅은 색들(칸 바탕은 대시보드에 칠해질 색과 같은 비율로 섞는다) */
function swatch(color: DashboardGadgetColor): CSSProperties {
    const hex = UtilDashboardLayout.colorHex(color);
    return hex ? { background: `color-mix(in srgb, ${hex} 18%, var(--p-content-background))` } : { background: 'var(--p-content-background)' };
}
const colorChoices = computed(() => [
    { value: 'AUTO' as const, label: t('dashboard.colorAuto', { color: t(`dashboard.color.${recommended.value}`) }), style: swatch(recommended.value) },
    ...DASHBOARD_GADGET_COLORS.map((c) => ({ value: c, label: t(`dashboard.color.${c}`), style: swatch(c) })),
]);

// 창을 열 때 가젯의 지금 값에서 시작한다(닫고 다시 열면 고치다 만 값은 버린다)
watch(
    () => props.visible,
    (open) => {
        if (!open || !props.gadget) return;
        const c = props.gadget.config;
        draft.title = c.title ?? '';
        draft.refresh = c.refresh ?? 0;
        draft.limit = c.limit ?? 5;
        draft.days = c.days ?? 7;
        draft.text = c.text ?? '';
        draft.links = [...(c.links ?? [])];
        draft.color = c.color ?? 'AUTO';
    },
);

const refreshOptions = computed(() => [
    { value: 0, label: t('dashboard.refreshOff') },
    { value: 60, label: t('dashboard.refreshMinutes', { n: 1 }) },
    { value: 300, label: t('dashboard.refreshMinutes', { n: 5 }) },
]);
const limitOptions = computed(() => [5, 10].map((n) => ({ value: n, label: t('dashboard.limitN', { n }) })));
const daysOptions = computed(() => [7, 30].map((n) => ({ value: n, label: t('dashboard.daysN', { n }) })));

/** 바로가기 후보 — 메뉴 묶음별 */
const linkGroups = computed(() => {
    const out: { key: string; labelKey: string | null; items: ReturnType<typeof UtilDashboardLayout.shortcutChoices> }[] = [];
    for (const c of UtilDashboardLayout.shortcutChoices(can)) {
        let last = out[out.length - 1];
        if (!last || last.labelKey !== c.groupKey) {
            last = { key: `${c.groupKey ?? 'top'}-${out.length}`, labelKey: c.groupKey, items: [] };
            out.push(last);
        }
        last.items.push(c);
    }
    return out;
});

function disabledLink(to: string): boolean {
    return draft.links.length >= UtilDashboardLayout.LINKS_MAX && !draft.links.includes(to);
}

function apply(): void {
    const s = spec.value;
    if (!s) return;
    const config: DashboardGadgetConfig = {};
    if (draft.title.trim()) config.title = draft.title.trim().slice(0, UtilDashboardLayout.TITLE_MAX);
    if (draft.color !== 'AUTO') config.color = draft.color;
    if (s.refreshable && draft.refresh) config.refresh = draft.refresh;
    if (s.options.includes('limit') && draft.limit !== 5) config.limit = draft.limit;
    if (s.options.includes('days') && draft.days !== 7) config.days = draft.days;
    if (s.options.includes('text') && draft.text) config.text = draft.text.slice(0, UtilDashboardLayout.MEMO_MAX);
    if (s.options.includes('links') && draft.links.length) {
        // 메뉴 차례로 담는다
        const order = UtilDashboardLayout.shortcutChoices(can).map((c) => c.to);
        config.links = order.filter((to) => draft.links.includes(to)).slice(0, UtilDashboardLayout.LINKS_MAX);
    }
    emit('apply', config);
}
</script>
