<template>
    <!--
        게시판 기본 설정 › 알림 — **회원이 받는 게시판 알림의 기본값**. 자기 요청·자기 저장(`GET·PUT /api/admin/boards/defaults/notifications`, 하단 StickySaveBar).
        그누보드7 원본(알림 탭: 채널 이메일·사이트 알림 × 유형 새 댓글·대댓글·답변글·관리자 처리·신규 게시글(관리자)·신고 접수(관리자)·신고 처리 결과, 유형별 템플릿, notify_admin_on_post · notify_author) → roboyam 권장안:
          · **운영자 알림**(신규 글·신고 접수)은 이미 설정 › 알림 › 이벤트·알림 도구가 맡는다 → 이 화면은 안내 + 링크만(중복하지 않는다).
          · 회원 알림 표: 새 댓글 · 답글 · 멘션 · 인기글 · 운영 처리 · 스텝·매니저 처리 · 신고 처리 결과 × 사이트 알림 / 이메일 / 회원이 끌 수 있음.
            기본: 사이트 알림 모두 켬, 이메일은 운영 처리만 켬. **운영 처리·스텝 처리는 회원이 끌 수 없다**(operator-permissions.md "알림은 처리 종류별로 사유와 함께 보내고 끌 수 없다") — 사이트 알림 켬·끌 수 없음으로 잠근다.
            그누보드의 답변글(답글형 글)은 roboyam 에 없어(답글은 댓글 1단계) 「답글(내 댓글에)」로 합쳤고, 멘션·인기글은 추가했다. 유형별 템플릿 편집은 후속(문구는 서버 고정).
          · 묶음(같은 글의 새 댓글을 10분 동안 하나로)·이메일 조용한 시간(22:00~08:00, 이메일만 미룸)·회원 알림 보관 90일(community 일반 설정 값 — roboyam 에는 아직 다른 자리가 없어 여기 둔다)은 추가.
        권한: 조회 `board.read`, 변경 `board.settings`. 서버 연결됨(`useBoardApi`) — 저장만 하고 알림 발송에 쓰이는 것은 후속이다.
    -->
    <div class="flex flex-col gap-4">
        <PageTitle :title="t('board.tabTitle.notifications')" :subtitle="t('board.tabSubtitle.notifications')" />

        <AppGadget v-if="tab.loading.value && !tab.response.value" :title="t('board.section.notifyMember')">
            <div class="flex flex-col gap-3" role="status" :aria-label="t('common.loading')">
                <Skeleton v-for="n in 4" :key="n" height="3rem" />
            </div>
        </AppGadget>

        <AppGadget v-else-if="!tab.response.value || !form" :title="t('board.tabTitle.notifications')">
            <ErrorState :title="t('board.tabLoadFailed')" :description="tab.loadError.value" retry :retrying="tab.loading.value" @retry="tab.load" />
        </AppGadget>

        <form v-else class="flex flex-col gap-4" novalidate @submit.prevent="tab.save">
            <p v-if="tab.response.value.updatedAt" class="-mt-2 text-xs text-muted-color">{{ t('board.lastSaved', { at: time.full(tab.response.value.updatedAt), by: tab.response.value.updatedBy ?? '—' }) }}</p>

            <!-- 회원 알림 기본값 표 -->
            <AppGadget :title="t('board.section.notifyMember')" anchor="notify-member" :changes="memberChanges">
                <p class="mb-1 text-xs leading-relaxed text-muted-color">{{ t('board.notifyMemberLead') }}</p>
                <p class="mb-3 flex items-center gap-1.5 text-xs text-muted-color">
                    <i class="pi pi-globe text-[11px]" aria-hidden="true" />
                    {{ t('board.notifyNoGuest') }}
                </p>
                <div class="overflow-x-auto">
                    <table class="w-full min-w-[34rem] border-collapse text-[13px]">
                        <thead>
                            <tr class="border-b border-(--line) text-left text-xs text-muted-color">
                                <th scope="col" class="py-2 pr-3 font-medium">{{ t('board.notifyCol.kind') }}</th>
                                <th v-for="col in COLUMNS" :key="col" scope="col" class="w-28 py-2 text-center font-medium">{{ t(`board.notifyCol.${col}`) }}</th>
                            </tr>
                        </thead>
                        <tbody>
                            <tr v-for="row in form.memberDefaults" :key="row.kind" class="border-b border-(--line) last:border-0">
                                <th scope="row" class="py-2 pr-3 text-left font-normal">
                                    <span class="block font-medium">{{ t(`board.notifyKind.${row.kind}`) }}</span>
                                    <span v-if="isLocked(row.kind)" class="flex items-center gap-1 text-[11px] text-muted-color">
                                        <i class="pi pi-lock text-[10px]" aria-hidden="true" />
                                        {{ t('board.notifyLockedReason') }}
                                    </span>
                                </th>
                                <td v-for="col in COLUMNS" :key="col" class="py-2 text-center">
                                    <Checkbox
                                        binary
                                        :model-value="row[col]"
                                        :disabled="!editable || (isLocked(row.kind) && col !== 'email')"
                                        :aria-label="t('board.notifyCellLabel', { kind: t(`board.notifyKind.${row.kind}`), col: t(`board.notifyCol.${col}`) })"
                                        @update:model-value="(v: boolean) => setCell(row.kind, col, v)"
                                    />
                                </td>
                            </tr>
                        </tbody>
                    </table>
                </div>
                <ul class="m-0 mt-4 flex list-none flex-col gap-1.5 border-t border-(--line) p-0 pt-3">
                    <li v-for="key in ['board.note.notifySanctionEmail', 'board.note.notifyMemberOverride']" :key="key" class="flex items-start gap-2 text-xs leading-relaxed text-muted-color">
                        <i class="pi pi-info-circle mt-0.5 text-[11px]" aria-hidden="true" />
                        <span>{{ t(key) }}</span>
                    </li>
                </ul>
            </AppGadget>

            <!-- 묶음·조용한 시간 -->
            <AppGadget :title="t('board.section.notifyGroup')" anchor="notify-group" :changes="tab.changesIn(GROUP)" :invalid="tab.invalidIn(GROUP)">
                <div class="grid gap-x-8 gap-y-5 md:grid-cols-2">
                    <BoardSettingField field="groupWindowMinutes" :model-value="form.groupWindowMinutes" :editable="editable" :error="tab.errorOf('groupWindowMinutes')" @update:model-value="(v: BoardFieldValue) => tab.set('groupWindowMinutes', v)" />
                    <div class="flex flex-col gap-3">
                        <BoardSettingField field="emailQuietHoursEnabled" :model-value="form.emailQuietHoursEnabled" :editable="editable" @update:model-value="(v: BoardFieldValue) => tab.set('emailQuietHoursEnabled', v)" />
                        <div class="flex flex-wrap items-center gap-2" role="group" :aria-label="t('board.quietRange')">
                            <InputText
                                v-for="(key, i) in CLOCKS"
                                :key="key"
                                :model-value="form[key]"
                                :aria-label="t(i === 0 ? 'board.quietStart' : 'board.quietEnd')"
                                inputmode="numeric"
                                placeholder="HH:mm"
                                :readonly="!editable"
                                :disabled="!form.emailQuietHoursEnabled"
                                :invalid="Boolean(tab.errorOf(key))"
                                class="w-24 text-center tabular-nums"
                                @update:model-value="(v: string | undefined) => tab.set(key, v ?? '')"
                            />
                        </div>
                        <p v-if="tab.errorOf('emailQuietStart') || tab.errorOf('emailQuietEnd')" role="alert" class="m-0 text-xs text-red-600 dark:text-red-400">{{ tab.errorOf('emailQuietStart') || tab.errorOf('emailQuietEnd') }}</p>
                        <p v-else class="m-0 text-xs text-muted-color">{{ t('board.quietHelp') }}</p>
                    </div>
                </div>
            </AppGadget>

            <!-- 보관 -->
            <AppGadget :title="t('board.section.notifyRetention')" anchor="notify-retention" :changes="tab.changesIn(['memberNotificationRetentionDays'])" :invalid="tab.invalidIn(['memberNotificationRetentionDays'])">
                <BoardSettingField field="memberNotificationRetentionDays" :model-value="form.memberNotificationRetentionDays" :editable="editable" :error="tab.errorOf('memberNotificationRetentionDays')" @update:model-value="(v: BoardFieldValue) => tab.set('memberNotificationRetentionDays', v)" />
            </AppGadget>

            <!-- 운영자 알림 — 안내만 -->
            <AppGadget :title="t('board.section.notifyOperator')" anchor="notify-operator">
                <p class="m-0 text-sm leading-relaxed">{{ t('board.notifyOperatorLead') }}</p>
                <ul class="m-0 mt-2 flex list-none flex-col gap-1 p-0 text-xs text-muted-color">
                    <li v-for="code in OPERATOR_EVENTS" :key="code"><code>{{ code }}</code> — {{ t(`board.operatorEvent.${code}`) }}</li>
                </ul>
                <NuxtLink to="/settings/notifications" class="mt-3 inline-flex items-center gap-1.5 text-sm font-medium text-primary-700 underline dark:text-primary-300">
                    <i class="pi pi-bell text-xs" aria-hidden="true" />
                    {{ t('board.openNotificationEvents') }}
                </NuxtLink>
            </AppGadget>

            <StickySaveBar v-if="tab.canEdit.value" submit revertable :dirty="tab.dirty.value" :saving="tab.saving.value" :disabled="!tab.canSave.value" :count="tab.changed.value.length" :error-count="tab.errorCount.value" :error="tab.formError.value" @revert="tab.revert" />
            <p v-else class="text-xs text-muted-color">{{ t('board.readOnlyNote') }}</p>
        </form>
    </div>
