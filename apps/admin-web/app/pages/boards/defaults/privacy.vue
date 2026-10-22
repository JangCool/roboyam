<template>
    <!--
        게시판 기본 설정 › 개인정보 보유기간 — 게시판에 남는 개인정보를 기간이 지나면 파기·익명화한다. 자기 요청·자기 저장(`GET·PUT /api/admin/boards/defaults/privacy`, 하단 StickySaveBar).
        **이 화면의 변경과 「지금 실행」은 감사 기록 대상이다**(후보 `BOARD_PRIVACY_RETENTION_UPDATE` · `BOARD_PRIVACY_RUN`).
        그누보드7 원본(개인정보 탭: enabled false · ip_retention_days 365(1~3650) · guest_retention_days 0 + guest_mode anonymize/delete · 대상 건수 미리보기·대상 보기·지금 실행 · 켜기 전 처리방침 고지 안내) → roboyam 권장안:
          · 전체 스위치 기본 꺼짐·처리방침 고지 경고·대상 N건·대상 보기·지금 실행은 그대로.
          · 비회원 축은 그대로 살렸다: guestRetentionDays(0~3650, 0 = 처리 안 함) 권장 365 · guestMode ANONYMIZE(이름 ***·비밀번호·IP 파기, 글은 남김) 권장 / DELETE(글·답글·댓글 삭제 + 파기 — 되돌릴 수 없다는 경고, 그누보드 delete_warning 취지).
          · IP 보유 365일은 그대로, 하한을 90일로 올렸다(분쟁·수사 협조 대응 — 1년 권장).
          · 게시판에 남는 다른 개인정보 축을 더했다: 삭제된 글·댓글 원문(휴지통) 30일 · 신고 기록 365일(신고자 익명화) · 스텝·매니저 처리 기록 365일(operator-permissions.md 미확정 「처리 기록 보관 기간」의 권장값).
          · 삭제 원문 보존 기간이 「일반 › 첨부파일 정리」 보존 기간과 다르면 경고 줄(같게 두는 것을 권장).
        「대상 N건」은 **저장된** 설정 기준이다. 「지금 실행」은 축별로 저장된 값으로 한 번 실행하고(확인창에 처리 예정 N건), 저장하지 않은 변경이 있거나 저장된 스위치가 꺼져 있으면 비활성 + 이유. 평소에는 매일 배치(후보 `board-privacy-retention`, 03:30)가 한다.
        권한: 조회 `board.read`, 변경·지금 실행 `board.settings`. 서버 연결됨(`useBoardApi`). 지금 실행은 배치가 도는 중이면 409 `BOARD_PRIVACY_RUNNING`, 꺼져 있으면 409 `BOARD_PRIVACY_DISABLED` — 서버 문구를 토스트로 그대로 보인다. 대상 보기의 `id` 는 `post-<id>`·`comment-<id>`, 댓글 `title` 은 본문 앞 40자.
    -->
    <div class="flex flex-col gap-4">
        <PageTitle :title="t('board.tabTitle.privacy')" :subtitle="t('board.tabSubtitle.privacy')" />

        <AppGadget v-if="tab.loading.value && !tab.response.value" :title="t('board.section.privacyMaster')">
            <div class="flex flex-col gap-3" role="status" :aria-label="t('common.loading')">
                <Skeleton v-for="n in 4" :key="n" height="3rem" />
            </div>
        </AppGadget>

        <AppGadget v-else-if="!response || !form" :title="t('board.tabTitle.privacy')">
            <ErrorState :title="t('board.tabLoadFailed')" :description="tab.loadError.value" retry :retrying="tab.loading.value" @retry="tab.load" />
        </AppGadget>

        <form v-else class="flex flex-col gap-4" novalidate @submit.prevent="tab.save">
            <!-- 켜기 전 경고 -->
            <Message severity="warn" :closable="false" size="small">
                <span class="block font-medium">{{ t('board.privacyWarnTitle') }}</span>
                <span class="block text-xs">{{ t('board.privacyWarnBody') }}</span>
            </Message>

            <!-- 전체 스위치 -->
            <AppGadget :title="t('board.section.privacyMaster')" anchor="privacy-master" :changes="tab.changesIn(['retentionEnabled'])">
                <BoardSettingField field="retentionEnabled" :model-value="form.retentionEnabled" :editable="editable" @update:model-value="(v: BoardFieldValue) => tab.set('retentionEnabled', v)" />
                <p class="mt-3 text-xs leading-relaxed text-muted-color">
                    {{ t('board.privacyBatch') }}
                    <template v-if="response.lastRunAt"> · {{ t('board.privacyLastRun', { at: time.full(response.lastRunAt) }) }}</template>
                    <template v-if="response.updatedAt"> · {{ t('board.lastSaved', { at: time.full(response.updatedAt), by: response.updatedBy ?? '—' }) }}</template>
                </p>
            </AppGadget>

            <!-- 축별 기간 -->
            <AppGadget :title="t('board.section.privacyAxes')" anchor="privacy-axes" :changes="tab.changesIn(AXIS_FIELDS)" :invalid="tab.invalidIn(AXIS_FIELDS)">
                <ul class="m-0 flex list-none flex-col divide-y divide-(--line) p-0">
                    <li v-for="axis in AXES" :key="axis" class="grid gap-3 py-4 first:pt-0 last:pb-0 md:grid-cols-[minmax(0,1fr)_auto]">
                        <div class="flex min-w-0 flex-col gap-1">
                            <BoardSettingField :field="FIELD_OF[axis]" :model-value="form[FIELD_OF[axis]]" :editable="editable" :error="tab.errorOf(FIELD_OF[axis])" @update:model-value="(v: BoardFieldValue) => tab.set(FIELD_OF[axis], v)" />
                            <!-- 비회원 글 — 처리 방식(삭제를 고르면 되돌릴 수 없다는 경고) -->
                            <BoardSettingField
                                v-if="axis === 'guest'"
                                class="mt-2"
                                field="guestMode"
                                :model-value="form.guestMode"
                                :editable="editable"
                                :inactive="guestOff"
                                :note="guestOff ? t('board.inactiveNote.guestOff') : form.guestMode === 'DELETE' ? t('board.guestDeleteWarn') : ''"
                                @update:model-value="(v: BoardFieldValue) => tab.set('guestMode', v)"
                            />
                            <p class="m-0 text-xs leading-relaxed text-muted-color">{{ t(`board.privacyAxisNote.${axis}`) }}</p>
                            <p v-if="axis === 'deletedContent' && attachmentMismatch" class="m-0 flex items-start gap-1.5 text-xs text-amber-700 dark:text-amber-400">
                                <i class="pi pi-exclamation-triangle mt-0.5 text-[11px]" aria-hidden="true" />
                                <span>
                                    {{ t('board.privacyAttachmentMismatch', { days: response.attachmentRetentionDays }) }}
                                    <NuxtLink to="/boards/defaults/general#defaults-attachment" class="underline">{{ t('board.openAttachment') }}</NuxtLink>
                                </span>
                            </p>
                        </div>
                        <div class="flex flex-wrap items-start gap-2 md:flex-col md:items-end">
                            <span class="text-sm tabular-nums">{{ t('board.privacyTargetCount', { n: (response.targets[axis] ?? 0).toLocaleString(locale) }) }}</span>
                            <div class="flex gap-1.5">
                                <Button type="button" :label="t('board.privacyViewTargets')" size="small" severity="secondary" variant="outlined" @click="openTargets(axis)" />
                                <span v-if="tab.canEdit.value" v-tooltip.top="runBlockedReason || undefined" class="inline-flex">
                                    <Button type="button" :label="t('board.privacyRunNow')" icon="pi pi-play" size="small" severity="danger" variant="outlined" :disabled="Boolean(runBlockedReason) || running !== null" :loading="running === axis" :aria-description="runBlockedReason || undefined" @click="run(axis)" />
                                </span>
                            </div>
                        </div>
                    </li>
                </ul>
                <p v-if="tab.canEdit.value && runBlockedReason" class="mt-3 text-xs text-muted-color">{{ runBlockedReason }}</p>
            </AppGadget>

            <!-- 범위 밖 -->
            <AppGadget :title="t('board.section.privacyScope')" anchor="privacy-scope">
                <ul class="m-0 flex list-none flex-col gap-1.5 p-0">
                    <li v-for="key in ['board.note.privacyWithdrawn', 'board.note.privacyAudit']" :key="key" class="flex items-start gap-2 text-xs leading-relaxed text-muted-color">
                        <i class="pi pi-info-circle mt-0.5 text-[11px]" aria-hidden="true" />
                        <span>{{ t(key) }}</span>
                    </li>
                </ul>
            </AppGadget>

            <StickySaveBar v-if="tab.canEdit.value" submit revertable :dirty="tab.dirty.value" :saving="tab.saving.value" :disabled="!tab.canSave.value" :count="tab.changed.value.length" :error-count="tab.errorCount.value" :error="tab.formError.value" @revert="tab.revert" />
            <p v-else class="text-xs text-muted-color">{{ t('board.readOnlyNote') }}</p>
        </form>

        <!-- 대상 보기 -->
        <Dialog v-model:visible="targetsOpen" modal :draggable="false" :header="targetsAxis ? t('board.privacyTargetsTitle', { axis: t(`board.privacyAxis.${targetsAxis}`) }) : ''" :style="{ width: 'min(36rem, calc(100vw - 2rem))' }">
            <div v-if="targetsLoading" class="flex flex-col gap-2" role="status" :aria-label="t('common.loading')">
                <Skeleton v-for="n in 4" :key="n" height="2rem" />
            </div>
            <p v-else-if="targetsError" role="alert" class="m-0 text-sm text-red-600 dark:text-red-400">{{ targetsError }}</p>
            <template v-else-if="targets.length">
                <table class="w-full border-collapse text-[13px]">
                    <thead>
                        <tr class="border-b border-(--line) text-left text-xs text-muted-color">
                            <th scope="col" class="py-2 pr-3 font-medium">{{ t('board.targetCol.title') }}</th>
                            <th scope="col" class="py-2 pr-3 font-medium">{{ t('board.targetCol.board') }}</th>
                            <th scope="col" class="py-2 font-medium">{{ t('board.targetCol.createdAt') }}</th>
                        </tr>
                    </thead>
                    <tbody>
                        <tr v-for="row in targets" :key="row.id" class="border-b border-(--line) last:border-0">
                            <td class="max-w-0 truncate py-2 pr-3">{{ row.title }}</td>
                            <td class="py-2 pr-3 whitespace-nowrap">{{ row.boardName }}</td>
                            <td class="py-2 whitespace-nowrap tabular-nums">{{ time.date(row.createdAt) }}</td>
                        </tr>
                    </tbody>
                </table>
                <p class="mt-2 mb-0 text-xs text-muted-color">{{ t('board.privacyTargetsNote') }}</p>
            </template>
            <EmptyState v-else icon="pi pi-check-circle" :title="t('board.privacyNoTargets')" />
            <template #footer>
                <Button type="button" :label="t('common.close')" severity="secondary" variant="text" @click="targetsOpen = false" />
            </template>
        </Dialog>
    </div>
