<template>
    <!-- 대화방 — 머리(‹ · 상대 · ⋯) · 법적 고지 · 말풍선 · 안내 · 입력창. 모바일은 화면 전체(안쪽 화면), 데스크톱(lg)은 쪽지함 오른쪽 칸.
         받은 쪽지 읽기·대화 삭제·차단은 보낼 수 없는 상태에서도 된다 -->
    <section class="flex min-h-0 flex-1 flex-col bg-card" :aria-label="t('dm.roomLabel')" data-testid="dm-room">
        <header class="flex h-12 shrink-0 items-center gap-1 border-b border-surface px-2">
            <NuxtLink
                :to="UtilDm.INBOX_PATH"
                class="flex size-10 shrink-0 items-center justify-center rounded-full hover:bg-surface-100 focus-visible:outline-2 focus-visible:outline-primary lg:hidden dark:hover:bg-surface-800"
                :aria-label="t('dm.backToInbox')"
                data-testid="dm-back"
            >
                <i class="pi pi-chevron-left" aria-hidden="true" />
            </NuxtLink>
            <h1 class="m-0 flex min-w-0 flex-1 justify-center truncate text-sm font-semibold lg:justify-start lg:px-3" data-testid="dm-room-title">
                <PostAuthorLabel v-if="thread" :author="UtilDm.partnerAuthor(thread.partner)" link strong />
                <span v-else>{{ t('dm.roomTitleUnknown') }}</span>
            </h1>
            <button
                type="button"
                class="flex size-10 shrink-0 items-center justify-center rounded-full hover:bg-surface-100 focus-visible:outline-2 focus-visible:outline-primary disabled:opacity-40 dark:hover:bg-surface-800"
                :aria-label="t('dm.menuOpen')"
                aria-haspopup="dialog"
                :disabled="!thread"
                data-testid="dm-menu"
                @click="sheetOpen = true"
            >
                <i class="pi pi-ellipsis-h" aria-hidden="true" />
            </button>
        </header>

        <!-- 법적 고지 — 늘 보인다 -->
        <DmNotice class="m-3 mb-0 shrink-0" data-testid="dm-room-notice">{{ t('dm.roomNotice') }}</DmNotice>

        <!-- 말풍선 -->
        <div
            ref="scroller"
            class="min-h-0 flex-1 overflow-y-auto overscroll-contain py-2"
            role="log"
            :aria-label="t('dm.roomLog')"
            aria-relevant="additions"
            :aria-busy="status === 'loading'"
            data-testid="dm-messages"
        >
            <div v-if="status === 'loading'" class="space-y-2 px-3.5 py-2" role="status" :aria-label="t('dm.roomLoading')" data-testid="dm-loading">
                <div v-for="n in 5" :key="n" class="flex" :class="n % 2 ? 'justify-start' : 'justify-end'" aria-hidden="true">
                    <div class="h-9 animate-pulse rounded-2xl bg-surface-200 dark:bg-surface-800" :class="SKELETON_WIDTH[n - 1]" />
                </div>
            </div>

            <ErrorState v-else-if="status === 'error'" :title="t('dm.roomLoadFailed')" :description="errorMessage" retry data-testid="dm-error" @retry="load" />

            <ErrorState v-else-if="status === 'notfound'" code="404" :title="t('dm.roomNotFoundTitle')" :description="t('dm.roomNotFoundText')" data-testid="dm-notfound">
                <Button as="router-link" :to="UtilDm.INBOX_PATH" :label="t('dm.toInbox')" />
            </ErrorState>

            <template v-else>
                <div v-if="hasOlder" class="pb-2 text-center">
                    <Button type="button" :label="t('dm.olderMore')" size="small" severity="secondary" variant="outlined" :loading="loadingOlder" data-testid="dm-older" @click="onLoadOlder" />
                    <p v-if="olderError" class="mt-2 px-4 text-xs text-red-600 dark:text-red-400" role="alert">{{ olderError }}</p>
                </div>

                <EmptyState v-if="rows.length === 0" icon="pi pi-comments" :title="t('dm.roomEmptyTitle')" :description="t('dm.roomEmptyText')" data-testid="dm-empty" />

                <ul v-else class="m-0 list-none space-y-1.5 p-0" data-testid="dm-timeline">
                    <li v-for="row in rows" :key="row.key">
                        <p v-if="row.kind === 'day'" class="mx-auto my-2.5 w-fit rounded-full bg-surface-100 px-2.5 py-0.5 text-center text-[0.6875rem] text-muted-color dark:bg-surface-800" data-testid="dm-day">{{ row.label }}</p>
                        <DmBubble v-else-if="row.kind === 'message'" :mine="row.message.mine" :body="row.message.body" :created-at="row.message.createdAt" />
                        <DmBubble
                            v-else
                            mine
                            failed
                            :body="row.failed.body"
                            :created-at="row.failed.createdAt"
                            :retrying="row.failed.retrying"
                            @resend="onResend(row.failed.key)"
                            @discard="removeFailed(row.failed.key)"
                        />
                    </li>
                </ul>

                <p v-if="thread?.partner.withdrawn" class="mx-auto my-2.5 w-fit rounded-full bg-surface-100 px-2.5 py-0.5 text-center text-[0.6875rem] text-muted-color dark:bg-surface-800" data-testid="dm-withdrawn">
                    {{ t('dm.withdrawnNote') }}
                </p>
            </template>
        </div>

        <!-- 입력창 위 안내 — 거절·전송 실패 · 링크·외부 연락처 -->
        <div v-if="notice || contactWarn || contactInfo" class="shrink-0 space-y-2 px-3 pt-2">
            <DmNotice v-if="notice" :tone="notice.tone" :title="notice.title" :text="notice.text" :link="notice.link" live dismissible data-testid="dm-notice" @dismiss="clearNotice" />
            <DmNotice v-if="contactWarn" tone="warn" :title="t('dm.contactWarnTitle')" :text="t('dm.contactWarnText')" live data-testid="dm-contact-warn" />
            <DmNotice v-else-if="contactInfo" data-testid="dm-contact-info">{{ t('dm.contactInfo') }}</DmNotice>
        </div>

        <DmCompose
            ref="composer"
            v-model="draft"
            :locked="locked"
            :lock-text="lockCopy?.lock ?? t('dm.lockUnknown')"
            :lock-link="lockCopy?.link ?? null"
            :disabled="status !== 'ready'"
            :sending="sending"
            :cooling="cooling"
            :invalid="contactWarn"
            @send="onSend"
            @focus="onComposeFocus"
        />

        <!-- ⋯ 시트 — 차단 · 대화 삭제 -->
        <DmActionSheet
            v-model:visible="sheetOpen"
            :title="sheetTitle"
            :nickname="thread?.partner.nickname ?? ''"
            :show-block="showBlock"
            :blocked="blockTarget?.blocked ?? false"
            @block="onBlock"
            @unblock="onUnblock"
            @remove="onRemove"
        />
    </section>
