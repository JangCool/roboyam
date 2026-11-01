<template>
    <!--
        핫딜 › 핫딜 설정(`/hotdeals/settings`) — 회원 핫딜 1차의 숫자 규칙(계약 「핫딜 설정」 표, 05 §3). 포인트 상점 설정과 같은 모양:
        구획 카드(`AppGadget` — 저장 전 바뀐 칸 수가 구역 내비에 점) + 칸(`LabeledField` + 숫자 `InputText` — `InputNumber` 는 범위 밖 값을 말없이 고친다) + 범위 도움말(0 의 뜻 「없음/끔」) + 하단 떠 있는 저장 줄(`StickySaveBar`, 화면 하나로 저장).
        **목업**(`useHotdealSettingsApi` → `hotdeal/api/mock`) — 스위치 `?mock=error|slow|forbidden|save-error`.
        - 종료 요청: 문턱 · 유예 시간(0 = 자동 종료 안 함) · 하루 요청 한도 · 같은 작성자 글 한도/기간(0 = 없음) · keep 최대(0 = 없음) · 요청자 보상 포인트(**읽기 전용** — 포인트 정책이 정본, 링크)
        - 글쓰기: 회원 하루 글 수(0 = 없음) · 같은 판매처 간격(분, 0 = 없음) · 마감 없는 딜 기간 종료(일, 0 = 끔)
        - 가격: 금액 상한(천 단위 쉼표 — 칸을 떠날 때 쉼표를 다시 붙인다) · 할인율 경고 % / 인기 표시: HOT 추천 수
        - 위장 홍보(회원): 같은 판매처 글 수(0 = 끔) · 기간 / 위장 홍보(판매처): 서로 다른 회원 수(0 = 끔) · 기간 · 다른 핫딜 비율 %
        - 상품 이미지: 링크 OG 가져오기 켬/끔(`ogFetchEnabled`). OG 보안 값(시간 제한·크기·동시 수)은 여기 두지 않는다(서버 규칙 1).
        - 권한: 조회·저장 `hotdeal.manage`(없으면 칸 `readonly` + 읽기 전용 안내). 저장하지 않고 나가면 묻는다.
    -->
    <div class="flex flex-col gap-4">
        <PageTitle :title="t('hotdeal.settings.title')" :subtitle="t('hotdeal.settings.subtitle')" />

        <AppGadget v-if="loading && !saved" :title="t('hotdeal.settings.endSection')">
            <div class="flex flex-col gap-3" role="status" :aria-label="t('common.loading')">
                <Skeleton v-for="n in 5" :key="n" height="3rem" />
            </div>
        </AppGadget>

        <AppGadget v-else-if="!saved" :title="t('hotdeal.settings.endSection')">
            <ErrorState :code="loadError?.status === 403 ? '403' : '!'" :title="t('hotdeal.settings.loadFailed')" :description="loadError?.message" :retry="loadError?.status !== 403" :retrying="loading" @retry="load" />
        </AppGadget>

        <form v-else class="flex flex-col gap-4" novalidate @submit.prevent="save">
            <AppGadget
                v-for="section in SECTIONS"
                :key="section.key"
                :title="t(`hotdeal.settings.${section.key}Section`)"
                :anchor="`hotdeal-${section.key}`"
                :changes="changesOf(section.fields) + (section.key === 'image' && ogChanged ? 1 : 0)"
                :invalid="invalidOf(section.fields)"
            >
                <div class="flex flex-col gap-4">
                    <p class="m-0 text-xs leading-relaxed text-muted-color">{{ t(`hotdeal.settings.${section.key}Lead`) }}</p>
                    <div class="grid gap-4 sm:grid-cols-2">
                        <LabeledField v-for="field in section.fields" :key="field" :label="t(`hotdeal.settings.field.${field}`)" :hint="UtilHotdealMessages.settingRange(t, spec(field))" :error="errorOf(field)" :help="t(`hotdeal.settings.help.${field}`)">
                            <template #default="{ id, describedBy, invalid }">
                                <div class="flex flex-wrap items-center gap-2">
                                    <InputText
                                        :id="id"
                                        :model-value="texts[field]"
                                        :name="field"
                                        inputmode="numeric"
                                        autocomplete="off"
                                        :readonly="!canManage || saving"
                                        :invalid="invalid"
                                        :aria-describedby="describedBy"
                                        class="text-right tabular-nums"
                                        :class="spec(field).money ? 'w-40' : 'w-24'"
                                        @update:model-value="(v) => onInput(field, v)"
                                        @blur="onBlur(field)"
                                    />
                                    <span class="text-sm text-muted-color">{{ t(`hotdeal.settings.unit.${field}`) }}</span>
                                    <Tag v-if="spec(field).zero && UtilHotdeal.parseInt(texts[field]) === 0" :value="t(`hotdeal.settings.zeroTag.${spec(field).zero}`)" severity="secondary" class="text-[11px]!" />
                                    <span v-if="UtilHotdeal.parseInt(texts[field]) !== saved[field]" class="text-xs text-amber-700 dark:text-amber-400">{{ t('hotdeal.settings.savedValue', { value: UtilHotdeal.formatSetting(field, saved[field]) }) }}</span>
                                </div>
                            </template>
                        </LabeledField>

                        <!-- 보상 포인트 — 포인트 정책이 정본이라 여기서는 읽기만 -->
                        <div v-if="section.key === 'end'" class="flex flex-col gap-1.5">
                            <span class="text-sm font-medium">{{ t('hotdeal.settings.rewardLabel') }}</span>
                            <p class="m-0 text-sm tabular-nums">{{ t('hotdeal.settings.rewardValue', { n: saved.rewardPoints, daily: saved.rewardDailyLimit }) }}</p>
                            <p class="m-0 text-xs leading-relaxed text-muted-color">
                                {{ t('hotdeal.settings.rewardHelp') }}
                                <NuxtLink v-if="can('point.read')" to="/points/policy" class="text-primary hover:underline">{{ t('hotdeal.settings.rewardLink') }}</NuxtLink>
                            </p>
                        </div>

                        <!-- 상품 이미지 — 켬/끔 -->
                        <div v-if="section.key === 'image'" class="flex flex-col gap-1.5 sm:col-span-2">
                            <div class="flex items-center gap-3">
                                <ToggleSwitch v-model="ogFetchEnabled" :input-id="`${uid}-og`" :disabled="!canManage || saving" :aria-describedby="`${uid}-og-help`" />
                                <label :for="`${uid}-og`" class="text-sm font-medium">{{ t('hotdeal.settings.field.ogFetchEnabled') }}</label>
                                <span v-if="ogChanged" class="text-xs text-amber-700 dark:text-amber-400">{{ t('hotdeal.settings.savedValue', { value: t(saved.ogFetchEnabled ? 'hotdeal.settings.on' : 'hotdeal.settings.off') }) }}</span>
                            </div>
                            <p :id="`${uid}-og-help`" class="m-0 text-xs leading-relaxed text-muted-color">{{ t(ogFetchEnabled ? 'hotdeal.settings.help.ogFetchEnabledOn' : 'hotdeal.settings.help.ogFetchEnabledOff') }}</p>
                        </div>
                    </div>
                </div>
            </AppGadget>

            <p class="m-0 text-xs text-muted-color">
                {{ saved.updatedAt ? t('hotdeal.settings.lastSaved', { at: time.full(saved.updatedAt), by: saved.updatedBy ?? '—' }) : t('hotdeal.settings.neverSaved') }}
            </p>

            <StickySaveBar v-if="canManage" submit revertable :dirty="changed > 0" :saving="saving" :disabled="!canSave" :count="changed" :error-count="invalidOf(FIELDS)" :error="formError" @revert="revert" />
            <p v-else class="flex items-center gap-1.5 text-xs text-muted-color"><i class="pi pi-lock" aria-hidden="true" />{{ t('hotdeal.settings.readOnly') }}</p>
        </form>
    </div>