</template>

<script setup lang="ts">
import type { BoardMemberNotifyKind, BoardNotificationSettings } from '~/composables/board/api/useBoardApi.types';
import { BOARD_NOTIFY_LOCKED } from '~/composables/board/api/useBoardApi.types';
import type { BoardFieldValue, BoardTabForm } from '~/utils/util-board';
import UtilBoard from '~/utils/util-board';

const { t } = useI18n();
useSeoMeta({ title: () => t('board.tabTitle.notifications') });

const api = useBoardApi();
const time = useDisplayTime();

const COLUMNS = ['site', 'email', 'memberCanTurnOff'] as const;
const CLOCKS = ['emailQuietStart', 'emailQuietEnd'] as const;
const GROUP = ['groupWindowMinutes', 'emailQuietHoursEnabled', 'emailQuietStart', 'emailQuietEnd'] as const;
const OPERATOR_EVENTS = ['REPORT_RECEIVED', 'BOARD_SANCTION_REPEAT_REVIEW'] as const;

/** 조용한 시간 글자 검사 — 켜져 있을 때만 */
function validate(f: BoardTabForm<BoardNotificationSettings>): Record<string, string> {
    if (!f.emailQuietHoursEnabled) return {};
    const out: Record<string, string> = {};
    for (const key of CLOCKS) if (!UtilBoard.isClock(f[key])) out[key] = t('board.problemClock');
    return out;
}

const tab = useBoardDefaultsTab({ load: api.notifications, save: api.saveNotifications, validate, savedKey: 'board.tabSaved' });

const form = computed(() => tab.form.value);
const editable = computed(() => tab.canEdit.value && !tab.saving.value);
/** 표에서 바뀐 칸 수(줄·열 단위) */
const memberChanges = computed(() => {
    const saved = tab.response.value?.settings.memberDefaults ?? [];
    return (form.value?.memberDefaults ?? []).reduce((sum, row) => {
        const before = saved.find((r) => r.kind === row.kind);
        return sum + COLUMNS.filter((c) => before?.[c] !== row[c]).length;
    }, 0);
});

function isLocked(kind: BoardMemberNotifyKind): boolean {
    return BOARD_NOTIFY_LOCKED.includes(kind);
}

function setCell(kind: BoardMemberNotifyKind, col: (typeof COLUMNS)[number], value: boolean) {
    if (!form.value) return;
    tab.set(
        'memberDefaults',
        form.value.memberDefaults.map((r) => (r.kind === kind ? { ...r, [col]: value } : r)),
    );
}

onMounted(() => void tab.load());
</script>