</template>

<script setup lang="ts">
import UtilBlock from '~/utils/util-block';
import UtilDm from '~/utils/util-dm';
import UtilDmContact from '~/utils/util-dm-contact';
import UtilDmMessages from '~/utils/util-dm-messages';

/**
 * 대화방 `/messages/{id}` — 상태·서버 호출·읽음 처리·보내기·주기 조회는 `useDmRoom`, 여기는 그리기·스크롤·시트·확인 대화상자(community `DmRoom`).
 *  - 열면 맨 아래로 스크롤하고, 「이전 쪽지 더 보기」는 보던 자리를 지킨다. 열려 있는 동안 약 10초마다 첫 쪽을 다시 받고(탭이 가려지면 멈춤) 맨 아래를 보고 있었다면 따라 내려간다.
 *  - 보내기: 칸을 바로 비우고 보낸다. 서버가 거절하면 쓰던 글을 되돌리고, 연결 끊김은 붉은 말풍선으로 남는다.
 *  - ⋯ 시트: 차단(확인 대화상자·실행 취소) · 대화 삭제(내 쪽지함에서만 — 확인 대화상자). community 의 쪽지 신고는 로보얌 신고 대상에 쪽지가 없어 뺐다.
 *  - 대화를 바꾸면 화면을 새로 만든다(`key` = 경로). 없는·남의 대화(`404 DM_THREAD_NOT_FOUND`)는 「대화를 찾을 수 없어요」.
 */
definePageMeta({ middleware: 'auth', shell: 'inner', key: (route) => route.path });
const { t } = useI18n();
const route = useRoute();
const router = useRouter();
const toast = useAppToast();
const time = useDisplayTime();
const { confirm } = useConfirmDialog();

const threadId = computed(() => String(route.params.id ?? ''));
const room = useDmRoom(() => threadId.value);
const { status, errorMessage, thread, messages, quota, hasOlder, loadingOlder, olderError, failed, sending, notice, canSend, cooling } = room;
const { load, refresh, loadOlder, send, resend, removeFailed, clearNotice } = room;

const { targetOf, block, unblock } = useUserActions({ onBlockChanged: () => void refresh() });

