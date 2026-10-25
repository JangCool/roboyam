<template>
    <div class="flex flex-col gap-3">
        <h1 class="text-lg font-bold">{{ t('settings.blocks') }}</h1>

        <!-- 탭 둘 — 닉네임 차단 목록(해제) · 차단 단어(추가·삭제). 개수는 탭 이름에 붙는다 -->
        <div role="tablist" :aria-label="t('block.tabsLabel')" class="flex border-b border-surface">
            <button
                v-for="key in TABS"
                :id="`${uid}-tab-${key}`"
                :key="key"
                type="button"
                role="tab"
                :aria-selected="tab === key"
                :aria-controls="`${uid}-panel-${key}`"
                :tabindex="tab === key ? 0 : -1"
                class="-mb-px min-h-11 flex-1 border-b-2 py-2.5 text-center text-sm focus-visible:outline-2 focus-visible:-outline-offset-2 focus-visible:outline-primary"
                :class="tab === key ? 'border-primary font-semibold text-color' : 'border-transparent text-muted-color hover:text-color'"
                :data-testid="`blocks-tab-${key}`"
                @click="tab = key"
                @keydown.left.prevent="moveTab(-1)"
                @keydown.right.prevent="moveTab(1)"
            >
                {{ tabLabel(key) }}
            </button>
        </div>

        <!-- ── 닉네임 ── -->
        <section v-show="tab === 'users'" :id="`${uid}-panel-users`" role="tabpanel" :aria-labelledby="`${uid}-tab-users`">
            <div v-if="usersStatus === 'loading'" class="space-y-2" role="status" :aria-label="t('block.usersLoading')" data-testid="blocks-users-loading">
                <div v-for="n in 3" :key="n" class="h-14 animate-pulse rounded-xl bg-surface-200 dark:bg-surface-800" />
            </div>

            <ErrorState v-else-if="usersStatus === 'error'" :title="t('block.usersLoadFailed')" :description="usersError" retry data-testid="blocks-users-error" @retry="loadUsers()" />

            <template v-else>
                <p class="m-0 rounded-lg bg-surface-100 px-3 py-2.5 text-xs leading-relaxed text-muted-color dark:bg-surface-800/60" role="note">{{ t('block.usersNotice') }}</p>

                <EmptyState v-if="users.length === 0" :title="t('block.usersEmptyTitle')" :description="t('block.usersEmptyText')" icon="pi pi-user-minus" data-testid="blocks-users-empty" />

                <template v-else>
                    <ul class="m-0 mt-3 list-none rounded-xl border border-surface bg-surface-0 p-0 dark:bg-surface-900" data-testid="blocks-users-list">
                        <li v-for="row in users" :key="row.id" class="flex min-h-14 items-center gap-3 border-b border-surface px-4 py-2.5 last:border-b-0" data-testid="blocks-user">
                            <div class="min-w-0 flex-1">
                                <p class="m-0 flex min-w-0 items-center gap-1.5 text-sm">
                                    <StaffBadge v-if="row.role !== 'USER'" :role="row.role" class="shrink-0" />
                                    <GradeBadge v-else :grade="row.grade as Grade" :label="row.gradeLabel" class="shrink-0" />
                                    <NuxtLink :to="UtilProfile.path(row.id)" class="truncate rounded font-medium hover:underline focus-visible:outline-2 focus-visible:outline-primary">{{ row.nickname }}</NuxtLink>
                                </p>
                                <small class="block text-xs text-muted-color">{{ UtilBlockMessages.blockedAt(t, row.blockedAt, time) }}</small>
                            </div>
                            <Button
                                type="button"
                                :label="t('block.unblock')"
                                size="small"
                                severity="secondary"
                                variant="outlined"
                                class="shrink-0"
                                :loading="unblockingId === row.id"
                                :disabled="unblockingId !== null"
                                :aria-label="t('block.unblockLabel', { nickname: row.nickname })"
                                data-testid="blocks-unblock"
                                @click="onUnblock(row)"
                            />
                        </li>
                    </ul>

                    <div v-if="usersHasNext" class="py-4 text-center">
                        <Button type="button" :label="t('common.more')" severity="secondary" variant="outlined" :loading="loadingMoreUsers" data-testid="blocks-users-more" @click="loadMoreUsers" />
                        <p v-if="moreUsersError" class="mt-2 text-xs text-red-600 dark:text-red-400" role="alert">{{ moreUsersError }}</p>
                    </div>
                    <p class="mt-3 text-center text-xs text-muted-color">{{ t('block.usersHelp') }}</p>
                </template>
            </template>
        </section>

        <!-- ── 단어 ── -->
        <section v-show="tab === 'words'" :id="`${uid}-panel-words`" role="tabpanel" :aria-labelledby="`${uid}-tab-words`">
            <div v-if="wordsStatus === 'loading'" class="space-y-2" role="status" :aria-label="t('block.wordsLoading')" data-testid="blocks-words-loading">
                <div class="h-10 animate-pulse rounded bg-surface-200 dark:bg-surface-800" />
                <div v-for="n in 3" :key="n" class="h-14 animate-pulse rounded-xl bg-surface-200 dark:bg-surface-800" />
            </div>

            <ErrorState v-else-if="wordsStatus === 'error'" :title="t('block.wordsLoadFailed')" :description="wordsError" retry data-testid="blocks-words-error" @retry="loadWords()" />

            <template v-else>
                <p class="m-0 rounded-lg bg-surface-100 px-3 py-2.5 text-xs leading-relaxed text-muted-color dark:bg-surface-800/60" role="note">{{ t('block.wordsNotice') }}</p>

                <!-- 단어 입력 — 글자 수는 보내기 전에 알려 주고, 서버 오류(중복·상한)는 서버 문구 그대로 -->
                <form class="mt-3" novalidate :aria-label="t('block.wordFormLabel')" @submit.prevent="addWord">
                    <div class="flex gap-2">
                        <InputText
                            :id="wordInputId"
                            v-model="wordInput"
                            name="word"
                            autocomplete="off"
                            class="min-w-0 flex-1"
                            :placeholder="t('block.wordPlaceholder')"
                            :aria-label="t('block.wordInputLabel')"
                            :invalid="Boolean(shownWordError)"
                            :aria-describedby="wordDescribedBy"
                            :disabled="adding"
                            data-testid="blocks-word-input"
                        />
                        <Button type="submit" :label="t('common.add')" class="shrink-0" :loading="adding" :disabled="!canAdd" data-testid="blocks-word-add" />
                    </div>
                    <p v-if="shownWordError" :id="wordErrorId" role="alert" class="mt-1.5 text-xs text-red-600 dark:text-red-400" data-testid="blocks-word-error">{{ shownWordError }}</p>
                    <p :id="wordHelpId" class="mt-1.5 text-xs text-muted-color" data-testid="blocks-word-help">
                        {{ t('block.wordHelp', { min: limits.min, max: limits.max, limit: limits.limit, count: words.length }) }}
                    </p>
                </form>

                <EmptyState v-if="words.length === 0" :title="t('block.wordsEmptyTitle')" :description="t('block.wordsEmptyText')" icon="pi pi-filter-slash" data-testid="blocks-words-empty" />

                <ul v-else class="m-0 mt-3 list-none rounded-xl border border-surface bg-surface-0 p-0 dark:bg-surface-900" data-testid="blocks-words-list">
                    <li v-for="row in words" :key="row.id" class="flex min-h-14 items-center gap-3 border-b border-surface px-4 py-2.5 last:border-b-0" data-testid="blocks-word">
                        <p class="m-0 min-w-0 flex-1 break-words text-sm font-medium [overflow-wrap:anywhere]">{{ row.word }}</p>
                        <Button
                            type="button"
                            :label="t('common.delete')"
                            size="small"
                            severity="secondary"
                            variant="outlined"
                            class="shrink-0"
                            :loading="removingWordId === row.id"
                            :disabled="removingWordId !== null"
                            :aria-label="t('block.wordRemoveLabel', { word: row.word })"
                            data-testid="blocks-word-remove"
                            @click="removeWord(row)"
                        />
                    </li>
                </ul>
            </template>
        </section>
    </div>
