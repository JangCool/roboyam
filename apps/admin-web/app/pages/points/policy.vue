<template>
    <!--
        포인트 정책 — 어떤 활동에 몇 포인트를, 하루 몇 번까지 줄지 정한다. community 관리자 앱 `settings/points.vue`(`point_settings` 13칸)를 **빠짐없이** 옮기고 가입 포인트(`signupPoints`, 그누보드의 회원가입 포인트)를 더했다.
        community 와 다른 점: 최고 관리자 전용 → roboyam 권한(조회 `point.read`, 변경 `point.settings`). 지급 규칙 표 한 장 대신 업무별 구획(출석 · 글·댓글 · 인기글 보상 · 추천 보너스 · 핫딜 종료 요청 보상 · 가입 포인트 · 상점 · 운영 원칙)으로 나눠 우측 구역 내비(`SectionNav`)로 이동한다.
        community 의 「새로고침」 단추·`defaults`(기본값으로 되돌리기)는 두지 않았다. 서버가 값을 저장만 하고 아직 지급하는 지점이 없는 구획(출석 · 인기글 보상 · 핫딜 종료 요청 · 가입 포인트 · 상점의 교환권 가격 — `point-contract.md` 「적용 지점 없음」)에는
        「아직 적용되지 않아요」 딱지와 한 줄 안내를 단다(값은 저장된다). 실제로 지급되는 것은 글·댓글·추천 보너스(+ 본인 삭제 회수)다. 인기글 보상이 등급 조건이 될 수 있다는 안내는 문구만이다(등급 조건에 인기글 칸이 아직 없다).
        값이 0이면 그 항목이 꺼진다(전역 스위치 없음) — 꺼진 묶음에는 「꺼짐」 딱지가 붙는다(입력 중인 값 기준). 운영 원칙 구획은 community 확정 규칙의 읽기 전용 안내다.
        **저장은 한 번에**(14칸 모두, PUT 한 번) — [저장]·[되돌리기]·상태 글·저장 실패 문구는 `<form>` 맨 끝의 떠 있는 줄(`StickySaveBar`)에 둔다(`forms.md` 2-4). 칸은 `InputText` + `inputmode=numeric`(등급 설정과 같은 이유 — `InputNumber` 는 범위 밖 값을 말없이 고친다).
        범위 검사는 `UtilPoint`(서버와 같은 범위) — 하나라도 틀리면 저장 단추가 꺼진다. 서버가 거절한 칸(`400 VALIDATION` `fields.<칸>`)은 그 칸 아래에 서버 문구 그대로, 칸에 붙지 않는 것은 저장 줄 안에 보인다.
        권한 `point.settings` 가 없으면 저장 줄 대신 읽기 전용 안내가 보이고 칸은 `readonly`(서버 값이 그대로 보인다). 저장하지 않은 변경이 있는 채 떠나면 확인을 묻는다(`onBeforeRouteLeave`).
    -->
    <div class="flex flex-col gap-4">
        <PageTitle :title="t('point.policyTitle')" :subtitle="t('point.policySubtitle')" />

        <!-- 읽는 중 -->
        <AppGadget v-if="loading && !saved" :title="t('point.section.attendance')">
            <div class="flex flex-col gap-3" role="status" :aria-label="t('common.loading')">
                <Skeleton v-for="n in 4" :key="n" height="3.5rem" />
            </div>
        </AppGadget>

        <!-- 읽기 실패 — 서버 문구를 그대로 보이고 다시 시도 -->
        <AppGadget v-else-if="!saved" :title="t('point.policyTitle')">
            <ErrorState :title="t('point.policyLoadFailed')" :description="loadError?.message" retry :retrying="loading" @retry="load" />
        </AppGadget>

        <form v-else class="flex flex-col gap-4" novalidate @submit.prevent="save">
            <p class="text-xs text-muted-color">
                {{ saved.updatedAt ? t('point.lastUpdated', { at: time.full(saved.updatedAt), by: saved.updatedBy ?? '—' }) : t('point.neverUpdated') }}
            </p>

            <AppGadget v-for="section in SECTIONS" :key="section.anchor" :title="t(section.titleKey)" :anchor="section.anchor" :changes="sectionChanges(section)" :invalid="sectionInvalid(section)">
                <template #title-extra>
                    <Tag v-if="section.notReady" :value="t('point.notReadyTag')" icon="pi pi-clock" severity="secondary" />
                    <Tag v-if="section.groups.length === 1 && section.groups[0] && UtilPoint.groupOff(section.groups[0].group, form)" :value="t('point.offTag')" icon="pi pi-power-off" severity="warn" />
                </template>

                <p class="mb-4 text-xs leading-relaxed text-muted-color">{{ t(section.leadKey) }}</p>

                <div class="flex flex-col gap-5">
                    <div v-for="group in section.groups" :key="group.group" role="group" :aria-label="group.labelKey ? t(group.labelKey) : t(section.titleKey)" class="flex flex-col gap-3">
                        <div v-if="group.labelKey" class="flex items-center gap-2">
                            <h3 class="text-sm font-medium">{{ t(group.labelKey) }}</h3>
                            <Tag v-if="UtilPoint.groupOff(group.group, form)" :value="t('point.offTag')" icon="pi pi-power-off" severity="warn" />
                        </div>

                        <div class="grid gap-x-4 gap-y-3 sm:grid-cols-[repeat(3,minmax(0,15rem))]">
                            <LabeledField v-for="f in group.fields" :key="f.field" :label="t(`point.field.${f.field}`)" :hint="UtilPointMessages.range(t, f.field, locale)" :help="t(`point.help.${f.field}`)" :error="errorOf(f.field)">
                                <template #default="{ id, describedBy, invalid }">
                                    <div class="flex items-center gap-2">
                                        <InputText
                                            :id="id"
                                            :model-value="form[f.field]"
                                            :name="f.field"
                                            inputmode="numeric"
                                            autocomplete="off"
                                            :readonly="!canEdit || saving"
                                            :invalid="invalid"
                                            :aria-describedby="describedBy"
                                            :aria-required="true"
                                            class="w-32 text-right tabular-nums"
                                            @update:model-value="(value: string | undefined) => onInput(f.field, value ?? '')"
                                        />
                                        <span class="text-sm text-muted-color">{{ t(`point.unit.${f.unit}`) }}</span>
                                    </div>
                                </template>
                            </LabeledField>
                        </div>
                    </div>
                </div>

                <ul v-if="section.notes.length" class="m-0 mt-4 flex list-none flex-col gap-1.5 p-0 text-xs leading-relaxed text-muted-color">
                    <li v-for="key in section.notes" :key="key" class="flex items-start gap-2">
                        <i class="pi pi-info-circle mt-0.5 text-xs" aria-hidden="true" />
                        <span>{{ t(key) }}</span>
                    </li>
                </ul>
            </AppGadget>

            <!-- 운영 원칙 — 읽기 전용 안내(community 확정 규칙) -->
            <AppGadget :title="t('point.section.rules')" anchor="point-rules">
                <p class="mb-3 text-xs leading-relaxed text-muted-color">{{ t('point.lead.rules') }}</p>
                <ul class="m-0 flex list-none flex-col gap-2 p-0 text-sm">
                    <li v-for="key in RULES" :key="key" class="flex items-start gap-2">
                        <i class="pi pi-check-circle mt-1 text-xs text-muted-color" aria-hidden="true" />
                        <span>{{ t(key) }}</span>
                    </li>
                </ul>
            </AppGadget>

            <!-- 저장 줄 — 구획 밖, 모든 구획 아래. 14칸을 한 번에 저장한다. 저장 실패(칸에 붙지 않는 것)는 이 줄 안에 선다 -->
            <StickySaveBar v-if="canEdit" submit revertable :dirty="dirty" :saving="saving" :disabled="!canSave" :count="changed.length" :error-count="errorCount" :error="formError" @revert="revert" />
            <p v-else class="text-xs text-muted-color">{{ t('point.readOnlyNote') }}</p>
        </form>
    </div>
