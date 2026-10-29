<template>
    <!--
        소모임 일정 상세 `/groups/{id}/events/{eventId}`(community `pages/groups/[id]/events/[eventId]/index.vue`, `docs/api-groups.md` 9절·「화면 (SPA) — 6-3」). **멤버만**.
        머리(진행 방식·상태·제목) → 일시 → 진행(온라인: 채팅 탭 링크에서 만나요)/장소(오프라인) → 만든 사람 → 설명 → (취소 사유) → (오프라인 개인 책임 고지 `offlineNotice`) →
        내 참석 여부 세 단추(누르면 바로 저장, 「불참으로 표시했어요 · 실행 취소」) → 집계 → 참석자(최신순, 더보기).
        관리(`canManage` — 모임장·`EVENTS` 매니저, 시작 전·진행 중)는 ⋯ 메뉴에서 수정(→ 수정 화면)과 취소(사유 선택, 확인 시트).
        신고: 오프라인 고지 아래 「신고하기」가 소모임 신고 시트(`BoardReportDialog` target `GROUP` · `useGroupReport`)를 연다(모임장 제외).
          일정 응답에는 소모임 이름·모임장 여부가 없어, 고지가 보일 때만 소모임 상세를 한 번 받아 쓴다(community 와 같다). 못 받으면 이름 없이 신고한다(모임장이면 서버가 거절).
        상태: 로딩 · 비회원(로그인 안내, 요청 없음) · 멤버 아님(잠금) · 일정 없음(404) · 소모임 없음/비활성 · 오류(다시 시도) · 열림.
    -->
    <div class="mx-auto max-w-2xl" data-testid="event-page">
        <div class="-mx-4 -mt-4 mb-3 flex items-center gap-1 border-b border-surface px-2 py-1.5 md:hidden">
            <Button type="button" icon="pi pi-angle-left" variant="text" severity="secondary" :aria-label="t('common.back')" @click="goBack" />
            <span class="min-w-0 flex-1 text-sm font-semibold">{{ t('groupEvent.detailTitle') }}</span>
            <Button v-if="canManage" type="button" icon="pi pi-ellipsis-h" variant="text" severity="secondary" rounded :aria-label="t('groupEvent.menuLabel')" aria-haspopup="dialog" @click="menuOpen = true" />
        </div>

        <header class="flex min-h-11 items-center justify-between gap-2 max-md:hidden">
            <NuxtLink :to="tabPath" class="rounded text-xs text-muted-color hover:underline focus-visible:outline-2 focus-visible:outline-primary">‹ {{ t('groupEvent.backToTab') }}</NuxtLink>
            <Button v-if="canManage" type="button" icon="pi pi-ellipsis-h" variant="text" severity="secondary" rounded :aria-label="t('groupEvent.menuLabel')" aria-haspopup="dialog" data-testid="event-menu" @click="menuOpen = true" />
        </header>

        <div v-if="view === 'loading'" class="mt-3 space-y-3" role="status" :aria-label="t('common.loading')" data-testid="event-loading">
            <div class="h-6 w-2/3 animate-pulse rounded bg-surface-200 dark:bg-surface-800" />
            <div class="h-24 animate-pulse rounded-xl bg-surface-200 dark:bg-surface-800" />
            <div class="h-14 animate-pulse rounded-xl bg-surface-200 dark:bg-surface-800" />
        </div>

        <StatusPanel v-else-if="view === 'login'" icon="pi pi-lock" tone="info" :title="t('groupEvent.loginTitle')" class="mt-6" data-testid="event-login">
            {{ t('groupEvent.loginText') }}
            <template #actions>
                <Button as="router-link" :to="UtilRedirect.toLogin(route.fullPath)" :label="t('common.login')" fluid />
            </template>
        </StatusPanel>

        <EmptyState v-else-if="view === 'locked'" :title="t('groupEvent.lockedTitle')" :description="t('groupEvent.lockedText')" icon="pi pi-lock" data-testid="event-locked">
            <Button as="router-link" :to="groupPath" :label="t('group.backToGroup')" />
        </EmptyState>

        <ErrorState v-else-if="view === 'event-notfound'" code="404" :title="t('groupEvent.notFoundTitle')" :description="t('groupEvent.notFoundText')" data-testid="event-notfound">
            <Button as="router-link" :to="tabPath" :label="t('groupEvent.backToTab')" />
        </ErrorState>

        <ErrorState v-else-if="view === 'group-notfound'" code="404" :title="t('group.notFoundTitle')" :description="t('group.notFoundText')" data-testid="event-group-gone">
            <Button as="router-link" :to="UtilGroup.LIST_PATH" :label="t('group.backToList')" />
        </ErrorState>

        <ErrorState v-else-if="view === 'group-inactive'" :title="t('group.inactiveTitle')" :description="t('group.inactiveText')" data-testid="event-group-inactive">
            <Button as="router-link" :to="UtilGroup.LIST_PATH" :label="t('group.backToList')" />
        </ErrorState>

        <ErrorState v-else-if="view === 'error'" :title="t('groupEvent.gateEventError')" :description="errorMessage" retry data-testid="event-error" @retry="load">
            <Button as="router-link" :to="tabPath" :label="t('groupEvent.backToTab')" severity="secondary" variant="outlined" />
        </ErrorState>

        <article v-else-if="detail" :aria-labelledby="titleId" data-testid="event-detail">
            <!-- 머리 — 진행 방식 · 상태 · 제목 -->
            <div class="mt-1 flex flex-wrap items-center gap-1.5">
                <GroupTag :tone="modeTag.tone" data-testid="event-mode">{{ modeTag.label }}</GroupTag>
                <GroupTag v-if="stateTag" :tone="stateTag.tone" data-testid="event-state">{{ stateTag.label }}</GroupTag>
            </div>
            <h1 :id="titleId" class="mt-1.5 break-words text-lg font-bold leading-snug [overflow-wrap:anywhere]" :class="detail.state === 'CANCELED' ? 'text-muted-color line-through' : ''" data-testid="event-title">{{ detail.title }}</h1>

            <!-- 일시 · 진행(온라인) / 장소(오프라인) · 만든 사람 -->
            <dl class="m-0 mt-3 panel-card text-sm">
                <div class="flex gap-3 border-b border-surface px-4 py-2.5">
                    <dt class="w-16 shrink-0 text-muted-color">{{ t('groupEvent.whenLabel') }}</dt>
                    <dd class="m-0 min-w-0 flex-1 break-words" data-testid="event-when"><time :datetime="detail.startsAt">{{ when }}</time></dd>
                </div>
                <div class="flex gap-3 px-4 py-2.5" :class="detail.creator ? 'border-b border-surface' : ''">
                    <dt class="w-16 shrink-0 text-muted-color">{{ detail.mode === 'ONLINE' ? t('groupEvent.howLabel') : t('groupEvent.placeLabel') }}</dt>
                    <dd class="m-0 min-w-0 flex-1 break-words [overflow-wrap:anywhere]" data-testid="event-where">
                        <template v-if="detail.mode === 'ONLINE'">
                            {{ t('groupEvent.onlineHow') }}
                            <NuxtLink :to="UtilGroupEvent.chatTabPath(id)" class="ml-1 whitespace-nowrap rounded text-primary hover:underline focus-visible:outline-2 focus-visible:outline-primary">{{ t('groupEvent.chatLink') }} ›</NuxtLink>
                        </template>
                        <template v-else>{{ detail.place }}</template>
                    </dd>
                </div>
                <div v-if="detail.creator" class="flex items-center gap-3 px-4 py-2.5">
                    <dt class="w-16 shrink-0 text-muted-color">{{ t('groupEvent.creatorLabel') }}</dt>
                    <dd class="m-0 flex min-w-0 flex-1"><PostAuthorLabel :author="UtilGroup.authorOf(detail.creator, t('group.withdrawn'))" link /></dd>
                </div>
            </dl>

            <p v-if="detail.description" class="m-0 mt-3 whitespace-pre-line break-words text-sm leading-relaxed [overflow-wrap:anywhere]" data-testid="event-description">{{ detail.description }}</p>

            <GroupNotice v-if="detail.state === 'CANCELED' && detail.cancelReason" class="mt-3" tone="warn" :title="t('groupEvent.cancelReasonTitle')" data-testid="event-cancel-note">{{ detail.cancelReason }}</GroupNotice>

            <!-- 오프라인 개인 책임 고지 — 오프라인 일정이거나 오프라인 모임이면 늘 보인다 -->
            <template v-if="detail.offlineNotice">
                <GroupNotice class="mt-4" tone="warn" :title="t('groupEvent.offlineNoticeTitle')" data-testid="event-offline-notice">{{ t('groupEvent.offlineNoticeText') }}</GroupNotice>
                <p v-if="reportable" class="m-0 mt-1.5 text-xs text-muted-color" data-testid="event-report-line">
                    {{ t('groupEvent.reportLead') }}
                    <button type="button" class="ml-1 rounded text-primary hover:underline focus-visible:outline-2 focus-visible:outline-primary" data-testid="event-report" @click="report.open">{{ t('groupEvent.reportLink') }}</button>
                </p>
            </template>

            <!-- 내 참석 여부 — 하나만 눌린다(`aria-pressed`, 눌린 단추에는 체크 — 색만으로 가르지 않는다). 닫힌 일정은 단추를 끄고 까닭을 적는다 -->
            <section class="mt-5" data-testid="event-response">
                <p :id="`${uid}-response`" class="text-sm font-semibold">{{ t('groupEvent.responseLabel') }}</p>
                <div role="group" :aria-labelledby="`${uid}-response`" :aria-describedby="`${uid}-response-note`" :aria-busy="saving" class="mt-1.5 grid grid-cols-3 gap-2">
                    <button
                        v-for="option in responseOptions"
                        :key="option.value"
                        type="button"
                        class="inline-flex min-h-11 items-center justify-center gap-1 rounded-lg border px-3 text-sm focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-60"
                        :class="[
                            detail.myResponse === option.value ? 'border-primary bg-primary font-semibold text-primary-contrast' : 'border-surface bg-surface-0 hover:bg-surface-100 dark:bg-surface-900 dark:hover:bg-surface-800',
                            saving ? 'cursor-progress' : '',
                        ]"
                        :aria-pressed="detail.myResponse === option.value"
                        :aria-disabled="saving || undefined"
                        :disabled="!detail.canRespond"
                        :data-testid="`event-response-${option.value}`"
                        @click="respond(option.value)"
                    >
                        <i v-if="detail.myResponse === option.value" class="pi pi-check text-xs" aria-hidden="true" />
                        <span>{{ option.label }}</span>
                    </button>
                </div>
                <p :id="`${uid}-response-note`" class="mt-2 text-xs text-muted-color">{{ detail.canRespond ? t('groupEvent.responseHelp') : UtilGroupEventMessages.responseClosed(t, detail.state) }}</p>
                <p class="m-0 mt-3 text-sm tabular-nums" data-testid="event-counts">{{ UtilGroupEventMessages.countsLine(t, detail.counts) }}</p>
            </section>

            <!-- 참석자 — 등급 뱃지 + 닉네임만. 모임장·나에게 작은 표시 -->
            <section class="mt-5" :aria-labelledby="`${uid}-attendees`" :aria-busy="attendees.status.value === 'loading'" data-testid="event-attendees">
                <h2 :id="`${uid}-attendees`" class="text-sm font-semibold">
                    {{ t('groupEvent.attendeesHeading', { n: detail.counts.going }) }}
                    <small class="ml-1 text-xs font-normal text-muted-color">{{ t('groupEvent.attendeesHint') }}</small>
                </h2>
                <div v-if="attendees.status.value === 'loading'" class="mt-2" aria-hidden="true">
                    <div v-for="n in 3" :key="n" class="border-b border-surface py-3">
                        <div class="h-3.5 animate-pulse rounded bg-surface-200 dark:bg-surface-800" :class="n % 2 ? 'w-2/5' : 'w-1/3'" />
                    </div>
                </div>
                <!-- 참석자만 못 받았다 — 일정과 내 응답은 그대로 보인다 -->
                <div v-else-if="attendees.status.value === 'error'" class="mt-2 rounded-lg bg-surface-100 px-3 py-3 text-sm dark:bg-surface-800" role="alert">
                    <p class="m-0 font-semibold">{{ t('groupEvent.attendeesError') }}</p>
                    <p v-if="attendees.errorMessage.value" class="m-0 mt-0.5 text-xs text-muted-color">{{ attendees.errorMessage.value }}</p>
                    <Button type="button" :label="t('common.retry')" size="small" severity="secondary" variant="outlined" class="mt-2" @click="attendees.load" />
                </div>
                <template v-else>
                    <p v-if="attendees.items.value.length === 0" class="m-0 mt-2 rounded-lg bg-surface-100 px-3 py-3 text-sm text-muted-color dark:bg-surface-800">{{ t('groupEvent.attendeesEmpty') }}</p>
                    <ul v-else class="m-0 mt-1 list-none p-0">
                        <li v-for="(item, index) in attendees.items.value" :key="item.user.id ?? `withdrawn-${index}`" class="flex min-h-11 items-center gap-1.5 border-b border-surface py-2 last:border-b-0" data-testid="event-attendee">
                            <PostAuthorLabel :author="UtilGroup.authorOf(item.user, t('group.withdrawn'))" link />
                            <GroupTag v-if="item.leader" tone="amber">{{ t('groupEvent.leaderTag') }}</GroupTag>
                            <GroupTag v-if="isMe(item.user.id)" tone="gray">{{ t('groupEvent.meTag') }}</GroupTag>
                        </li>
                    </ul>
                    <div v-if="attendees.hasNext.value" class="py-3 text-center">
                        <Button type="button" :label="t('common.more')" severity="secondary" variant="outlined" :loading="attendees.loadingMore.value" @click="attendees.loadMore" />
                        <p v-if="attendees.moreError.value" class="mt-2 text-xs text-red-600 dark:text-red-400" role="alert">{{ attendees.moreError.value }}</p>
                    </div>
                </template>
            </section>
        </article>

        <!-- 소모임 신고 시트 -->
        <BoardReportDialog
            v-if="detail && reportable"
            v-model:visible="report.visible.value"
            target="GROUP"
            :summary="reportInfo?.name ? t('group.reportTarget', { name: reportInfo.name }) : ''"
            :stock="reportInfo?.stock ?? false"
            :loading="report.loading.value"
            :error="report.error.value"
            :field-error="report.fieldError.value"
            @submit="report.submit"
        />

        <!-- 관리: ⋯ 메뉴(수정 · 취소) -->
        <AppSheet v-if="detail && canManage" v-model:visible="menuOpen" :title="t('groupEvent.menuTitle')">
            <ul class="m-0 mt-3 list-none p-0">
                <li>
                    <NuxtLink :to="UtilGroupEvent.editPath(id, eventId)" :class="SHEET_ITEM" data-testid="event-menu-edit" @click="menuOpen = false">{{ t('groupEvent.menuEdit') }}</NuxtLink>
                </li>
                <li>
                    <button type="button" :class="[SHEET_ITEM, 'text-red-600 dark:text-red-400']" data-testid="event-menu-cancel" @click="openCancel">{{ t('groupEvent.menuCancel') }}</button>
                </li>
            </ul>
            <div class="px-5 pt-3">
                <Button type="button" :label="t('common.close')" severity="secondary" variant="outlined" fluid @click="menuOpen = false" />
            </div>
        </AppSheet>

        <!-- 관리: 취소 확인 — 사유(선택, 100자). 시트를 닫는 단추는 「돌아가기」라 「취소」가 두 뜻이 되지 않는다 -->
        <AppSheet v-if="detail" v-model:visible="cancelOpen" :title="t('groupEvent.cancelTitle')" :description="t('groupEvent.cancelText', { title: detail.title })">
            <form class="mt-3 px-5" novalidate @submit.prevent="onCancel">
                <LabeledField :label="t('groupEvent.cancelReasonLabel')" :hint="t('group.optional')" :help="t('groupEvent.cancelReasonHelp')" :error="cancelShownError">
                    <template #aside>
                        <span class="text-xs tabular-nums" :class="cancelTooLong ? 'text-red-600 dark:text-red-400' : 'text-muted-color'">{{ t('group.counter', { n: cancelLength, max: UtilGroupEvent.CANCEL_REASON_MAX }) }}</span>
                    </template>
                    <template #default="{ id: inputId, describedBy, invalid }">
                        <Textarea
                            :id="inputId"
                            v-model="cancelReason"
                            rows="3"
                            fluid
                            class="resize-none"
                            :placeholder="t('groupEvent.cancelReasonPlaceholder')"
                            :invalid="invalid"
                            :disabled="cancelBusy"
                            :aria-describedby="describedBy"
                            data-testid="event-cancel-reason"
                        />
                    </template>
                </LabeledField>
                <div class="mt-4 flex gap-2">
                    <Button type="button" :label="t('groupEvent.cancelBack')" severity="secondary" variant="outlined" class="flex-1" :disabled="cancelBusy" @click="cancelOpen = false" />
                    <Button type="submit" :label="t('groupEvent.cancelConfirm')" severity="danger" class="flex-1" :loading="cancelBusy" :disabled="cancelBusy || cancelTooLong" data-testid="event-cancel-confirm" />
                </div>
            </form>
        </AppSheet>
    </div>
