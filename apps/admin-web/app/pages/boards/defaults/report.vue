<template>
    <!--
        게시판 기본 설정 › 신고 정책 — 모든 게시판에 적용되는 신고 받기·자동 숨김·즉시 조치·남용 방지·알림. **자기 요청·자기 저장**(`GET·PUT /api/admin/boards/defaults/report`, 하단 StickySaveBar).
        그누보드7 원본(`config/settings/defaults.json` 신고 탭) → roboyam 권장안:
          · auto_hide_threshold 5 · auto_hide_target both → 그대로(5 · BOTH). community 는 신고 몰이 악용 때문에 "자동 숨김 없음"이었는데, 권장안은 **자동 숨김을 쓰되 악용 방지 조건**을 붙인다:
            서로 다른 회원 수로만 센다(고정) · 최소 등급(기본 두 번째 등급 — 새싹 신고는 접수만 되고 셈에서 빠짐) · 운영자·공지 글 제외. 자동 숨김은 「검토 대기 숨김」이고 반려되면 자동 복구한다.
          · 즉시 조치(그누보드엔 없음, 추가): 불법촬영물·아동성착취물은 1건으로 즉시 숨김 + 긴급 알림, **끌 수 없음**(전기통신사업법 제22조의5 삭제·접속차단 의무).
            권리침해(개인정보·명예훼손)는 정보통신망법 제44조의2 임시조치 기간(1~30일, 기본 30).
          · daily_report_limit 10 · rejection_limit 5회/30일 → 그대로. 차단 기간 rejectionBlockDays 7일은 그누보드에 없어 추가. 신고 간격 60초는 그누보드 보안 탭의 값을 이 화면으로 옮겼다.
          · notify_admin_on_report + scope(per_case/per_report) → 범위만 여기, 받는 운영자·채널은 설정 › 알림 › 이벤트 `REPORT_RECEIVED`. notify_author_on_report_action → 그대로. 신고자에게 결과 알림은 추가.
        신고 사유는 스텝·매니저 처리 사유 코드(`docs/domain/operator-permissions.md` 처리 규칙)와 같은 코드 체계(`BoardReportReason` 주석)다 — 사유 표는 읽기 전용.
        권한: 조회 `board.read`, 변경 `board.settings` — 없으면 칸 readonly·저장 줄 대신 안내. 서버 연결됨(`useBoardApi`).
        신고 관리(`/reports`, report-admin-contract)가 집행하는 것: 반려 제한(「반려」 = 허위 신고로 표시한 무시만 센다) · 작성자 알림(`notifyAuthorOnAction` — 자동 숨김·숨기기·삭제) · 신고자 결과 알림(`notifyReporterOnResult`) · 무시하면 자동 숨김 복구 — 안내 문구도 이에 맞췄다.
    -->
    <div class="flex flex-col gap-4">
        <PageTitle :title="t('board.tabTitle.report')" :subtitle="t('board.tabSubtitle.report')" />

        <AppGadget v-if="tab.loading.value && !tab.response.value" :title="t('board.section.reportReceive')">
            <div class="flex flex-col gap-3" role="status" :aria-label="t('common.loading')">
                <Skeleton v-for="n in 4" :key="n" height="3rem" />
            </div>
        </AppGadget>

        <AppGadget v-else-if="!tab.response.value || !form" :title="t('board.tabTitle.report')">
            <ErrorState :title="t('board.tabLoadFailed')" :description="tab.loadError.value" retry :retrying="tab.loading.value" @retry="tab.load" />
        </AppGadget>

        <form v-else class="flex flex-col gap-4" novalidate @submit.prevent="tab.save">
            <p v-if="tab.response.value.updatedAt" class="-mt-2 text-xs text-muted-color">{{ t('board.lastSaved', { at: time.full(tab.response.value.updatedAt), by: tab.response.value.updatedBy ?? '—' }) }}</p>

            <!-- 신고 받기 + 사유 표 -->
            <AppGadget :title="t('board.section.reportReceive')" anchor="report-receive" :changes="tab.changesIn(['reportEnabled'])">
                <BoardSettingField field="reportEnabled" :model-value="form.reportEnabled" :editable="editable" @update:model-value="(v: BoardFieldValue) => tab.set('reportEnabled', v)" />
                <p class="mt-3 flex items-start gap-2 text-xs leading-relaxed text-muted-color">
                    <i class="pi pi-info-circle mt-0.5 text-[11px]" aria-hidden="true" />
                    <span>{{ t('board.note.reportMembersOnly') }}</span>
                </p>
                <div class="mt-4 overflow-x-auto">
                    <table class="w-full min-w-[32rem] border-collapse text-[13px]">
                        <caption class="mb-2 text-left text-xs text-muted-color">{{ t('board.reasonTableCaption') }}</caption>
                        <thead>
                            <tr class="border-b border-(--line) text-left text-xs text-muted-color">
                                <th scope="col" class="py-2 pr-3 font-medium">{{ t('board.reasonCol.reason') }}</th>
                                <th scope="col" class="py-2 pr-3 font-medium">{{ t('board.reasonCol.handling') }}</th>
                                <th scope="col" class="py-2 font-medium">{{ t('board.reasonCol.staff') }}</th>
                            </tr>
                        </thead>
                        <tbody>
                            <tr v-for="reason in REASONS" :key="reason" class="border-b border-(--line) last:border-0">
                                <th scope="row" class="py-2 pr-3 text-left font-medium">{{ t(`board.reason.${reason}`) }}</th>
                                <td class="py-2 pr-3" :class="reason === 'ILLEGAL_FILMING' ? 'text-red-700 dark:text-red-400' : ''">{{ handlingOf(reason) }}</td>
                                <td class="py-2 text-muted-color">{{ t(STAFF_CODES.includes(reason) ? 'common.yes' : 'common.no') }}</td>
                            </tr>
                        </tbody>
                    </table>
                </div>
            </AppGadget>

            <!-- 자동 숨김 -->
            <AppGadget :title="t('board.section.reportAutoHide')" anchor="report-autohide" :changes="tab.changesIn(AUTO_HIDE)" :invalid="tab.invalidIn(AUTO_HIDE)">
                <p class="mb-4 text-xs leading-relaxed text-muted-color">{{ t('board.reportAutoHideLead') }}</p>
                <div class="grid gap-x-8 gap-y-5 md:grid-cols-2">
                    <BoardSettingField
                        v-for="field in AUTO_HIDE"
                        :key="field"
                        :field="field"
                        :model-value="form[field]"
                        :grades="grades"
                        :editable="editable"
                        :inactive="field !== 'autoHideThreshold' && autoHideOff"
                        :note="field !== 'autoHideThreshold' && autoHideOff ? t('board.inactiveNote.autoHideOff') : ''"
                        :error="tab.errorOf(field)"
                        @update:model-value="(v: BoardFieldValue) => tab.set(field, v)"
                    />
                    <!-- 고정 규칙 — 서로 다른 회원 수로만 센다 -->
                    <div class="flex flex-col gap-1.5">
                        <span class="flex items-center gap-1.5 text-sm font-medium"><i class="pi pi-lock text-[11px] text-muted-color" aria-hidden="true" />{{ t('board.field.autoHideCountDistinct') }}</span>
                        <p class="m-0 flex min-h-8 items-center text-sm">{{ t('board.fixedOn') }}</p>
                        <p class="m-0 text-xs leading-relaxed text-muted-color">{{ t('board.fieldHelp.autoHideCountDistinct') }}</p>
                    </div>
                </div>
                <ul class="m-0 mt-4 flex list-none flex-col gap-1.5 border-t border-(--line) p-0 pt-3">
                    <li v-for="key in ['board.note.autoHidePending', 'board.note.autoHideRestore']" :key="key" class="flex items-start gap-2 text-xs leading-relaxed text-muted-color">
                        <i class="pi pi-info-circle mt-0.5 text-[11px]" aria-hidden="true" />
                        <span>{{ t(key) }}</span>
                    </li>
                </ul>
            </AppGadget>

            <!-- 즉시 조치 사유 -->
            <AppGadget :title="t('board.section.reportImmediate')" anchor="report-immediate" :changes="tab.changesIn(['rightsTempMeasureDays'])" :invalid="tab.invalidIn(['rightsTempMeasureDays'])">
                <div class="grid gap-x-8 gap-y-5 md:grid-cols-2">
                    <div class="flex flex-col gap-1.5">
                        <span class="flex items-center gap-1.5 text-sm font-medium"><i class="pi pi-lock text-[11px] text-muted-color" aria-hidden="true" />{{ t('board.field.illegalContentImmediateHide') }}</span>
                        <div class="flex min-h-8 items-center gap-2">
                            <ToggleSwitch :model-value="true" disabled :aria-label="t('board.field.illegalContentImmediateHide')" />
                            <span class="text-sm">{{ t('common.on') }}</span>
                        </div>
                        <p class="m-0 text-xs leading-relaxed text-muted-color">{{ t('board.fieldHelp.illegalContentImmediateHide') }}</p>
                    </div>
                    <BoardSettingField field="rightsTempMeasureDays" :model-value="form.rightsTempMeasureDays" :editable="editable" :error="tab.errorOf('rightsTempMeasureDays')" @update:model-value="(v: BoardFieldValue) => tab.set('rightsTempMeasureDays', v)" />
                </div>
                <p class="mt-4 flex items-start gap-2 border-t border-(--line) pt-3 text-xs leading-relaxed text-muted-color">
                    <i class="pi pi-info-circle mt-0.5 text-[11px]" aria-hidden="true" />
                    <span>{{ t('board.note.rightsTempMeasure') }}</span>
                </p>
            </AppGadget>

            <!-- 신고 제한(남용 방지) -->
            <AppGadget :title="t('board.section.reportLimit')" anchor="report-limit" :changes="tab.changesIn(LIMIT)" :invalid="tab.invalidIn(LIMIT)">
                <div class="grid gap-x-8 gap-y-5 md:grid-cols-2">
                    <BoardSettingField
                        v-for="field in LIMIT"
                        :key="field"
                        :field="field"
                        :model-value="form[field]"
                        :editable="editable"
                        :inactive="(field === 'rejectionLimitDays' || field === 'rejectionBlockDays') && rejectionOff"
                        :note="(field === 'rejectionLimitDays' || field === 'rejectionBlockDays') && rejectionOff ? t('board.inactiveNote.rejectionOff') : ''"
                        :error="tab.errorOf(field)"
                        @update:model-value="(v: BoardFieldValue) => tab.set(field, v)"
                    />
                </div>
            </AppGadget>

            <!-- 알림 -->
            <AppGadget :title="t('board.section.reportNotify')" anchor="report-notify" :changes="tab.changesIn(NOTIFY)">
                <div class="grid gap-x-8 gap-y-5 md:grid-cols-2">
                    <BoardSettingField v-for="field in NOTIFY" :key="field" :field="field" :model-value="form[field]" :editable="editable" @update:model-value="(v: BoardFieldValue) => tab.set(field, v)" />
                </div>
                <p class="mt-4 flex items-start gap-2 border-t border-(--line) pt-3 text-xs leading-relaxed text-muted-color">
                    <i class="pi pi-info-circle mt-0.5 text-[11px]" aria-hidden="true" />
                    <span>
                        {{ t('board.note.reportOperatorEvent') }}
                        <NuxtLink to="/settings/notifications/operator" class="underline">{{ t('board.openNotificationEvents') }}</NuxtLink>
                    </span>
                </p>
            </AppGadget>

            <StickySaveBar v-if="tab.canEdit.value" submit revertable :dirty="tab.dirty.value" :saving="tab.saving.value" :disabled="!tab.canSave.value" :count="tab.changed.value.length" :error-count="tab.errorCount.value" :error="tab.formError.value" @revert="tab.revert" />
            <p v-else class="text-xs text-muted-color">{{ t('board.readOnlyNote') }}</p>
        </form>
    </div>