const scroller = ref<HTMLElement | null>(null);
const composer = ref<{ focus: () => void } | null>(null);
const sheetOpen = ref(false);
const draft = ref('');

const SKELETON_WIDTH = ['w-40', 'w-56', 'w-32', 'w-48', 'w-24'];

const rows = computed(() => UtilDm.timeline(messages.value, failed.value, time.value, (iso) => UtilDmMessages.dayLabel(t, iso, time.value)));

/** 입력창이 서버에 막혔는가 */
const locked = computed(() => status.value === 'ready' && !canSend.value);
const lockCopy = computed(() => {
    const th = thread.value;
    if (!th || !th.reason) return null;
    return UtilDmMessages.reason(t, th.reason, { quota: quota.value, availableFrom: th.availableFrom, time: time.value });
});

/** 상대가 답장하기 전이라 링크·외부 연락처를 못 보내는 상태 + 지금 쓴 글에 그것이 들어 있다 */
const contactWarn = computed(() => canSend.value && thread.value?.contactAllowed === false && UtilDmContact.has(draft.value));
const contactInfo = computed(() => status.value === 'ready' && canSend.value && thread.value?.contactAllowed === false);

/** ⋯ 시트의 차단 줄 — 탈퇴한 회원은 대상이 없고 운영진은 차단할 수 없다(이미 차단해 둔 사람이면 풀 수 있게) */
const blockTarget = computed(() => {
    const partner = thread.value?.partner;
    return partner ? targetOf({ id: partner.id, nickname: partner.nickname, role: partner.role, memo: partner.memo, withdrawn: partner.withdrawn }, false) : null;
});
const showBlock = computed(() => blockTarget.value !== null && (blockTarget.value.blocked || UtilBlock.canBlock(blockTarget.value.role)));
const sheetTitle = computed(() => (thread.value ? UtilDmMessages.sheetTitle(t, thread.value.partner) : t('dm.roomTitleUnknown')));

useSeoMeta({ title: () => UtilDmMessages.roomTitle(t, thread.value?.partner), robots: 'noindex,nofollow' });

/* ── 스크롤 ── */
function scrollToBottom() {
    const el = scroller.value;
    if (el) el.scrollTop = el.scrollHeight;
}

function nearBottom(): boolean {
    const el = scroller.value;
    return !el || UtilDm.isNearBottom(el.scrollHeight, el.scrollTop, el.clientHeight);
}

watch(status, async (value) => {
    if (value !== 'ready') return;
    await nextTick();
    scrollToBottom();
});

async function onLoadOlder() {
    const el = scroller.value;
    const before = el ? { height: el.scrollHeight, top: el.scrollTop } : null;
    const loaded = await loadOlder();
    if (!loaded || !el || !before) return;
    await nextTick();
    el.scrollTop = el.scrollHeight - before.height + before.top;
}

/** 키보드가 올라와 보이는 높이가 줄면 마지막 쪽지가 가려지니, 맨 아래를 보고 있었다면 다시 내려간다 */
function onComposeFocus() {
    if (!nearBottom()) return;
    setTimeout(scrollToBottom, 300);
}

/* ── 주기 조회 ── */
async function onPoll() {
    const stick = nearBottom();
    const { added, replaced } = await refresh();
    if ((added > 0 && stick) || replaced) {
        await nextTick();
        scrollToBottom();
    }
}
useDmPoll(onPoll, UtilDm.ROOM_POLL_MS);

/* ── 보내기 ── */
async function onSend() {
    const text = draft.value;
    draft.value = '';
    const outcome = await send(text);
    if (outcome === 'rejected') {
        if (draft.value === '') draft.value = text;
        return;
    }
    await nextTick();
    scrollToBottom();
    composer.value?.focus();
}

async function onResend(key: string) {
    const outcome = await resend(key);
    if (outcome === 'sent') {
        await nextTick();
        scrollToBottom();
    }
}

/* ── ⋯ 시트 ── */
async function onBlock() {
    if (blockTarget.value) await block(blockTarget.value);
}

async function onUnblock() {
    if (blockTarget.value) await unblock(blockTarget.value);
}

async function onRemove() {
    const ok = await confirm({ title: t('dm.deleteTitle'), message: t('dm.deleteMessage'), confirmLabel: t('common.delete'), tone: 'danger' });
    if (!ok) return;
    try {
        await room.remove();
    } catch (e) {
        toast.error(t('dm.deleteFailed', { reason: UtilDmMessages.errorOf(t, e) }), 6000);
        return;
    }
    toast.show(t('dm.deleteDone'));
    await router.replace(UtilDm.INBOX_PATH);
}

void load();
</script>