</template>

<script setup lang="ts">
import UtilGroup from '~/utils/util-group';
import UtilGroupEvent from '~/utils/util-group-event';
import UtilGroupEventMessages from '~/utils/util-group-event-messages';
import UtilRedirect from '~/utils/util-redirect';

definePageMeta({ shell: 'inner' });

const { t } = useI18n();
const route = useRoute();
const router = useRouter();
const uid = useId();
const titleId = `${uid}-title`;
const time = useDisplayTime();
const { user } = useAuth();

const id = computed(() => String(route.params.id ?? ''));
const eventId = computed(() => String(route.params.eventId ?? ''));
const groupPath = computed(() => UtilGroup.detailPath(id.value));
const tabPath = computed(() => UtilGroupEvent.tabPath(id.value));

const { detail, view, errorMessage, saving, cancelBusy, cancelError, load, respond, cancel } = useGroupEventDetail({
    groupId: id,
    eventId,
    // 내가 참석으로 응답하거나 참석을 거두면 참석자 목록에서 내 줄이 생기고 사라진다 — 첫 쪽을 조용히 다시 받는다
    onResponseChanged: (prev, next) => {
        if (prev === 'GOING' || next === 'GOING') void attendees.reload();
    },
});
// 참석자는 일정 상세를 받은 뒤(멤버임이 확인된 뒤)에만 받는다
const attendees = useGroupEventAttendees({ groupId: id, eventId, enabled: computed(() => view.value === 'ready') });

