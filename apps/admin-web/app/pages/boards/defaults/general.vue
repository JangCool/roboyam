<template>
    <!--
        게시판 기본 설정 › 일반(`/boards/defaults/general` — 기본 설정의 첫 하위 메뉴, 부모 주소 `/boards/defaults` 는 여기로 보낸다) — 모든 게시판에 일괄 적용되는 값이다. 게시판이 비워 둔(「기본값 따름」) 정책 칸은 이 값을 따르고, 게시판별로 따로 정한 칸은 그 게시판 값이 이긴다(`/boards/[slug]`).
        그누보드7 게시판 「기본 설정」(날짜 표시 방식·목록 글 수·삭제 첨부 정리)과 community `board_defaults`(글쓰기 자격·작성 속도·노출·보호·지킴이 한도) + 베스트 댓글 기준 + 댓글 공개 시간을 한 화면으로 합쳤다.
        community 와 다른 점: 글쓰기·댓글 최소 자격은 **등급 목록**(`useGradeAppearance`)에서 고르고(`gradeKey` 또는 운영진만, 댓글은 끔도), 「지킴이」 한도는 **게시판 매니저·스텝** 한도(+ 인원 상한)다.
        베스트 댓글은 community 에서 자기 저장 줄이 따로 있었지만 여기서는 **화면 하나로 한 번 저장**(PUT 한 번)이라 다른 칸과 함께 하단 떠 있는 줄(`StickySaveBar`)로 저장한다. 댓글 공개 시간의 토론·소모임 예외는 roboyam 에 개념이 없어 뺐다.
        맨 위 안내: 기본값을 따르는 게시판 N개 · 따로 정한 게시판 M개(목록에서 보기 → `/boards?differing=1`). 칸마다 그 칸을 따로 정한 게시판이 있으면 「N개 게시판이 따로 정함」 단추(누르면 이름 목록·링크).
        구획(우측 구역 내비): 표시 · 쓰기 권한 · 작성 속도 · 목록·노출 · 첨부파일 정리 · 포인트·글 보호 · 댓글 공개 시간 · 베스트 댓글 · 게시판 매니저·스텝 · 고정 규칙(읽기 전용).
        숫자 칸은 글자 입력(grades.vue 와 같은 이유) — 저장 전 범위 검사(`UtilBoard`), 서버 `fields` 오류는 칸 아래. 칸 정의·범위는 `UtilBoard.SPECS` 한 곳.
        권한: 조회 `board.read`(메뉴) · 칸 대부분 `board.settings` · 매니저·스텝 구획 `board.staff.limit` — 없는 권한의 칸은 readonly(서버 값 그대로), 둘 다 없으면 저장 줄 대신 읽기 전용 안내. 감추는 것은 편의이고 막는 것은 서버다.
        저장하지 않은 변경이 있는 채 다른 화면으로 가면 확인을 묻는다. 서버 연결됨(`useBoardApi`). 바뀐 칸 없이 저장하면 서버는 마지막 저장 기록(`updatedAt/By`)을 바꾸지 않는다.
    -->
    <div class="flex flex-col gap-4">
        <PageTitle :title="t('board.tabTitle.general')" :subtitle="t('board.defaultsSubtitle')" />

        <!-- 읽는 중 -->
        <AppGadget v-if="loading && !saved" :title="t('board.section.write')">
            <div class="flex flex-col gap-3" role="status" :aria-label="t('common.loading')">
                <Skeleton v-for="n in 4" :key="n" height="3rem" />
            </div>
        </AppGadget>

        <!-- 읽기 실패 — 서버 문구를 그대로 보이고 다시 시도 -->
        <AppGadget v-else-if="!saved || !form" :title="t('board.tabTitle.general')">
            <ErrorState :title="t('board.defaultsLoadFailed')" :description="loadError" retry :retrying="loading" @retry="load" />
        </AppGadget>

        <form v-else class="flex flex-col gap-4" novalidate @submit.prevent="save">
            <!-- 영향 안내 — 바꾸면 기본값을 따르는 게시판에 바로 적용된다 -->
            <Message severity="info" :closable="false" size="small">
                <span>{{ UtilBoardMessages.defaultsBanner(t, saved.followingCount, saved.boardCount) }}</span>
                <NuxtLink to="/boards?differing=1" class="ml-1 font-medium underline">{{ t('board.seeDiffering') }}</NuxtLink>
                <span v-if="saved.updatedAt" class="mt-1 block text-xs opacity-80">{{ t('board.lastSaved', { at: time.full(saved.updatedAt), by: saved.updatedBy ?? '—' }) }}</span>
            </Message>

            <!-- 1. 표시 — 날짜 표시 방식(라디오 카드 + 예시 줄) -->
            <AppGadget :title="t('board.section.display')" anchor="defaults-display" :changes="sectionChanges['defaults-display']">
                <p class="mb-3 text-xs leading-relaxed text-muted-color">{{ t('board.sectionLead.display') }}</p>
                <div role="radiogroup" :aria-label="t('board.field.dateDisplay')" class="grid gap-3 sm:grid-cols-2">
                    <label
                        v-for="mode in DATE_MODES"
                        :key="mode"
                        class="flex gap-3 rounded-lg border p-3"
                        :class="[form.dateDisplay === mode ? 'border-primary bg-primary-50 dark:bg-primary-950' : 'border-(--line)', canSettings && !saving ? 'cursor-pointer' : '']"
                    >
                        <RadioButton v-model="form.dateDisplay" :value="mode" :name="`${idBase}-date`" :disabled="!canSettings || saving" class="mt-0.5 shrink-0" />
                        <span class="flex min-w-0 flex-col gap-1">
                            <span class="text-sm font-medium">{{ t(`board.choice.dateDisplay.${mode}`) }}</span>
                            <span class="text-xs leading-relaxed text-muted-color">{{ t(`board.dateDisplayHelp.${mode}`) }}</span>
                            <span class="text-xs tabular-nums">{{ t(`board.dateDisplayExample.${mode}`) }}</span>
                        </span>
                    </label>
                </div>
                <p class="mt-3 flex items-start gap-1.5 text-xs leading-relaxed text-muted-color">
                    <i class="pi pi-info-circle mt-0.5 text-[11px]" aria-hidden="true" />
                    <span>
                        {{ t('board.dateDisplayFormatNote') }}
                        <NuxtLink to="/settings/general" class="underline">{{ t('board.dateDisplayFormatLink') }}</NuxtLink>
                    </span>
                </p>
            </AppGadget>

            <!-- 2~9. 칸 구획 — 같은 칸 부품(`BoardSettingField`)으로 그린다 -->
            <AppGadget v-for="section in FIELD_SECTIONS" :key="section" :title="t(`board.section.${section}`)" :anchor="`defaults-${section}`" :changes="sectionChanges[`defaults-${section}`]" :invalid="sectionInvalid[`defaults-${section}`]">
                <p class="mb-4 text-xs leading-relaxed text-muted-color">{{ t(`board.sectionLead.${section}`) }}</p>
                <Message v-if="section === 'staff' && !canStaffLimit" severity="secondary" :closable="false" size="small" class="mb-4">{{ t('board.staffLimitReadOnly') }}</Message>

                <div class="grid gap-x-8 gap-y-5 md:grid-cols-2">
                    <BoardSettingField
                        v-for="field in UtilBoard.DEFAULT_SECTIONS[`defaults-${section}`]"
                        :key="field"
                        :field="field"
                        :model-value="form[field]"
                        :grades="grades"
                        :editable="canEdit(field) && !saving"
                        :inactive="inactiveOf(field)"
                        :note="noteOf(field)"
                        :error="errorOf(field)"
                        :overrides="saved.overrides[field as BoardPolicyField] ?? []"
                        @update:model-value="(value: BoardFieldValue) => onInput(field, value)"
                    />
                </div>

                <!-- 구획별 덧붙임 안내 -->
                <ul v-if="NOTES[section]" class="m-0 mt-4 flex list-none flex-col gap-1.5 border-t border-(--line) p-0 pt-3">
                    <li v-for="key in NOTES[section]" :key="key" class="flex items-start gap-2 text-xs leading-relaxed text-muted-color">
                        <i class="pi pi-info-circle mt-0.5 text-[11px]" aria-hidden="true" />
                        <span>{{ t(key) }}</span>
                    </li>
                </ul>
            </AppGadget>

            <!-- 10. 고정 규칙 — 읽기 전용 -->
            <AppGadget :title="t('board.section.fixed')" anchor="defaults-fixed">
                <p class="mb-3 text-xs leading-relaxed text-muted-color">{{ t('board.sectionLead.fixed') }}</p>
                <ul class="m-0 grid list-none gap-2 p-0 sm:grid-cols-2">
                    <li v-for="key in FIXED_RULES" :key="key" class="flex items-start gap-2 text-sm">
                        <i class="pi pi-lock mt-1 text-[11px] text-muted-color" aria-hidden="true" />
                        <span>{{ t(key) }}</span>
                    </li>
                </ul>
            </AppGadget>

            <!-- 저장 줄 — 구획 밖, 모든 구획 아래. 모든 칸을 한 번에 저장한다 -->
            <StickySaveBar v-if="canSettings || canStaffLimit" submit revertable :dirty="dirty" :saving="saving" :disabled="!canSave" :count="changed.length" :error-count="errorCount" :error="formError" @revert="revert" />
            <p v-else class="text-xs text-muted-color">{{ t('board.readOnlyNote') }}</p>
        </form>
    </div>
