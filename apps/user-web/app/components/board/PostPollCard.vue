<template>
    <!--
        글 상세의 투표 카드(post-poll-contract 「화면」) — 본문 아래, 찬반 토론 위. 머리: 「투표」 · 마감 알약 · 복수 선택 알약 · 마감 시각(없으면 「마감 없음」).
        투표 전: 질문 · 선택지(단일 = 라디오, 복수 = 체크) → [투표하기]. 「다시 투표 허용」이 꺼진 투표는 확인창(「이 선택으로 투표할까요? 한 번 투표하면 바꿀 수 없어요」, 토론 투표 확인창과 같은 `useConfirmDialog`).
        투표 뒤·마감 뒤(`resultsVisible`): 결과 막대 · 퍼센트(득표 / 참여한 사람 수 — 복수는 합이 100 을 넘을 수 있다) · 득표 수 · 내 선택 표시 · 「N명 참여」.
          `changeAllowed` 이고 지금 투표할 수 있으면 [다시 투표] → 지금 선택을 고른 채 선택지로 돌아가 [이 선택으로 다시 투표] · [취소].
        결과 비공개(투표 전·마감 전)면 「투표하면 결과를 볼 수 있어요」. 투표할 수 없으면 `voteBlockedReason` 안내(로그인 필요면 [로그인]).
        글쓴이이고 첫 표 전(`editable`)이면 [투표 지우기](확인창 → `DELETE …/poll`).
        서버 호출은 페이지가 내려 준 `actions` 로 한다. `409 ALREADY_VOTED`·`POLL_CLOSED`·`POLL_LOCKED`·`403` 은 서버 문구 토스트 후 다시 읽기, `404 POLL_NOT_FOUND` 는 카드를 치운다(`update:poll` null),
          `400 fields.optionIds` 는 선택지 아래 한 줄.
    -->
    <section class="my-2 flex flex-col gap-3 rounded-xl border border-surface bg-surface-50 p-3.5 dark:bg-surface-900/60" :aria-labelledby="titleId" :aria-busy="busy" data-testid="post-poll">
        <div class="flex flex-wrap items-center gap-x-2 gap-y-1">
            <h2 :id="titleId" class="m-0 flex items-center gap-1.5 text-[0.9375rem] font-semibold">
                <i class="pi pi-chart-bar text-sm text-muted-color" aria-hidden="true" />{{ t('poll.title') }}
            </h2>
            <span v-if="poll.closed" class="inline-flex items-center justify-center rounded-full bg-surface-200 px-2 py-1 text-[0.6875rem] font-semibold leading-none text-surface-700 dark:bg-surface-700 dark:text-surface-100" data-testid="poll-closed-tag">
                <span class="relative top-[-0.09em]">{{ t('poll.closedTag') }}</span>
            </span>
            <span v-if="poll.multiple" class="inline-flex items-center justify-center rounded-full border border-surface px-2 py-1 text-[0.6875rem] leading-none text-muted-color">
                <span class="relative top-[-0.09em]">{{ t('poll.multipleTag') }}</span>
            </span>
            <span class="text-xs text-muted-color" data-testid="poll-closes">{{ closeText }}</span>
        </div>

        <p class="m-0 break-words text-[0.9375rem] font-semibold leading-snug" data-testid="poll-question">{{ poll.question }}</p>

        <!-- ═══ 결과 ═══ -->
        <template v-if="rows && !changing">
            <ul class="m-0 flex list-none flex-col gap-2 p-0" data-testid="poll-results">
                <li v-for="row in rows" :key="row.option.id" class="flex flex-col gap-1">
                    <div class="flex items-baseline justify-between gap-2 text-sm">
                        <span class="flex min-w-0 items-baseline gap-1.5 break-words" :class="row.mine ? 'font-semibold' : ''">
                            <i v-if="row.mine" class="pi pi-check-circle text-xs text-primary" aria-hidden="true" />
                            <span>{{ row.option.label }}</span>
                            <span v-if="row.mine" class="sr-only">{{ t('poll.mineLabel') }}</span>
                        </span>
                        <span class="shrink-0 text-xs tabular-nums text-muted-color">{{ t('poll.resultLine', { p: row.percent, n: row.votes }) }}</span>
                    </div>
                    <div class="h-2 overflow-hidden rounded-full bg-surface-200 dark:bg-surface-700" aria-hidden="true">
                        <div class="h-full rounded-full transition-[width] duration-200" :class="row.mine ? 'bg-primary' : 'bg-surface-400 dark:bg-surface-500'" :style="{ width: `${row.percent}%` }" />
                    </div>
                </li>
            </ul>
            <div class="flex flex-wrap items-center justify-between gap-2">
                <p class="m-0 text-sm" data-testid="poll-voters">{{ t('poll.voters', { n: (poll.voterCount ?? 0).toLocaleString() }) }}</p>
                <Button v-if="canRevote" type="button" icon="pi pi-replay" :label="t('poll.revote')" size="small" severity="secondary" variant="outlined" data-testid="poll-revote" @click="startChange" />
            </div>
            <p v-if="resultHint" class="m-0 text-xs leading-relaxed text-muted-color">{{ resultHint }}</p>
        </template>

        <!-- ═══ 선택지(투표 전 · 다시 투표) ═══ -->
        <template v-else>
            <div class="flex flex-col gap-1.5" role="group" :aria-labelledby="titleId" :aria-describedby="helpId">
                <label
                    v-for="option in poll.options"
                    :key="option.id"
                    class="flex items-start gap-2.5 rounded-lg border px-3 py-2.5 text-sm"
                    :class="[isSelected(option.id) ? 'border-primary bg-surface-0 dark:bg-surface-900' : 'border-surface bg-surface-0 dark:bg-surface-900', inputsDisabled ? 'cursor-default opacity-80' : 'cursor-pointer hover:bg-surface-100 dark:hover:bg-surface-800']"
                >
                    <Checkbox
                        v-if="poll.multiple"
                        :model-value="isSelected(option.id)"
                        binary
                        :disabled="inputsDisabled"
                        class="mt-px"
                        @update:model-value="toggle(option.id, Boolean($event))"
                    />
                    <RadioButton
                        v-else
                        :model-value="selected[0] ?? null"
                        :value="option.id"
                        :name="groupName"
                        :disabled="inputsDisabled"
                        class="mt-px"
                        @update:model-value="pick(String($event))"
                    />
                    <span class="min-w-0 break-words">{{ option.label }}</span>
                </label>
            </div>
            <p v-if="choiceError" role="alert" class="m-0 text-xs text-red-600 dark:text-red-400" data-testid="poll-choice-error">{{ choiceError }}</p>

            <div v-if="poll.canVote" class="flex flex-wrap gap-2">
                <Button
                    type="button"
                    :label="changing ? t('poll.revoteSubmit') : t('poll.vote')"
                    icon="pi pi-check"
                    size="small"
                    :loading="busy"
                    :disabled="!submittable"
                    data-testid="poll-vote"
                    @click="submit"
                />
                <Button v-if="changing" type="button" :label="t('common.cancel')" size="small" severity="secondary" variant="text" :disabled="busy" @click="changing = false" />
            </div>

            <div :id="helpId" class="flex flex-col gap-1 text-xs leading-relaxed text-muted-color">
                <p v-if="blockedText" class="m-0 flex flex-wrap items-center gap-x-2 text-color" data-testid="poll-blocked">
                    <span>{{ blockedText }}</span>
                    <Button v-if="poll.voteBlockedReason === 'LOGIN_REQUIRED'" type="button" :label="t('common.login')" size="small" variant="text" @click="prompt('vote')" />
                </p>
                <p v-if="!poll.resultsVisible && !poll.closed" class="m-0">{{ t('poll.resultsHidden') }}</p>
                <p v-if="poll.canVote && !poll.changeAllowed" class="m-0 font-medium text-amber-700 dark:text-amber-300">{{ t('poll.changeLockedNote') }}</p>
            </div>
        </template>

        <div class="flex flex-wrap items-center justify-between gap-2 border-t border-surface pt-2">
            <p class="m-0 text-xs text-muted-color">{{ t('poll.anonymous') }}</p>
            <Button v-if="poll.editable" type="button" icon="pi pi-trash" :label="t('poll.remove')" size="small" severity="danger" variant="text" :disabled="busy" data-testid="poll-remove" @click="onRemove" />
        </div>

        <!-- 투표했을 때 스크린리더에 알린다(눌렀던 단추가 사라지면 초점도 사라지므로) -->
        <p class="sr-only" role="status" aria-live="polite">{{ announce }}</p>
    </section>
