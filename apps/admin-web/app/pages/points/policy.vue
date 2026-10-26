<template>
    <!--
        포인트 정책 — 어떤 활동에 몇 포인트를, 하루 몇 번까지 줄지 정한다. community 관리자 앱 `settings/points.vue`(`point_settings` 13칸)를 **빠짐없이** 옮기고 가입 포인트(`signupPoints`, 그누보드의 회원가입 포인트)를 더했다.
        community 와 다른 점: 최고 관리자 전용 → roboyam 권한(조회 `point.read`, 변경 `point.settings`). 지급 규칙 표 한 장 대신 업무별 구획(출석 · 글·댓글 · 인기글 보상 · 추천 보너스 · 핫딜 종료 요청 보상 · 가입 포인트 · 운영 원칙)으로 나눠 우측 구역 내비(`SectionNav`)로 이동한다.
        「상점」 구획(닉네임 변경권 가격)은 없앴다 — 변경권 값의 출처는 포인트 상점 품목 가격 하나다(commerce-stage1 결정 2). 그 자리(폼 맨 위)에 「포인트 상점 › 품목 관리에서 정해요」 안내 한 줄 + 링크.
        community 의 「새로고침」 단추·`defaults`(기본값으로 되돌리기)는 두지 않았다. 서버가 값을 저장만 하고 아직 지급하는 지점이 없는 구획(인기글 보상 · 핫딜 종료 요청 — `point-contract.md` 「적용 지점 없음」)에는
        「아직 적용되지 않아요」 딱지와 한 줄 안내를 단다(값은 저장된다). 실제로 지급되는 것은 출석(+ 연속 출석 보너스)·글·댓글·추천 보너스(+ 본인 삭제 회수)·가입 포인트다. 인기글 보상이 등급 조건이 될 수 있다는 안내는 문구만이다(등급 조건에 인기글 칸이 아직 없다).
        값이 0이면 그 항목이 꺼진다(전역 스위치 없음) — 꺼진 묶음에는 「꺼짐」 딱지가 붙는다(입력 중인 값 기준). 운영 원칙 구획은 community 확정 규칙의 읽기 전용 안내다.
        **저장은 한 번에**(13칸 모두, PUT 한 번 — 변경권 가격 칸은 V86 이 지웠다) — [저장]·[되돌리기]·상태 글·저장 실패 문구는 `<form>` 맨 끝의 떠 있는 줄(`StickySaveBar`)에 둔다(`forms.md` 2-4). 칸은 `InputText` + `inputmode=numeric`(등급 설정과 같은 이유 — `InputNumber` 는 범위 밖 값을 말없이 고친다).
        범위 검사는 `UtilPoint`(서버와 같은 범위) — 하나라도 틀리면 저장 단추가 꺼진다. 서버가 거절한 칸(`400 VALIDATION` `fields.<칸>`)은 그 칸 아래에 서버 문구 그대로, 칸에 붙지 않는 것은 저장 줄 안에 보인다.
        권한 `point.settings` 가 없으면 저장 줄 대신 읽기 전용 안내가 보이고 칸은 `readonly`(스위치는 disabled — 서버 값이 그대로 보인다). 저장하지 않은 변경이 있는 채 떠나면 확인을 묻는다(`onBeforeRouteLeave`).
        커머스 1-3 「적립 규칙 보강」(`docs/domain/commerce.md` 6-2): 글·댓글 구획에 「적립 조건」 묶음(최소 글자 수·작성 간격 + 스위치 둘 — 칸마다 0 = 그 조건 없음), 새 구획 「하루 총 한도」(0 = **한도 없음** 딱지),
        출석 구획에 「연속 출석 보너스」(출석체크가 생겨 출석 구획의 「아직 적용되지 않아요」 딱지를 뗐다), 가입 포인트는 본인인증 기준(딱지 뗌), 새 구획 「운영 조정」(조정 문턱 — 1-1 의 읽기 전용 칸을 저장 가능하게, 딱지 없음). 서버 연결됨.
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

            <!-- 옛 「상점」 구획 자리 — 닉네임 변경권 가격은 포인트 상점 품목에서 정한다(링크는 그 화면을 열 수 있을 때만) -->
            <Message severity="info" :closable="false" size="small">
                <span>{{ t('point.ticketPriceMoved') }}</span>
                <NuxtLink v-if="canOpenPointShop" to="/point-shop/items" class="ml-1.5 whitespace-nowrap font-medium text-primary hover:underline">{{ t('point.ticketPriceMovedLink') }} ›</NuxtLink>
            </Message>

            <AppGadget v-for="section in SECTIONS" :key="section.anchor" :title="t(section.titleKey)" :anchor="section.anchor" :changes="sectionChanges(section)" :invalid="sectionInvalid(section)">
                <template #title-extra>
                    <Tag v-if="section.notReady" :value="t('point.notReadyTag')" icon="pi pi-clock" severity="secondary" />
                    <template v-if="section.groups.length === 1 && section.groups[0]">
                        <Tag v-if="UtilPoint.groupTag(section.groups[0].group, form) === 'OFF'" :value="t('point.offTag')" icon="pi pi-power-off" severity="warn" />
                        <Tag v-else-if="UtilPoint.groupTag(section.groups[0].group, form) === 'NO_LIMIT'" :value="t('point.noLimitTag')" icon="pi pi-arrows-v" severity="info" />
                    </template>
                </template>

                <p class="mb-4 text-xs leading-relaxed text-muted-color">{{ t(section.leadKey) }}</p>

                <div class="flex flex-col gap-5">
                    <div v-for="group in section.groups" :key="group.group" role="group" :aria-label="group.labelKey ? t(group.labelKey) : t(section.titleKey)" class="flex flex-col gap-3">
                        <div v-if="group.labelKey" class="flex items-center gap-2">
                            <h3 class="text-sm font-medium">{{ t(group.labelKey) }}</h3>
                            <Tag v-if="UtilPoint.groupTag(group.group, form) === 'OFF'" :value="t('point.offTag')" icon="pi pi-power-off" severity="warn" />
                            <Tag v-else-if="UtilPoint.groupTag(group.group, form) === 'NO_LIMIT'" :value="t('point.noLimitTag')" icon="pi pi-arrows-v" severity="info" />
                        </div>

                        <div class="grid gap-x-4 gap-y-3 sm:grid-cols-[repeat(3,minmax(0,15rem))]">
                            <LabeledField v-for="f in group.fields" :key="f.field" :label="t(`point.field.${f.field}`)" :hint="UtilPointMessages.range(t, f.field, locale)" :help="t(`point.help.${f.field}`)" :error="errorOf(f.field)">
                                <!-- 적립 조건 칸은 칸마다 꺼진다(0 = 그 조건 없음) -->
                                <template v-if="UtilPoint.fieldOff(f.field, form)" #aside>
                                    <Tag :value="t('point.offTag')" icon="pi pi-power-off" severity="warn" class="shrink-0" />
                                </template>
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
                                            @update:model-value="(value: string | undefined) => setField(f.field, value ?? '')"
                                        />
                                        <span class="text-sm text-muted-color">{{ t(`point.unit.${f.unit}`) }}</span>
                                    </div>
                                </template>
                            </LabeledField>
                        </div>

                        <!-- 켜기/끄기 칸 — 권한이 없으면 disabled(서버 값이 그대로 보인다). 「꺼짐」 딱지 대상이 아니다 -->
                        <div v-if="group.switches?.length" class="flex flex-col gap-3">
                            <div v-for="s in group.switches" :key="s" class="flex items-start gap-2">
                                <ToggleSwitch
                                    :model-value="form[s]"
                                    :input-id="`${uid}-${s}`"
                                    :name="s"
                                    :disabled="!canEdit || saving"
                                    :invalid="Boolean(serverErrors[s])"
                                    :aria-describedby="`${uid}-${s}-msg`"
                                    class="shrink-0"
                                    @update:model-value="(value: boolean) => setField(s, value)"
                                />
                                <div class="flex min-w-0 flex-col gap-0.5">
                                    <label :for="`${uid}-${s}`" class="text-sm">{{ t(`point.field.${s}`) }}</label>
                                    <span :id="`${uid}-${s}-msg`" aria-live="polite" class="text-xs leading-relaxed" :class="serverErrors[s] ? 'text-red-600 dark:text-red-400' : 'text-muted-color'" :role="serverErrors[s] ? 'alert' : undefined">
                                        {{ serverErrors[s] || t(`point.help.${s}`) }}
                                    </span>
                                </div>
                            </div>
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

            <!-- 저장 줄 — 구획 밖, 모든 구획 아래. 모든 칸을 한 번에 저장한다. 저장 실패(칸에 붙지 않는 것)는 이 줄 안에 선다 -->
            <StickySaveBar v-if="canEdit" submit revertable :dirty="dirty" :saving="saving" :disabled="!canSave" :count="changed.length" :error-count="errorCount" :error="formError" @revert="revert" />
            <p v-else class="text-xs text-muted-color">{{ t('point.readOnlyNote') }}</p>
        </form>
    </div>