</template>

<script setup lang="ts">
import type { Grade } from '~/composables/auth/api/useAuthApi.types';
import type { BlockedUserView, BlockedWordView } from '~/composables/block/api/useBlockApi.types';
import { ApiError } from '~/composables/common/useApiClient';
import UtilBlock from '~/utils/util-block';
import UtilBlockMessages from '~/utils/util-block-messages';
import UtilBoardMessages from '~/utils/util-board-messages';
import UtilProfile from '~/utils/util-profile';

/**
 * 차단 관리 `/settings/blocks` — 로그인 필요(community `pages/settings/blocks.vue`, `docs/api-block-scrap.md`).
 * 탭 둘 — **닉네임**(차단한 회원 목록·해제·더 보기) · **단어**(추가·삭제). 탭 이름에 개수가 붙는다.
 *  - 닉네임 해제는 확인 없이 바로 풀고 토스트의 **실행 취소**로 되돌린다(`useUserActions` — 프로필 ⋯ 메뉴와 같은 흐름).
 *  - 단어는 글자 수(2~20자)를 보내기 전에 알려 주고, 같은 단어·50개 초과 같은 서버 오류는 서버 문구 그대로 입력칸 아래에.
 *  - 두 목록은 따로 불러온다 — 한쪽이 실패해도 다른 쪽은 그대로 쓴다.
 * 단어 차단으로 글·댓글을 접는 판정은 서버가 한다(글 줄·댓글의 `blocked: 'WORD'`) — 단어를 바꾼 뒤 다시 받은 목록부터 반영된다.
 */