</template>

<script setup lang="ts">
import type { PollView } from '~/composables/board/api/usePollApi.types';
import type { PollCardActions } from '~/components/board/PostPollCard.types';
import UtilBoardMessages from '~/utils/util-board-messages';
import UtilPoll from '~/utils/util-poll';
import UtilPollMessages from '~/utils/util-poll-messages';

interface Props {
    poll: PollView;
    actions: PollCardActions;
}

const props = withDefaults(defineProps<Props>(), {});
const emit = defineEmits<{
    /** 투표·다시 읽기의 새 투표 — 페이지가 글의 `poll` 을 바꾼다. null 이면 투표가 없어졌다(지움·404) */
    'update:poll': [poll: PollView | null];
}>();

/** 지금 상태와 어긋난 거절 — 서버 문구를 알리고 투표를 다시 읽는다 */
const REFRESH_CODES: ReadonlySet<string> = new Set(['ALREADY_VOTED', 'POLL_CLOSED', 'POLL_LOCKED']);

const { t } = useI18n();
const time = useDisplayTime();
const toast = useAppToast();
const { confirm } = useConfirmDialog();
const { prompt } = useLoginPrompt();
const titleId = useId();
const helpId = useId();
const groupName = useId();

/** 고른 선택지 id — 단일 선택이면 0~1개 */
const selected = ref<string[]>([]);
/** [다시 투표]를 눌러 선택지로 돌아왔다 */
const changing = ref(false);
const busy = ref(false);
const choiceError = ref('');
const announce = ref('');

