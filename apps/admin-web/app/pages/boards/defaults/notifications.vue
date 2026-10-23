<template>
    <!--
        게시판 기본 설정 › 알림 — **회원 알림에 같이 쓰는 값**(묶는 시간 · 메일 조용한 시간 · 보관 일수). 자기 요청·자기 저장(`GET·PUT /api/admin/boards/defaults/notifications`, 하단 StickySaveBar).
          · 이벤트별 칸(옛 `memberDefaults` — 새 댓글·답글·멘션·인기글·운영 처리·스텝 처리·신고 결과 × 사이트 알림 / 이메일 / 회원이 끌 수 있음)은 서버 V58 이 회원 이벤트로 옮겼다
            (`docs/features/message-template-contract.md` 「서버 구현 결과(회원 이벤트·이메일 확장)」) → 이 탭은 안내 + 「알림·이벤트 › 이벤트 › 회원」 링크만(`notification.read` 가 있을 때. 없으면 안내만).
          · **운영자 알림**(신규 글·신고 접수)은 설정 › 알림 › 이벤트·알림 도구가 맡는다 → 안내 + 링크만(중복하지 않는다).
          · 묶음(같은 글의 새 댓글을 10분 동안 하나로) · 이메일 조용한 시간(22:00~08:00, 이메일만 미룸) · 회원 알림 보관 90일(community 일반 설정 값 — roboyam 에는 아직 다른 자리가 없어 여기 둔다).
        권한: 조회 `board.read`, 변경 `board.settings`. 서버 연결됨(`useBoardApi`) — 조용한 시간은 저장만 되고 적용 지점은 후속이다(회원 이메일 발송 단계).
    -->
    <div class="flex flex-col gap-4">
        <PageTitle :title="t('board.tabTitle.notifications')" :subtitle="t('board.tabSubtitle.notifications')" />

        <AppGadget v-if="tab.loading.value && !tab.response.value" :title="t('board.section.notifyGroup')">
            <div class="flex flex-col gap-3" role="status" :aria-label="t('common.loading')">
                <Skeleton v-for="n in 3" :key="n" height="3rem" />
            </div>
        </AppGadget>

        <AppGadget v-else-if="!tab.response.value || !form" :title="t('board.tabTitle.notifications')">
            <ErrorState :title="t('board.tabLoadFailed')" :description="tab.loadError.value" retry :retrying="tab.loading.value" @retry="tab.load" />
        </AppGadget>

        <form v-else class="flex flex-col gap-4" novalidate @submit.prevent="tab.save">
            <p v-if="tab.response.value.updatedAt" class="-mt-2 text-xs text-muted-color">{{ t('board.lastSaved', { at: time.full(tab.response.value.updatedAt), by: tab.response.value.updatedBy ?? '—' }) }}</p>

            <!-- 회원 이벤트별 알림 — 회원 이벤트 화면으로 옮겼다(안내 + 링크) -->
            <AppGadget :title="t('board.section.notifyMember')" anchor="notify-member">
                <p class="m-0 text-sm leading-relaxed">{{ t('board.notifyMemberMoved') }}</p>
                <NuxtLink v-if="can('notification.read')" to="/settings/notifications/member" class="mt-3 inline-flex items-center gap-1.5 text-sm font-medium text-primary-700 underline dark:text-primary-300">
                    <i class="pi pi-bell text-xs" aria-hidden="true" />
                    {{ t('board.notifyMemberLink') }}
                </NuxtLink>
                <p v-else class="m-0 mt-2 text-xs text-muted-color">{{ t('board.notifyMemberNoAccess') }}</p>
                <p class="m-0 mt-3 flex items-center gap-1.5 text-xs text-muted-color">
                    <i class="pi pi-globe text-[11px]" aria-hidden="true" />
                    {{ t('board.notifyNoGuest') }}
                </p>
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
                <NuxtLink v-if="can('notification.read')" to="/settings/notifications/operator" class="mt-3 inline-flex items-center gap-1.5 text-sm font-medium text-primary-700 underline dark:text-primary-300">
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
import type { BoardNotificationSettings } from '~/composables/board/api/useBoardApi.types';
import type { BoardFieldValue, BoardTabForm } from '~/utils/util-board';
import UtilBoard from '~/utils/util-board';

const { t } = useI18n();
useSeoMeta({ title: () => t('board.tabTitle.notifications') });

const api = useBoardApi();
const time = useDisplayTime();
const { can } = useAdminPermission();

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

onMounted(() => void tab.load());
</script>