definePageMeta({ middleware: 'auth' });
const { t } = useI18n();
const { siteLabel } = useSiteName();
useSeoMeta({ title: () => t('settings.blocks'), description: () => t('block.blocksSeo', { site: siteLabel.value }), robots: 'noindex' });

const api = useBlockApi();
const toast = useAppToast();
const time = useDisplayTime();
const uid = useId();

type TabKey = 'users' | 'words';
const TABS: readonly TabKey[] = ['users', 'words'];
const tab = ref<TabKey>('users');

function tabLabel(key: TabKey): string {
    const count = key === 'users' ? usersTotal.value : wordsStatus.value === 'ready' ? words.value.length : null;
    const name = key === 'users' ? t('block.tabUsers') : t('block.tabWords');
    return count === null ? name : t('block.tabCount', { name, count });
}

async function moveTab(step: number) {
    const next = TABS[(TABS.indexOf(tab.value) + step + TABS.length) % TABS.length]!;
    tab.value = next;
    await nextTick();
    document.getElementById(`${uid}-tab-${next}`)?.focus();
}

/* ── 닉네임 ── */
type Status = 'loading' | 'ready' | 'error';
const usersStatus = ref<Status>('loading');
const usersError = ref('');
const users = ref<BlockedUserView[]>([]);
const usersTotal = ref<number | null>(null);
const usersHasNext = ref(false);
const usersNext = ref<string | null>(null);
const loadingMoreUsers = ref(false);
const moreUsersError = ref('');
const unblockingId = ref<string | null>(null);

let usersRequest = 0;
async function loadUsers(silent = false) {
    const mine = ++usersRequest;
    if (!silent) usersStatus.value = 'loading';
    moreUsersError.value = '';
    try {
        const res = await api.users();
        if (mine !== usersRequest) return;
        users.value = res.items;
        usersTotal.value = res.total;
        usersHasNext.value = res.hasNext && res.nextCursor !== null;
        usersNext.value = res.nextCursor;
        usersStatus.value = 'ready';
    } catch (e) {
        if (mine !== usersRequest) return;
        if (silent && usersStatus.value === 'ready') return;
        usersError.value = UtilBoardMessages.of(t, e);
        usersStatus.value = 'error';
    }
}