</template>

<script setup lang="ts">
import type { ApiError } from '~/composables/common/useApiClient';
import type { HotdealSettingsField, HotdealSettingsView } from '~/composables/hotdeal/api/useHotdealSettingsApi.types';
import UtilHotdeal from '~/utils/util-hotdeal';
import UtilHotdealMessages from '~/utils/util-hotdeal-messages';

const { t } = useI18n();
useSeoMeta({ title: () => t('hotdeal.settings.title') });

const api = useHotdealSettingsApi();
const toast = useAppToast();
const toApiError = useApiErrorOf();
const time = useDisplayTime();
const { confirm } = useConfirmDialog();
const { can } = useAdminPermission();
const perm = useHotdealPermission();
const canManage = perm.canManage;
const uid = useId();

type SectionKey = 'end' | 'post' | 'price' | 'hot' | 'suspicion' | 'storeSuspicion' | 'image';

const SECTIONS: readonly { key: SectionKey; fields: readonly HotdealSettingsField[] }[] = [
    { key: 'end', fields: ['endRequestThreshold', 'endGraceHours', 'endRequestDailyLimit', 'endRequestPerAuthorLimit', 'endRequestPerAuthorDays', 'keepMaxCount'] },
    { key: 'post', fields: ['memberDailyPostLimit', 'sameStoreIntervalMinutes', 'autoExpireDays'] },
    { key: 'price', fields: ['priceMaxWon', 'discountWarnPercent'] },
    { key: 'hot', fields: ['hotLikeThreshold'] },
    { key: 'suspicion', fields: ['suspicionMinPosts', 'suspicionWindowDays'] },
    { key: 'storeSuspicion', fields: ['storeSuspicionMembers', 'storeSuspicionWindowDays', 'storeSuspicionOtherRatioPercent'] },
    { key: 'image', fields: [] },
];
const FIELDS: readonly HotdealSettingsField[] = UtilHotdeal.SETTINGS.map((s) => s.field);