</template>

<script setup lang="ts">
import type { ApiError } from '~/composables/common/useApiClient';
import type { PointSettingsField, PointSettingsResponse } from '~/composables/point/api/usePointApi.types';
import UtilPoint, { type PointSettingsForm, type PointSettingsGroup } from '~/utils/util-point';
import UtilPointMessages from '~/utils/util-point-messages';

/** 칸 하나 — 단위 글자 키(`point.unit.*`) */
interface PolicyField {
    field: PointSettingsField;
    unit: 'point' | 'times' | 'rank' | 'likes';
}
/** 같은 「꺼짐」 판정을 받는 칸 묶음. 이름(`labelKey`)은 구획에 묶음이 둘 이상일 때만 */
interface PolicyGroup {
    group: PointSettingsGroup;
    labelKey?: string;
    fields: PolicyField[];
}
/** 구획 = 우측 구역 내비 한 항목 */
interface PolicySection {
    anchor: string;
    titleKey: string;
    leadKey: string;
    groups: PolicyGroup[];
    notes: string[];
    /** 서버가 저장만 하고 아직 적용(지급)하는 지점이 없는 구획 — 「아직 적용되지 않아요」 딱지 */
    notReady?: boolean;
}

const SECTIONS: PolicySection[] = [
    {
        anchor: 'point-attendance',
        titleKey: 'point.section.attendance',
        leadKey: 'point.lead.attendance',
        groups: [{ group: 'attendance', fields: [{ field: 'attendancePoints', unit: 'point' }] }],
        notes: ['point.note.attendanceOnce', 'point.note.attendanceNotApplied'],
        notReady: true,
    },
    {
        anchor: 'point-activity',
        titleKey: 'point.section.activity',
        leadKey: 'point.lead.activity',
        groups: [
            { group: 'post', labelKey: 'point.group.post', fields: [{ field: 'postPoints', unit: 'point' }, { field: 'postDailyCap', unit: 'times' }] },
            { group: 'comment', labelKey: 'point.group.comment', fields: [{ field: 'commentPoints', unit: 'point' }, { field: 'commentDailyCap', unit: 'times' }] },
        ],
        notes: [],
    },
    {
        anchor: 'point-popular',
        titleKey: 'point.section.popular',
        leadKey: 'point.lead.popular',
        groups: [{ group: 'popular', fields: [{ field: 'popularRewardPoints', unit: 'point' }, { field: 'popularRewardTopN', unit: 'rank' }] }],
        notes: ['point.note.popularGrade', 'point.note.popularNotApplied'],
        notReady: true,
    },
    {
        anchor: 'point-like-bonus',
        titleKey: 'point.section.likeBonus',
        leadKey: 'point.lead.likeBonus',
        groups: [{ group: 'likeBonus', fields: [{ field: 'likeBonusThreshold', unit: 'likes' }, { field: 'likeBonusPoints', unit: 'point' }, { field: 'likeBonusDailyLimit', unit: 'times' }] }],
        notes: ['point.note.likeBonus'],
    },
    {
        anchor: 'point-deal-end',
        titleKey: 'point.section.dealEnd',
        leadKey: 'point.lead.dealEnd',
        groups: [{ group: 'dealEnd', fields: [{ field: 'dealEndRequestPoints', unit: 'point' }, { field: 'dealEndRequestDailyLimit', unit: 'times' }] }],
        notes: ['point.note.dealEnd'],
        notReady: true,
    },
    {
        anchor: 'point-signup',
        titleKey: 'point.section.signup',
        leadKey: 'point.lead.signup',
        groups: [{ group: 'signup', fields: [{ field: 'signupPoints', unit: 'point' }] }],
        notes: ['point.note.signup', 'point.note.signupNotApplied'],
        notReady: true,
    },
    {
        anchor: 'point-shop',
        titleKey: 'point.section.shop',
        leadKey: 'point.lead.shop',
        groups: [{ group: 'shop', fields: [{ field: 'nicknameTicketPrice', unit: 'point' }] }],
        notes: ['point.note.shopNotApplied'],
        notReady: true,
    },
];