async function loadMoreUsers() {
    if (loadingMoreUsers.value || !usersHasNext.value || usersNext.value === null) return;
    const mine = usersRequest;
    loadingMoreUsers.value = true;
    moreUsersError.value = '';
    try {
        const res = await api.users({ cursor: usersNext.value });
        if (mine !== usersRequest) return;
        const have = new Set(users.value.map((u) => u.id));
        users.value = [...users.value, ...res.items.filter((u) => !have.has(u.id))];
        usersTotal.value = res.total;
        usersHasNext.value = res.hasNext && res.nextCursor !== null;
        usersNext.value = res.nextCursor;
    } catch (e) {
        if (mine !== usersRequest) return;
        moreUsersError.value = t('block.moreFailed', { reason: UtilBoardMessages.of(t, e) });
    } finally {
        if (mine === usersRequest) loadingMoreUsers.value = false;
    }
}

/** 차단 상태가 바뀌었다 — 풀었으면 줄을 빼고, 다시 걸었으면(실행 취소) 목록을 조용히 다시 받는다 */
function onBlockChanged(userId: string, blocked: boolean) {
    if (blocked) {
        void loadUsers(true);
        return;
    }
    if (users.value.some((u) => u.id === userId)) {
        users.value = users.value.filter((u) => u.id !== userId);
        usersTotal.value = Math.max(0, (usersTotal.value ?? 1) - 1);
    }
}

const { unblock } = useUserActions({ onBlockChanged });

async function onUnblock(row: BlockedUserView) {
    if (unblockingId.value !== null) return;
    unblockingId.value = row.id;
    try {
        await unblock({ id: row.id, nickname: row.nickname, role: row.role, memo: null, blocked: true });
    } finally {
        unblockingId.value = null;
    }
}

/* ── 단어 ── */
const wordsStatus = ref<Status>('loading');
const wordsError = ref('');
const words = ref<BlockedWordView[]>([]);
const limits = reactive({ limit: UtilBlock.WORD_LIMIT, min: UtilBlock.WORD_MIN, max: UtilBlock.WORD_MAX });
const wordInput = ref('');
const adding = ref(false);
const serverWordError = ref('');
const removingWordId = ref<string | null>(null);

const wordInputId = `${uid}-word`;
const wordErrorId = `${uid}-word-error`;
const wordHelpId = `${uid}-word-help`;

const wordProblem = computed(() => UtilBlock.wordProblem(wordInput.value, limits.min, limits.max));
const shownWordError = computed(() => UtilBlockMessages.wordProblem(t, wordProblem.value, limits.min, limits.max) || serverWordError.value);
const canAdd = computed(() => !adding.value && wordProblem.value === null);
const wordDescribedBy = computed(() => (shownWordError.value ? `${wordErrorId} ${wordHelpId}` : wordHelpId));

watch(wordInput, () => (serverWordError.value = ''));

let wordsRequest = 0;
async function loadWords() {
    const mine = ++wordsRequest;
    wordsStatus.value = 'loading';
    try {
        const res = await api.words();
        if (mine !== wordsRequest) return;
        words.value = res.items;
        limits.limit = res.limit;
        limits.min = res.minLength;
        limits.max = res.maxLength;
        wordsStatus.value = 'ready';
    } catch (e) {
        if (mine !== wordsRequest) return;
        wordsError.value = UtilBoardMessages.of(t, e);
        wordsStatus.value = 'error';
    }
}

async function addWord() {
    if (!canAdd.value) return;
    adding.value = true;
    serverWordError.value = '';
    try {
        const created = await api.addWord(UtilBlock.normalizeWord(wordInput.value));
        words.value = [created, ...words.value.filter((w) => w.id !== created.id)];
        wordInput.value = '';
        toast.success(t('block.wordAdded'));
    } catch (e) {
        serverWordError.value = e instanceof ApiError ? (e.field('word') ?? e.message) : UtilBoardMessages.of(t, e);
    } finally {
        adding.value = false;
    }
}

async function removeWord(row: BlockedWordView) {
    if (removingWordId.value !== null) return;
    removingWordId.value = row.id;
    try {
        await api.removeWord(row.id);
        words.value = words.value.filter((w) => w.id !== row.id);
        toast.show(t('block.wordRemoved', { word: row.word }));
    } catch (e) {
        toast.error(UtilBlockMessages.failure(t, t('block.wordRemoveFailed'), e), 6000);
    } finally {
        removingWordId.value = null;
    }
}

void loadUsers();
void loadWords();
onBeforeUnmount(() => {
    usersRequest++;
    wordsRequest++;
});
</script>