const modeTag = computed(() => UtilGroupEventMessages.modeTag(t, detail.value?.mode ?? 'ONLINE'));
const stateTag = computed(() => (detail.value ? UtilGroupEventMessages.stateTag(t, detail.value.state) : null));
const when = computed(() => UtilGroupEvent.when(detail.value?.startsAt, detail.value?.endsAt, time.value));
const responseOptions = computed(() => UtilGroupEventMessages.responseOptions(t));
/** 수정·취소 — 서버가 시작 전·진행 중이고 관리 권한이 있을 때만 true 로 준다 */
const canManage = computed(() => view.value === 'ready' && detail.value?.canManage === true);

function isMe(userId: string | null): boolean {
    return Boolean(userId && user.value && userId === user.value.id);
}

/* ── 관리: 메뉴 · 취소 ── */
const SHEET_ITEM = 'block w-full border-b border-surface px-5 py-3 text-left text-sm hover:bg-surface-100 dark:hover:bg-surface-800';
const menuOpen = ref(false);
const cancelOpen = ref(false);
const cancelReason = ref('');
/** 서버 오류를 숨긴다 — 사유를 고쳤다 */
const cancelDismissed = ref(false);
const cancelLength = computed(() => UtilGroupEvent.length(cancelReason.value.trim()));
const cancelTooLong = computed(() => cancelLength.value > UtilGroupEvent.CANCEL_REASON_MAX);
const cancelShownError = computed(() => {
    if (cancelTooLong.value) return t('groupEvent.cancelReasonTooLong', { max: UtilGroupEvent.CANCEL_REASON_MAX });
    return cancelDismissed.value ? '' : cancelError.value;
});