</template>

<script setup lang="ts">
import type { ApiError } from '~/composables/common/useApiClient';
import type { PointSettingsField, PointSettingsNumberField, PointSettingsResponse, PointSettingsSwitchField } from '~/composables/point/api/usePointApi.types';
import UtilPoint, { type PointSettingsForm, type PointSettingsGroup } from '~/utils/util-point';
import UtilPointMessages from '~/utils/util-point-messages';

/** 정수 칸 하나 — 단위 글자 키(`point.unit.*`) */
interface PolicyField {
    field: PointSettingsNumberField;
    unit: 'point' | 'times' | 'rank' | 'likes' | 'chars' | 'seconds' | 'days';
}
/** 같은 딱지 판정을 받는 칸 묶음. 이름(`labelKey`)은 구획에 묶음이 둘 이상일 때만. 켜기/끄기 칸(`switches`)은 정수 칸 아래에 */
interface PolicyGroup {
    group: PointSettingsGroup;
    labelKey?: string;
    fields: PolicyField[];
    switches?: PointSettingsSwitchField[];
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
        groups: [
            { group: 'attendance', labelKey: 'point.group.attendance', fields: [{ field: 'attendancePoints', unit: 'point' }] },
            { group: 'attendanceStreak', labelKey: 'point.group.attendanceStreak', fields: [{ field: 'attendanceStreakDays', unit: 'days' }, { field: 'attendanceStreakBonus', unit: 'point' }] },
        ],
        notes: ['point.note.attendanceOnce', 'point.note.attendanceStreak'],
    },
    {
        anchor: 'point-activity',
        titleKey: 'point.section.activity',
        leadKey: 'point.lead.activity',
        groups: [
            { group: 'post', labelKey: 'point.group.post', fields: [{ field: 'postPoints', unit: 'point' }, { field: 'postDailyCap', unit: 'times' }] },
            { group: 'comment', labelKey: 'point.group.comment', fields: [{ field: 'commentPoints', unit: 'point' }, { field: 'commentDailyCap', unit: 'times' }] },
            // 커머스 1-3 적립 조건(commerce.md 6-2) — 칸마다 0 = 그 조건 없음, 스위치는 딱지 대상이 아니다
            {
                group: 'earnConditions',
                labelKey: 'point.group.earnConditions',
                fields: [
                    { field: 'postMinLength', unit: 'chars' },
                    { field: 'commentMinLength', unit: 'chars' },
                    { field: 'writeIntervalSeconds', unit: 'seconds' },
                ],
                switches: ['commentOwnPostExcluded', 'commentOncePerPost'],
            },
        ],
        notes: ['point.note.conditionsKeepContent', 'point.note.conditionsChars', 'point.note.conditionsBoard'],
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
        // 커머스 1-3 — 0 은 「꺼짐」이 아니라 「한도 없음」
        anchor: 'point-daily-total',
        titleKey: 'point.section.dailyTotal',
        leadKey: 'point.lead.dailyTotal',
        groups: [{ group: 'dailyTotal', fields: [{ field: 'dailyTotalCap', unit: 'point' }] }],
        notes: ['point.note.dailyTotalIncluded', 'point.note.dailyTotalExcluded', 'point.note.dailyTotalPartial', 'point.note.dailyTotalCounts'],
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
        // 커머스 1-3 — 본인인증 완료 시 평생 1회(commerce.md 6-2). 「아직 적용되지 않아요」 딱지를 뗐다
        notes: ['point.note.signup', 'point.note.signupRestricted', 'point.note.signupNoRetro', 'point.note.signupNoDailyTotal'],
    },
    {
        // 커머스 1-3 — 1-1 이 읽기 전용으로 두었던 조정 문턱을 여기서 저장한다. 끄는 개념이 없어 딱지가 없다
        anchor: 'point-adjust',
        titleKey: 'point.section.adjust',
        leadKey: 'point.lead.adjust',
        groups: [{ group: 'adjust', fields: [{ field: 'adjustLargeThreshold', unit: 'point' }] }],
        notes: ['point.note.adjustLarge'],
    },
    // 「상점」 구획은 없앴다 — 변경권 값은 포인트 상점 품목 가격(commerce-stage1 결정 2, V86 이 정책 칸을 지웠다)
];