</template>

<script setup lang="ts">
import type { BoardDefaultsField, BoardDefaultsResponse, BoardPolicyField } from '~/composables/board/api/useBoardApi.types';
import type { BoardDefaultsForm, BoardDefaultsSection, BoardFieldValue } from '~/utils/util-board';
import UtilBoard from '~/utils/util-board';
import UtilBoardMessages from '~/utils/util-board-messages';

const { t } = useI18n();
useSeoMeta({ title: () => t('board.tabTitle.general') });

const api = useBoardApi();
const appearance = useGradeAppearance();
const toast = useAppToast();
const { confirm } = useConfirmDialog();
const { can } = useAdminPermission();
const time = useDisplayTime();
const idBase = useId();

/** 이 화면에서 하는 일에 필요한 권한(정의: util-permission-catalog.ts) */
const canSettings = computed(() => can('board.settings'));
const canStaffLimit = computed(() => can('board.staff.limit'));

const DATE_MODES = ['STANDARD', 'RELATIVE'] as const;
/** 칸 구획(표시·고정 규칙은 따로 그린다) */
const FIELD_SECTIONS = ['write', 'rate', 'list', 'attachment', 'protect', 'guest', 'best', 'staff'] as const;
/** 구획 아래 덧붙임 안내 문구 키 */
const NOTES: Partial<Record<(typeof FIELD_SECTIONS)[number], readonly string[]>> = {
    write: ['board.note.writeVerified', 'board.note.writeGuest', 'board.note.writeOverride'],
    rate: ['board.note.rateStaff'],
    attachment: ['board.note.attachmentOrphan', 'board.note.attachmentRestore'],
    protect: ['board.note.protectStaff', 'board.note.pointAmount'],
    guest: ['board.note.guestDelay'],
    staff: ['board.note.staffEligible', 'board.note.staffAutoRelease', 'board.note.staffDaily', 'board.note.staffOrder', 'board.note.staffGuestContent'],
};
/** 고정 규칙(바꿀 수 없는 것) 문구 키 */
const FIXED_RULES = ['board.fixed.noSecret', 'board.fixed.noDislike', 'board.fixed.noAutoHide', 'board.fixed.replyDepth', 'board.fixed.lengths', 'board.fixed.guestRead'] as const;