</template>

<script setup lang="ts">
import type { BoardReportReason, BoardReportSettings } from '~/composables/board/api/useBoardApi.types';
import type { BoardFieldValue } from '~/utils/util-board';
import UtilBoard from '~/utils/util-board';

const { t } = useI18n();
useSeoMeta({ title: () => t('board.tabTitle.report') });

const api = useBoardApi();
const appearance = useGradeAppearance();
const time = useDisplayTime();
const tab = useBoardDefaultsTab({ load: api.report, save: api.saveReport, savedKey: 'board.tabSaved' });

const REASONS: readonly BoardReportReason[] = ['SPAM_AD', 'ABUSE', 'OBSCENE', 'ILLEGAL_FILMING', 'RIGHTS', 'OTHER'];
/** 스텝·매니저 처리 사유에도 있는 코드 */
const STAFF_CODES: readonly BoardReportReason[] = ['SPAM_AD', 'ABUSE', 'OTHER'];
const AUTO_HIDE = ['autoHideThreshold', 'autoHideTarget', 'autoHideMinGrade', 'autoHideExemptStaffPosts'] as const satisfies readonly (keyof BoardReportSettings)[];
const LIMIT = ['dailyReportLimit', 'reportCooldownSeconds', 'rejectionLimitCount', 'rejectionLimitDays', 'rejectionBlockDays'] as const satisfies readonly (keyof BoardReportSettings)[];
const NOTIFY = ['notifyOperatorScope', 'notifyAuthorOnAction', 'notifyReporterOnResult'] as const satisfies readonly (keyof BoardReportSettings)[];

const form = computed(() => tab.form.value);
const grades = computed(() => appearance.grades.value);
const editable = computed(() => tab.canEdit.value && !tab.saving.value);
const autoHideOff = computed(() => UtilBoard.parse(form.value?.autoHideThreshold ?? '') === 0);
const rejectionOff = computed(() => UtilBoard.parse(form.value?.rejectionLimitCount ?? '') === 0);

/** 사유별 처리 — 즉시 숨김 · 임시조치 · 자동 숨김 셈 */
function handlingOf(reason: BoardReportReason): string {
    if (reason === 'ILLEGAL_FILMING') return t('board.handling.immediate');
    if (reason === 'RIGHTS') return t('board.handling.tempMeasure', { days: form.value?.rightsTempMeasureDays ?? '' });
    return autoHideOff.value ? t('board.handling.operator') : t('board.handling.autoHide', { n: form.value?.autoHideThreshold ?? '' });
}

onMounted(() => {
    void appearance.ensureLoaded();
    void tab.load();
});
</script>