function spec(field: HotdealSettingsField) {
    return UtilHotdeal.settingSpec(field);
}

const saved = ref<HotdealSettingsView | null>(null);
const texts = reactive(Object.fromEntries(FIELDS.map((f) => [f, ''])) as Record<HotdealSettingsField, string>);
const ogFetchEnabled = ref(true);
const serverErrors = ref<Partial<Record<HotdealSettingsField, string>>>({});
const loading = ref(false);
const loadError = ref<ApiError | null>(null);
const saving = ref(false);
const formError = ref('');

function problemOf(field: HotdealSettingsField) {
    return UtilHotdeal.settingProblem(field, texts[field]);
}
function errorOf(field: HotdealSettingsField): string {
    const p = problemOf(field);
    return p ? UtilHotdealMessages.settingProblem(t, p, spec(field)) : (serverErrors.value[field] ?? '');
}
function changesOf(fields: readonly HotdealSettingsField[]): number {
    const s = saved.value;
    return s ? fields.filter((f) => UtilHotdeal.parseInt(texts[f]) !== s[f]).length : 0;
}
function invalidOf(fields: readonly HotdealSettingsField[]): number {
    return fields.filter((f) => errorOf(f)).length;
}

const ogChanged = computed(() => saved.value !== null && ogFetchEnabled.value !== saved.value.ogFetchEnabled);
const changed = computed(() => (saved.value ? UtilHotdeal.changedCount(texts, saved.value) + (ogChanged.value ? 1 : 0) : 0));
const canSave = computed(() => canManage.value && changed.value > 0 && FIELDS.every((f) => !problemOf(f)));

function onInput(field: HotdealSettingsField, value: string | undefined): void {
    texts[field] = value ?? '';
    const next = { ...serverErrors.value };
    delete next[field];
    serverErrors.value = next;
}

/** 금액 칸은 떠날 때 천 단위 쉼표를 다시 붙인다(읽을 수 없는 글자는 그대로 둔다) */
function onBlur(field: HotdealSettingsField): void {
    if (!spec(field).money) return;
    const n = UtilHotdeal.parseInt(texts[field]);
    if (n !== null) texts[field] = UtilHotdeal.formatSetting(field, n);
}

function applySaved(view: HotdealSettingsView): void {
    saved.value = view;
    for (const f of FIELDS) texts[f] = UtilHotdeal.formatSetting(f, view[f]);
    ogFetchEnabled.value = view.ogFetchEnabled;
    serverErrors.value = {};
    formError.value = '';
}

function revert(): void {
    if (saved.value) applySaved(saved.value);
}

async function load(): Promise<void> {
    loading.value = true;
    loadError.value = null;
    try {
        applySaved(await api.get());
    } catch (e) {
        loadError.value = toApiError(e);
    } finally {
        loading.value = false;
    }
}
onMounted(() => void load());

async function save(): Promise<void> {
    if (saving.value || !canSave.value) return;
    const values = UtilHotdeal.settingValues(texts);
    if (!values) return;
    saving.value = true;
    formError.value = '';
    serverErrors.value = {};
    try {
        applySaved(await api.save({ ...values, ogFetchEnabled: ogFetchEnabled.value }));
        toast.success(t('hotdeal.settings.saved'));
    } catch (e) {
        const failure = toApiError(e);
        const fields = failure.code === 'VALIDATION' ? (failure.fields ?? {}) : {};
        const mine = Object.fromEntries(Object.entries(fields).filter(([k]) => (FIELDS as readonly string[]).includes(k))) as Partial<Record<HotdealSettingsField, string>>;
        if (Object.keys(mine).length) {
            serverErrors.value = mine;
            formError.value = t('hotdeal.settings.saveFieldsRejected');
        } else formError.value = `${t('hotdeal.settings.saveFailed')} — ${fields.ogFetchEnabled ?? failure.message}`;
    } finally {
        saving.value = false;
    }
}

onBeforeRouteLeave(async () => {
    if (changed.value === 0) return true;
    return confirm({ title: t('hotdeal.settings.leaveTitle'), message: t('hotdeal.settings.leaveMessage'), confirmLabel: t('hotdeal.settings.leaveConfirm'), cancelLabel: t('common.cancel'), tone: 'danger' });
});
</script>