</template>

<script setup lang="ts">
import type { BoardPrivacyAxis, BoardPrivacyResponse, BoardPrivacySettings, BoardPrivacyTarget } from '~/composables/board/api/useBoardApi.types';
import type { BoardFieldValue } from '~/utils/util-board';
import UtilBoard from '~/utils/util-board';
import UtilBoardMessages from '~/utils/util-board-messages';

const { t, locale } = useI18n();
useSeoMeta({ title: () => t('board.tabTitle.privacy') });

const api = useBoardApi();
const toast = useAppToast();
const { confirm } = useConfirmDialog();
const time = useDisplayTime();
const tab = useBoardDefaultsTab<BoardPrivacySettings, BoardPrivacyResponse>({ load: api.privacy, save: api.savePrivacy, savedKey: 'board.tabSaved' });

const AXES: readonly BoardPrivacyAxis[] = ['ip', 'guest', 'deletedContent', 'reportRecord', 'staffAction'];
const FIELD_OF = {
    ip: 'ipRetentionDays',
    guest: 'guestRetentionDays',
    deletedContent: 'deletedContentRetentionDays',
    reportRecord: 'reportRecordRetentionDays',
    staffAction: 'staffActionRetentionDays',
} as const satisfies Record<BoardPrivacyAxis, keyof BoardPrivacySettings>;
const AXIS_FIELDS = [...Object.values(FIELD_OF), 'guestMode'];