watch(cancelOpen, (open) => {
    if (!open) return;
    cancelReason.value = '';
    cancelDismissed.value = false;
});
watch(cancelReason, () => (cancelDismissed.value = true));

function openCancel(): void {
    menuOpen.value = false;
    cancelOpen.value = true;
}

/** 「일정 취소하기」 — 취소했거나 그 사이 상태가 달라졌으면(토스트로 알린다) 시트를 닫는다. 그 밖의 오류는 시트 안에 남는다 */
async function onCancel(): Promise<void> {
    if (cancelBusy.value || cancelTooLong.value) return;
    cancelDismissed.value = false;
    if ((await cancel(cancelReason.value)) !== 'failed') cancelOpen.value = false;
}

/* ── 소모임 신고(오프라인 고지 아래 「신고하기」) ── */
const groupApi = useGroupApi();
/** 신고 시트에 쓰는 소모임 정보 — 이름·투자 고지 소모임인가(사유 목록)·내가 모임장인가. 고지가 보일 때만 소모임 상세에서 받는다. 못 받아도 신고는 된다(이름 없이) */
const reportInfo = ref<{ name: string; stock: boolean; leader: boolean } | null>(null);
const wantsReportInfo = computed(() => view.value === 'ready' && detail.value?.offlineNotice === true);
let infoSeq = 0;
watch(
    [wantsReportInfo, id],
    async ([wants, groupId]) => {
        const mine = ++infoSeq;
        reportInfo.value = null;
        if (!wants || !groupId) return;
        try {
            const group = await groupApi.detail(groupId);
            if (mine === infoSeq) reportInfo.value = { name: group.name, stock: group.stockNotice === true, leader: group.myState === 'LEADER' || group.myRole === 'LEADER' };
        } catch {
            if (mine === infoSeq) reportInfo.value = { name: '', stock: false, leader: false };
        }
    },
    { immediate: true },
);
onBeforeUnmount(() => infoSeq++);
/** 「신고하기」를 보일까 — 소모임 정보를 받았고 모임장이 아니다(매니저는 신고할 수 있다) */
const reportable = computed(() => reportInfo.value !== null && !reportInfo.value.leader);
const report = useGroupReport({ groupId: id, onGone: () => void load() });

function goBack(): void {
    if (typeof window !== 'undefined' && window.history.state?.back) router.back();
    else void router.push(tabPath.value);
}

useSeoMeta({ title: () => detail.value?.title ?? t('groupEvent.detailTitle'), robots: 'noindex' });
</script>