const saved = ref<BoardDefaultsResponse | null>(null);
const form = ref<BoardDefaultsForm | null>(null);
const loading = ref(false);
const loadError = ref('');
const saving = ref(false);
const formError = ref('');
/** 서버가 거절한 칸 문구 — 그 칸을 고치면 지운다 */
const serverFields = ref<Record<string, string>>({});

const grades = computed(() => appearance.grades.value);
const problems = computed(() => (form.value ? UtilBoard.defaultsProblems(form.value) : {}));
const changed = computed<BoardDefaultsField[]>(() => (form.value && saved.value ? UtilBoard.defaultsChanged(form.value, saved.value.defaults) : []));
const dirty = computed(() => changed.value.length > 0);
const errorCount = computed(() => new Set([...Object.keys(problems.value), ...Object.keys(serverFields.value)]).size);
/** 서버가 거절한 칸은 저장을 막지 않는다 — 서버가 다시 판정한다 */
const canSave = computed(() => (canSettings.value || canStaffLimit.value) && dirty.value && Object.keys(problems.value).length === 0);

/** 구획 주소 → 바뀐 칸 수 · 오류 칸 수 */
const sectionChanges = computed(() => countBySection((field) => changed.value.includes(field)));
const sectionInvalid = computed(() => countBySection((field) => Boolean(errorOf(field))));

