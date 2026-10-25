<template>
    <!--
        토론 구획의 「고정 요청 N건」 — 확인 권한자(게시판 정책 `pinRequestReviewers` 의 글쓴이·게시판 매니저 — 서버가 판정해 `debate.pendingPinRequests` 를 숫자로 줄 때)만 보인다.
        줄마다 입장 배지·의견 쓴 회원·본문 앞부분 + [고정] · [거절](그 자리에서 사유를 펼침 — 0~100자 선택). 승인·거절하면 부르는 쪽이 댓글 목록을 다시 받아 대기 수가 줄고, 여기는 요청 목록을 다시 받는다.
        이미 처리됐거나(`PIN_REQUEST_DECIDED`) 의견이 사라졌으면(`OPINION_UNAVAILABLE`·`PIN_REQUEST_NOT_FOUND`) 서버 문구를 알리고 `retry` 로 목록을 맞춘다. 운영자는 관리자 의견 관리에서 처리한다
    -->
    <section class="flex flex-col gap-1.5 rounded-lg border border-amber-300 bg-surface-0 px-3 py-2.5 text-sm dark:border-amber-700/70 dark:bg-surface-900" :aria-labelledby="headingId" data-testid="debate-pin-reviews">
        <div class="flex flex-wrap items-baseline gap-x-2 gap-y-0.5">
            <h3 :id="headingId" class="m-0 text-sm font-semibold">{{ t('comment.pinReviewTitle', { n: count }) }}</h3>
            <span class="text-xs text-muted-color">{{ t('comment.pinReviewHint') }}</span>
        </div>

        <p v-if="loading && items.length === 0" class="m-0 text-xs text-muted-color" role="status">{{ t('comment.pinReviewLoading') }}</p>
        <p v-else-if="loadError" class="m-0 flex flex-wrap items-center gap-2 text-xs text-red-600 dark:text-red-400" role="alert">
            {{ loadError }}
            <Button type="button" :label="t('common.retry')" size="small" variant="text" @click="load" />
        </p>
        <p v-else-if="items.length === 0" class="m-0 text-xs text-muted-color">{{ t('comment.pinReviewEmpty') }}</p>

        <ul v-else class="m-0 flex list-none flex-col p-0">
            <li v-for="item in items" :key="item.id" class="flex min-w-0 flex-col gap-1.5 border-t border-surface py-2 first:border-t-0">
                <div class="flex min-w-0 flex-wrap items-center gap-1.5">
                    <DebateStanceBadge :stance="item.stance" />
                    <PostAuthorLabel :author="item.author" class="text-xs" />
                    <span class="min-w-0 flex-1 truncate text-xs text-muted-color" :title="item.excerpt">{{ t('comment.pinReviewExcerpt', { text: item.excerpt }) }}</span>
                    <div v-if="rejectingId !== item.id" class="ml-auto flex shrink-0 gap-1">
                        <Button type="button" :label="t('comment.pinApprove')" size="small" :loading="busyId === item.id" :disabled="Boolean(busyId)" @click="approve(item)" />
                        <Button type="button" :label="t('comment.pinReject')" size="small" severity="secondary" variant="outlined" :disabled="Boolean(busyId)" @click="openReject(item)" />
                    </div>
                </div>
                <!-- 거절 사유(선택) -->
                <form v-if="rejectingId === item.id" class="flex flex-col gap-1" novalidate @submit.prevent="reject(item)">
                    <div class="flex flex-wrap gap-2">
                        <InputText
                            v-model="reason"
                            size="small"
                            class="min-w-0 flex-1"
                            :maxlength="REASON_MAX"
                            :placeholder="t('comment.pinRejectReason')"
                            :aria-label="t('comment.pinRejectReason')"
                            :invalid="Boolean(reasonError)"
                            autofocus
                        />
                        <Button type="submit" :label="t('comment.pinRejectSubmit')" size="small" severity="danger" :loading="busyId === item.id" />
                        <Button type="button" :label="t('common.cancel')" size="small" severity="secondary" variant="text" @click="rejectingId = null" />
                    </div>
                    <p v-if="reasonError" role="alert" class="m-0 text-xs text-red-600 dark:text-red-400">{{ reasonError }}</p>
                </form>
            </li>
        </ul>
    </section>
</template>

<script setup lang="ts">
import type { PinRequestReviewItem } from '~/composables/board/api/useDebateApi.types';
import type { DebateActions } from '~/components/board/CommentSection.types';
import UtilBoardMessages from '~/utils/util-board-messages';

interface Props {
    /** 대기 수(`debate.pendingPinRequests`) — 바뀌면 요청 목록을 다시 받는다 */
    count: number;
    actions: DebateActions;
}

const props = withDefaults(defineProps<Props>(), {});

const emit = defineEmits<{ retry: [] }>();

/** 거절 사유 글자 수 한도(서버 0~100자) */
const REASON_MAX = 100;
/** 이미 처리됐거나 대상이 사라진 거절 — 서버 문구를 알리고 목록을 맞춘다 */
const STALE_CODES: ReadonlySet<string> = new Set(['PIN_REQUEST_DECIDED', 'PIN_REQUEST_NOT_FOUND', 'OPINION_UNAVAILABLE', 'PIN_REQUEST_FORBIDDEN']);

const { t } = useI18n();
const toast = useAppToast();
const headingId = useId();

const items = ref<PinRequestReviewItem[]>([]);
const loading = ref(false);
const loadError = ref('');
const busyId = ref('');
const rejectingId = ref<string | null>(null);
const reason = ref('');
const reasonError = ref('');

let request = 0;
async function load(): Promise<void> {
    const mine = ++request;
    loading.value = true;
    loadError.value = '';
    try {
        const result = await props.actions.pinRequests();
        if (mine !== request) return;
        items.value = result.items;
    } catch (e) {
        if (mine !== request) return;
        loadError.value = UtilBoardMessages.of(t, e);
    } finally {
        if (mine === request) loading.value = false;
    }
}

watch(
    () => props.count,
    () => void load(),
    { immediate: true },
);

function openReject(item: PinRequestReviewItem): void {
    rejectingId.value = item.id;
    reason.value = '';
    reasonError.value = '';
}

/** 처리 실패 — 이미 처리·사라진 요청이면 알리고 목록을 맞춘다, 그 밖은 토스트 */
function fail(e: unknown): void {
    toast.error(UtilBoardMessages.of(t, e), 8000);
    if (e instanceof ApiError && STALE_CODES.has(e.code)) {
        rejectingId.value = null;
        emit('retry');
        void load();
    }
}

async function approve(item: PinRequestReviewItem): Promise<void> {
    if (busyId.value) return;
    busyId.value = item.id;
    try {
        await props.actions.approvePin(item.id);
        toast.show(t('comment.pinApproved'), { tone: 'success' });
        void load();
    } catch (e) {
        fail(e);
    } finally {
        busyId.value = '';
    }
}

async function reject(item: PinRequestReviewItem): Promise<void> {
    if (busyId.value) return;
    const text = reason.value.trim();
    if (text.length > REASON_MAX) {
        reasonError.value = t('comment.pinRejectTooLong', { max: REASON_MAX });
        return;
    }
    busyId.value = item.id;
    reasonError.value = '';
    try {
        await props.actions.rejectPin(item.id, text || null);
        rejectingId.value = null;
        toast.show(t('comment.pinRejectedToast'), { tone: 'success' });
        void load();
    } catch (e) {
        const field = e instanceof ApiError ? e.field('reason') : null;
        if (field) reasonError.value = field;
        else fail(e);
    } finally {
        busyId.value = '';
    }
}
</script>