const form = computed(() => tab.form.value);
const response = computed(() => tab.response.value);
const editable = computed(() => tab.canEdit.value && !tab.saving.value);
/** 비회원 글 보유기간이 0(처리 안 함) */
const guestOff = computed(() => UtilBoard.parse(form.value?.guestRetentionDays ?? '') === 0);
/** 삭제 원문 보존 기간이 첨부 보존 기간과 다르다(입력 기준) */
const attachmentMismatch = computed(() => {
    const days = UtilBoard.parse(form.value?.deletedContentRetentionDays ?? '');
    return days !== null && response.value !== null && days !== response.value.attachmentRetentionDays;
});

/** 지금 실행을 막는 이유 — 저장 안 한 변경 · 저장된 스위치 꺼짐 */
const runBlockedReason = computed(() => {
    if (tab.dirty.value) return t('board.privacyRunDirty');
    if (!response.value?.settings.retentionEnabled) return t('board.privacyRunDisabled');
    return '';
});

const running = ref<BoardPrivacyAxis | null>(null);

async function run(axis: BoardPrivacyAxis) {
    if (!response.value || runBlockedReason.value || running.value) return;
    const n = response.value.targets[axis] ?? 0;
    const ok = await confirm({
        title: t('board.privacyRunTitle', { axis: t(`board.privacyAxis.${axis}`) }),
        message: t('board.privacyRunMessage', { n: n.toLocaleString(locale.value) }),
        confirmLabel: t('board.privacyRunNow'),
        cancelLabel: t('common.cancel'),
        tone: 'danger',
    });
    if (!ok) return;
    running.value = axis;
    try {
        const result = await api.runPrivacy(axis);
        toast.success(t('board.privacyRunDone', { n: result.processed.toLocaleString(locale.value) }));
        await tab.load();
    } catch (e) {
        toast.error(`${t('board.privacyRunFailed')} — ${UtilBoardMessages.of(t, e)}`, 8000);
    } finally {
        running.value = null;
    }
}

// ── 대상 보기 ─

const targetsOpen = ref(false);
const targetsAxis = ref<BoardPrivacyAxis | null>(null);
const targets = ref<BoardPrivacyTarget[]>([]);
const targetsLoading = ref(false);
const targetsError = ref('');

async function openTargets(axis: BoardPrivacyAxis) {
    targetsAxis.value = axis;
    targets.value = [];
    targetsError.value = '';
    targetsOpen.value = true;
    targetsLoading.value = true;
    try {
        targets.value = await api.privacyTargets(axis);
    } catch (e) {
        targetsError.value = UtilBoardMessages.of(t, e);
    } finally {
        targetsLoading.value = false;
    }
}

onMounted(() => void tab.load());
</script>