/** 운영 원칙(읽기 전용) — 0이면 꺼짐 · 하루 한도 · 0P 바닥 · 정지·탈퇴 · 삭제 회수 · 게시판별 켜고 끄기 · 15초 반영·소급 없음 · 등급과 무관 · 유효기간 없음 · 비회원 글·댓글 없음 */
const RULES = ['point.rule.zeroOff', 'point.rule.dailyCount', 'point.rule.floorZero', 'point.rule.restricted', 'point.rule.revoke', 'point.rule.boardToggle', 'point.rule.apply', 'point.rule.noGrade', 'point.rule.noExpiry', 'point.rule.noGuest'] as const;

const { t, locale } = useI18n();
useSeoMeta({ title: () => t('point.policyTitle') });

const api = usePointApi();
const toast = useAppToast();
const toApiError = useApiErrorOf();
const time = useDisplayTime();
const { confirm } = useConfirmDialog();
const { can } = useAdminPermission();

/** 정책을 바꿀 수 있나(정의: util-permission-catalog.ts) — 숨기는 것은 편의이고 막는 것은 서버다 */
const canEdit = computed(() => can('point.settings'));

/** 서버가 준 값(저장된 값). `null` = 아직 못 받았다 */
const saved = ref<PointSettingsResponse | null>(null);
const form = ref<PointSettingsForm>(UtilPoint.formOf(UtilPoint.DEFAULTS));
const loading = ref(false);
const loadError = ref<ApiError | null>(null);
const saving = ref(false);
/** 칸에 붙지 않는 저장 오류 */
const formError = ref('');
/** 서버가 거절한 칸별 문구 — 그 칸을 고치면 지운다 */
const serverErrors = ref<Partial<Record<PointSettingsField, string>>>({});

