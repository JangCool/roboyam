<template>
    <!--
        핫딜 › 핫딜 설정(`/hotdeals/settings`) — 회원 핫딜 1차의 숫자 규칙(03-hotdeal 1-1 「설정」, 계약 「목업 가정」). 포인트 상점 설정과 같은 모양:
        구획 카드(`AppGadget` — 저장 전 바뀐 칸 수가 구역 내비에 점) + 칸(`LabeledField` + 숫자 `InputText` — `InputNumber` 는 범위 밖 값을 말없이 고친다) + 범위 도움말 + 하단 떠 있는 저장 줄(`StickySaveBar`, 화면 하나로 저장).
        **목업**(`useHotdealSettingsApi` → `hotdeal/api/mock`) — 스위치 `?mock=error|slow|forbidden|save-error`.
        - 종료 요청: 문턱(1~20, 기본 3) · 유예 시간(0~168, 기본 12 — 0 = 자동 종료 안 함) · 하루 요청 한도(1~100, 기본 20) · 요청자 보상 포인트(**읽기 전용** — 포인트 정책의 「핫딜 종료 요청 보상」이 정본, 링크)
        - 위장 홍보 후보: 같은 판매처 글 수(0~50, 기본 3 — 0 = 끔) · 세는 기간(1~90일, 기본 7)
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
            <AppGadget v-for="section in SECTIONS" :key="section.key" :title="t(`hotdeal.settings.${section.key}Section`)" :anchor="`hotdeal-${section.key}`" :changes="changesOf(section.fields)" :invalid="invalidOf(section.fields)">
                <div class="flex flex-col gap-4">
                    <p class="m-0 text-xs leading-relaxed text-muted-color">{{ t(`hotdeal.settings.${section.key}Lead`) }}</p>
                    <div class="grid gap-4 sm:grid-cols-2">
                        <LabeledField
                            v-for="field in section.fields"
                            :key="field"
                            :label="t(`hotdeal.settings.field.${field}`)"
                            :hint="t('hotdeal.settings.range', { min: spec(field).min, max: spec(field).max })"
                            :error="errorOf(field)"
                            :help="t(`hotdeal.settings.help.${field}`)"
                        >
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
                                        class="w-24 text-right tabular-nums"
                                        @update:model-value="(v) => onInput(field, v)"
                                    />
                                    <span class="text-sm text-muted-color">{{ t(`hotdeal.settings.unit.${field}`) }}</span>
                                    <span v-if="UtilHotdeal.parseInt(texts[field]) !== saved[field]" class="text-xs text-amber-700 dark:text-amber-400">{{ t('hotdeal.settings.savedValue', { value: saved[field] }) }}</span>
                                </div>
                            </template>
                        </LabeledField>

                        <!-- 보상 포인트 — 포인트 정책이 정본이라 여기서는 읽기만 -->
                        <div v-if="section.key === 'end'" class="flex flex-col gap-1.5">
                            <span class="text-sm font-medium">{{ t('hotdeal.settings.rewardLabel') }}</span>
                            <p class="m-0 text-sm tabular-nums">{{ t('hotdeal.settings.rewardValue', { n: saved.rewardPoints }) }}</p>
                            <p class="m-0 text-xs leading-relaxed text-muted-color">
                                {{ t('hotdeal.settings.rewardHelp') }}
                                <NuxtLink v-if="can('point.read')" to="/points/policy" class="text-primary hover:underline">{{ t('hotdeal.settings.rewardLink') }}</NuxtLink>
                            </p>
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

const SECTIONS: readonly { key: 'end' | 'suspicion'; fields: readonly HotdealSettingsField[] }[] = [
    { key: 'end', fields: ['endRequestThreshold', 'endGraceHours', 'endRequestDailyLimit'] },
    { key: 'suspicion', fields: ['suspicionMinPosts', 'suspicionWindowDays'] },
];
const FIELDS: readonly HotdealSettingsField[] = UtilHotdeal.SETTINGS.map((s) => s.field);

function spec(field: HotdealSettingsField) {
    return UtilHotdeal.settingSpec(field);
}

const saved = ref<HotdealSettingsView | null>(null);
const texts = reactive<Record<HotdealSettingsField, string>>({ endRequestThreshold: '', endGraceHours: '', endRequestDailyLimit: '', suspicionMinPosts: '', suspicionWindowDays: '' });
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

const changed = computed(() => (saved.value ? UtilHotdeal.changedCount(texts, saved.value) : 0));
const canSave = computed(() => canManage.value && changed.value > 0 && FIELDS.every((f) => !problemOf(f)));

function onInput(field: HotdealSettingsField, value: string | undefined): void {
    texts[field] = value ?? '';
    const next = { ...serverErrors.value };
    delete next[field];
    serverErrors.value = next;
}

function applySaved(view: HotdealSettingsView): void {
    saved.value = view;
    for (const f of FIELDS) texts[f] = String(view[f]);
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
        applySaved(await api.save(values));
        toast.success(t('hotdeal.settings.saved'));
    } catch (e) {
        const failure = toApiError(e);
        const fields = failure.code === 'VALIDATION' ? (failure.fields ?? {}) : {};
        const mine = Object.fromEntries(Object.entries(fields).filter(([k]) => (FIELDS as readonly string[]).includes(k))) as Partial<Record<HotdealSettingsField, string>>;
        if (Object.keys(mine).length) {
            serverErrors.value = mine;
            formError.value = t('hotdeal.settings.saveFieldsRejected');
        } else formError.value = `${t('hotdeal.settings.saveFailed')} — ${failure.message}`;
    } finally {
        saving.value = false;
    }
}

onBeforeRouteLeave(async () => {
    if (changed.value === 0) return true;
    return confirm({ title: t('hotdeal.settings.leaveTitle'), message: t('hotdeal.settings.leaveMessage'), confirmLabel: t('hotdeal.settings.leaveConfirm'), cancelLabel: t('common.cancel'), tone: 'danger' });
});
</script>