const rows = computed(() => UtilPoll.rows(props.poll));
/** 선택지를 누를 수 없다 — 투표할 수 없거나 보내는 중 */
const inputsDisabled = computed(() => !props.poll.canVote || busy.value);
const canRevote = computed(() => props.poll.voted && props.poll.changeAllowed && props.poll.canVote);
const submittable = computed(() => {
    if (busy.value || !UtilPoll.selectionOk(props.poll, selected.value)) return false;
    // 다시 투표에서 같은 선택은 보낼 것이 없다
    return !(changing.value && UtilPoll.sameSelection(UtilPoll.ordered(props.poll, selected.value), props.poll.myOptionIds));
});
const blockedText = computed(() => (props.poll.canVote ? '' : UtilPollMessages.blocked(t, props.poll.voteBlockedReason)));

/** 머리의 마감 글자 */
const closeText = computed(() => {
    const at = props.poll.closesAt;
    if (!at) return t('poll.noClose');
    return props.poll.closed ? t('poll.closedAt', { at: time.value.full(at) }) : t('poll.closesAt', { at: time.value.full(at) });
});

/** 결과 아래 안내 — 마감 · 바꿀 수 없음 */
const resultHint = computed(() => {
    if (props.poll.closed) return props.poll.voted ? '' : t('poll.closedResults');
    if (props.poll.voted && !props.poll.changeAllowed) return t('poll.votedLocked');
    return '';
});

// 투표가 새로 오면 — 다시 투표를 마치고, 사라진 선택지를 고른 채로 두지 않는다
watch(
    () => props.poll,
    (next) => {
        changing.value = false;
        selected.value = UtilPoll.ordered(next, selected.value);
    },
);
watch(selected, () => {
    choiceError.value = '';
});

function isSelected(id: string): boolean {
    return selected.value.includes(id);
}

function pick(id: string): void {
    selected.value = [id];
}

function toggle(id: string, on: boolean): void {
    selected.value = on ? [...selected.value.filter((x) => x !== id), id] : selected.value.filter((x) => x !== id);
}

function startChange(): void {
    selected.value = [...props.poll.myOptionIds];
    changing.value = true;
}

async function submit(): Promise<void> {
    if (busy.value) return;
    const p = props.poll;
    if (!p.canVote) {
        if (p.voteBlockedReason === 'LOGIN_REQUIRED') prompt('vote');
        return;
    }
    if (!UtilPoll.selectionOk(p, selected.value)) {
        choiceError.value = p.multiple ? t('poll.chooseAtLeastOne') : t('poll.chooseOne');
        return;
    }
    if (!p.changeAllowed) {
        const ok = await confirm({ title: t('poll.confirmTitle'), message: t('poll.confirmMessage'), confirmLabel: t('poll.confirmLabel') });
        if (!ok) return;
    }
    const revoting = p.voted;
    busy.value = true;
    try {
        const next = await props.actions.vote(UtilPoll.ordered(p, selected.value));
        selected.value = [];
        emit('update:poll', next);
        const done = revoting ? t('poll.revoted') : t('poll.voted');
        toast.success(done);
        announce.value = next.resultsVisible && next.voterCount !== null ? `${done} ${t('poll.voters', { n: next.voterCount.toLocaleString() })}` : done;
    } catch (e) {
        await failed(e);
    } finally {
        busy.value = false;
    }
}

async function onRemove(): Promise<void> {
    if (busy.value) return;
    const ok = await confirm({ title: t('poll.removeTitle'), message: t('poll.removeMessage'), confirmLabel: t('poll.remove'), tone: 'danger' });
    if (!ok) return;
    busy.value = true;
    try {
        await props.actions.remove();
        emit('update:poll', null);
        toast.success(t('poll.removed'));
    } catch (e) {
        await failed(e);
    } finally {
        busy.value = false;
    }
}

/** 투표·지우기 실패 — 코드로 가른다(서버 문구는 그대로) */
async function failed(e: unknown): Promise<void> {
    if (!(e instanceof ApiError)) {
        toast.error(UtilBoardMessages.of(t, e), 8000);
        return;
    }
    if (e.code === 'UNAUTHENTICATED') {
        prompt('vote');
        return;
    }
    if (e.code === 'POLL_NOT_FOUND') {
        toast.error(e.message, 8000);
        emit('update:poll', null);
        return;
    }
    const choice = e.field('optionIds');
    if (choice) {
        choiceError.value = choice;
        return;
    }
    toast.error(UtilBoardMessages.of(t, e), 8000);
    // 그새 마감·잠김·이미 투표·자격이 바뀌었다 — 지금 상태로
    if (REFRESH_CODES.has(e.code) || e.status === 403) await refresh();
}

async function refresh(): Promise<void> {
    try {
        emit('update:poll', await props.actions.reload());
    } catch (e) {
        if (e instanceof ApiError && e.code === 'POLL_NOT_FOUND') emit('update:poll', null);
    }
}
</script>