const problems = computed(() => UtilPoint.validateSettings(form.value));
const changed = computed(() => (saved.value ? UtilPoint.changedFields(form.value, saved.value) : []));
const dirty = computed(() => changed.value.length > 0);
/** 맞지 않는 칸 수 — 화면이 미리 검사한 것 + 서버가 거절한 것(같은 칸은 한 번) */
const errorCount = computed(() => UtilPoint.FIELDS.filter((field) => problems.value[field] || serverErrors.value[field]).length);
/** 서버가 거절한 칸은 저장을 막지 않는다 — 고치지 않고 다시 보내 볼 수 있어야 하고, 서버가 다시 판정한다 */
const canSave = computed(() => canEdit.value && dirty.value && Object.keys(problems.value).length === 0);

function fieldsOf(section: PolicySection): PointSettingsField[] {
    return section.groups.flatMap((group) => group.fields.map((f) => f.field));
}
function sectionChanges(section: PolicySection): number {
    return fieldsOf(section).filter((field) => changed.value.includes(field)).length;
}
function sectionInvalid(section: PolicySection): number {
    return fieldsOf(section).filter((field) => problems.value[field] || serverErrors.value[field]).length;
}

/** 칸 아래 오류 — 화면이 미리 검사한 문제가 먼저, 없으면 서버가 거절한 문구 */
function errorOf(field: PointSettingsField): string {
    const problem = problems.value[field];
    if (problem) return UtilPointMessages.settingsProblem(t, problem, field, locale.value);
    return serverErrors.value[field] ?? '';
}

function onInput(field: PointSettingsField, value: string) {
    form.value = { ...form.value, [field]: value };
    if (!serverErrors.value[field]) return;
    const { [field]: _removed, ...rest } = serverErrors.value;
    serverErrors.value = rest;
}

onMounted(load);

async function load() {
    loading.value = true;
    loadError.value = null;
    try {
        applySaved(await api.loadSettings());
    } catch (e) {
        loadError.value = toApiError(e);
    } finally {
        loading.value = false;
    }
}

function applySaved(res: PointSettingsResponse) {
    saved.value = res;
    form.value = UtilPoint.formOf(res);
    serverErrors.value = {};
    formError.value = '';
}

function revert() {
    if (!saved.value) return;
    form.value = UtilPoint.formOf(saved.value);
    serverErrors.value = {};
    formError.value = '';
}

async function save() {
    if (saving.value || !canSave.value) return;
    const body = UtilPoint.toRequest(form.value);
    if (!body) return;
    formError.value = '';
    serverErrors.value = {};
    saving.value = true;
    try {
        applySaved(await api.saveSettings(body));
        toast.success(t('point.saved'));
    } catch (e) {
        const rejected = UtilPoint.settingsFieldErrorsOf(e);
        if (rejected) {
            serverErrors.value = rejected.fields;
            formError.value = [Object.keys(rejected.fields).length ? t('point.saveFieldsRejected') : '', rejected.rest].filter(Boolean).join(' ');
        } else {
            formError.value = `${t('point.saveFailed')} — ${toApiError(e).message}`;
        }
    } finally {
        saving.value = false;
    }
}

// 저장하지 않은 변경이 있는 채 다른 화면으로 가려 하면 묻는다
onBeforeRouteLeave(async () => {
    if (!dirty.value) return true;
    return confirm({ title: t('point.leaveTitle'), message: t('point.leaveMessage'), confirmLabel: t('point.leaveConfirm'), cancelLabel: t('point.leaveCancel'), tone: 'danger' });
});
</script>