/** 운영 원칙(읽기 전용) — 0이면 꺼짐(총 한도는 한도 없음) · 하루 한도 · 0P 바닥 · 정지·탈퇴·동결 · 삭제 회수 · 게시판별(최소 글자 수만 끌 수 있다) · 15초 반영·소급 없음 · 등급과 무관 · 소멸(적립 1년 · 충전 없음, 커머스 1-1) · 비회원 글·댓글 없음 */
const RULES = ['point.rule.zeroOff', 'point.rule.dailyCount', 'point.rule.floorZero', 'point.rule.restricted', 'point.rule.revoke', 'point.rule.boardToggle', 'point.rule.apply', 'point.rule.noGrade', 'point.rule.expiry', 'point.rule.noGuest'] as const;

const { t, locale } = useI18n();
useSeoMeta({ title: () => t('point.policyTitle') });
const uid = useId();

const api = usePointApi();
const toast = useAppToast();
const toApiError = useApiErrorOf();
const time = useDisplayTime();
const { confirm } = useConfirmDialog();
const { can } = useAdminPermission();

/** 정책을 바꿀 수 있나(정의: util-permission-catalog.ts) — 숨기는 것은 편의이고 막는 것은 서버다 */
const canEdit = computed(() => can('point.settings'));
/** 포인트 상점 품목 관리를 열 수 있나 — 바뀔 이름(`pointshop.read`)으로 묻는다(카탈로그는 서버 단계) */
const canOpenPointShop = computed(() => can('pointshop.read'));

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
    return section.groups.flatMap((group): PointSettingsField[] => [...group.fields.map((f) => f.field), ...(group.switches ?? [])]);
}
function sectionChanges(section: PolicySection): number {
    return fieldsOf(section).filter((field) => changed.value.includes(field)).length;
}
function sectionInvalid(section: PolicySection): number {
    return fieldsOf(section).filter((field) => problems.value[field] || serverErrors.value[field]).length;
}

/** 칸 아래 오류 — 화면이 미리 검사한 문제가 먼저, 없으면 서버가 거절한 문구 */
function errorOf(field: PointSettingsNumberField): string {
    const problem = problems.value[field];
    if (problem) return UtilPointMessages.settingsProblem(t, problem, field, locale.value);
    return serverErrors.value[field] ?? '';
}

/** 칸 하나를 바꾼다 — 정수 칸은 입력 글자, 켜기/끄기 칸은 불리언. 그 칸의 서버 오류를 지운다 */
function setField(field: PointSettingsField, value: string | boolean) {
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