function countBySection(test: (field: BoardDefaultsField) => boolean): Record<string, number> {
    const out: Record<string, number> = {};
    for (const [anchor, fields] of Object.entries(UtilBoard.DEFAULT_SECTIONS) as [BoardDefaultsSection, readonly BoardDefaultsField[]][]) out[anchor] = fields.filter(test).length;
    return out;
}

onMounted(() => {
    void appearance.ensureLoaded();
    void load();
});

async function load() {
    loading.value = true;
    loadError.value = '';
    try {
        apply(await api.defaults());
    } catch (e) {
        loadError.value = UtilBoardMessages.of(t, e);
    } finally {
        loading.value = false;
    }
}

function apply(res: BoardDefaultsResponse) {
    saved.value = res;
    form.value = UtilBoard.defaultsFormOf(res.defaults);
    serverFields.value = {};
}

function revert() {
    if (!saved.value) return;
    form.value = UtilBoard.defaultsFormOf(saved.value.defaults);
    serverFields.value = {};
    formError.value = '';
}

function canEdit(field: BoardDefaultsField): boolean {
    return can(UtilBoard.SPECS[field].permission);
}

/** 다른 칸 때문에 지금은 쓰이지 않는 칸 — 보존 기간(정리 꺼짐) · 예약 삭제 시간(예약 삭제가 아님) · 이미지 올리기(글 에디터가 고급이 아님) */
function inactiveOf(field: BoardDefaultsField): boolean {
    if (!form.value) return false;
    if (field === 'attachmentRetentionDays') return !form.value.attachmentPurgeEnabled;
    if (field === 'commentProtectDelayMinutes') return form.value.commentProtectDelete !== 'DELAY';
    if (field === 'imageUploadAllowed') return UtilBoard.imageUploadIdle(form.value.postEditor);
    return false;
}

function noteOf(field: BoardDefaultsField): string {
    if (!form.value) return '';
    if (field === 'attachmentRetentionDays' && inactiveOf(field)) return t('board.inactiveNote.retention');
    if (field === 'commentProtectDelayMinutes' && inactiveOf(field)) return t('board.inactiveNote.delay');
    if (field === 'imageUploadAllowed' && inactiveOf(field)) return t('board.inactiveNote.imageEditor');
    const protectOff = UtilBoard.parse(form.value.commentProtectMin) === 0;
    if (protectOff && (field === 'commentProtectEdit' || field === 'commentProtectDelete')) return t('board.inactiveNote.protectOff');
    return '';
}

/** 칸 아래 오류 — 화면 검사가 먼저, 없으면 서버 문구 */
function errorOf(field: BoardDefaultsField): string {
    const problem = problems.value[field];
    if (problem) return UtilBoardMessages.numProblem(t, field, problem);
    return serverFields.value[field] ?? '';
}

function onInput(field: BoardDefaultsField, value: BoardFieldValue) {
    if (!form.value) return;
    (form.value as Record<BoardDefaultsField, BoardFieldValue>)[field] = value;
    if (serverFields.value[field]) {
        const { [field]: _removed, ...rest } = serverFields.value;
        serverFields.value = rest;
    }
}

async function save() {
    if (saving.value || !canSave.value || !form.value || !saved.value) return;
    const body = UtilBoard.toDefaults(form.value);
    if (!body) return;
    formError.value = '';
    serverFields.value = {};
    saving.value = true;
    try {
        apply(await api.saveDefaults(body));
        toast.success(t('board.defaultsSaved', { n: saved.value?.followingCount ?? 0 }));
    } catch (e) {
        const rejected = UtilBoard.fieldErrorsOf(e);
        if (rejected) {
            serverFields.value = rejected.fields;
            formError.value = t('board.saveFieldsRejected');
        } else {
            formError.value = `${t('board.saveFailed')} — ${UtilBoardMessages.of(t, e)}`;
        }
    } finally {
        saving.value = false;
    }
}

// 저장하지 않은 변경이 있는 채 다른 화면으로 가려 하면 묻는다
onBeforeRouteLeave(async () => {
    if (!dirty.value) return true;
    return confirm({ title: t('board.leaveTitle'), message: t('board.leaveMessage'), confirmLabel: t('board.leaveConfirm'), cancelLabel: t('board.leaveCancel'), tone: 'danger' });
});
</script>
