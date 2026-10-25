<template>
    <!-- 받는 사람 고르기 — 닉네임 검색(2~16자) → 결과(등급 뱃지+닉네임 · 「처음 대화하는 상대예요」/「이미 대화 중이에요」 · 선택/대화 열기). 고르면 칩, ✕ 로 다시 고른다.
         나·탈퇴한 회원·차단 관계의 회원은 서버가 결과에서 뺀다 -->
    <div data-testid="dm-recipient">
        <div v-if="selected" class="flex min-h-11 items-center gap-2 rounded-lg border border-surface px-3 py-1.5" data-testid="dm-recipient-selected">
            <p class="m-0 flex min-w-0 flex-1 items-center text-sm"><PostAuthorLabel :author="UtilDm.partnerAuthor(selected)" /></p>
            <Button type="button" icon="pi pi-times" severity="secondary" variant="text" rounded size="small" :aria-label="t('dm.recipientChange')" data-testid="dm-recipient-clear" @click="$emit('clear')" />
        </div>

        <div v-else-if="loading" class="h-11 animate-pulse rounded-lg bg-surface-200 dark:bg-surface-800" role="status" :aria-label="t('dm.recipientChecking')" data-testid="dm-recipient-loading" />

        <template v-else>
            <form class="flex gap-2" role="search" :aria-label="t('dm.recipientSearchLabel')" novalidate @submit.prevent="run">
                <InputText
                    :id="inputId"
                    v-model="query"
                    name="recipient"
                    autocomplete="off"
                    class="min-w-0 flex-1"
                    :placeholder="t('dm.searchPlaceholder')"
                    :aria-label="t('dm.recipientInputLabel')"
                    :invalid="Boolean(problemText)"
                    :aria-describedby="problemText ? errorId : undefined"
                    data-testid="dm-recipient-input"
                />
                <Button type="submit" :label="t('common.search')" severity="secondary" class="shrink-0" :loading="status === 'loading'" data-testid="dm-recipient-search" />
            </form>
            <p v-if="problemText" :id="errorId" class="mt-1.5 text-xs text-red-600 dark:text-red-400" role="alert" data-testid="dm-recipient-error">{{ problemText }}</p>

            <div role="status" aria-live="polite">
                <p v-if="status === 'error'" class="mt-2 text-sm text-red-600 dark:text-red-400" data-testid="dm-recipient-fail">{{ failure }}</p>

                <template v-else-if="status === 'ready'">
                    <p v-if="results.length === 0" class="mt-3 text-sm text-muted-color" data-testid="dm-recipient-empty">{{ t('dm.searchEmpty', { q: searched }) }}</p>
                    <template v-else>
                        <p class="mb-1 mt-3 text-xs font-semibold text-muted-color">{{ t('dm.searchResults') }}</p>
                        <ul class="m-0 list-none rounded-lg border border-surface p-0" data-testid="dm-recipient-results">
                            <li v-for="row in results" :key="row.user.id ?? row.user.nickname" class="flex min-h-14 items-center gap-3 border-b border-surface px-3 py-2 last:border-b-0">
                                <div class="min-w-0 flex-1">
                                    <p class="m-0 flex min-w-0 items-center text-sm"><PostAuthorLabel :author="UtilDm.partnerAuthor(row.user)" /></p>
                                    <small class="block text-xs text-muted-color">{{ row.threadId ? t('dm.recipientExisting') : t('dm.recipientNew') }}</small>
                                </div>
                                <Button
                                    v-if="row.threadId"
                                    as="router-link"
                                    :to="UtilDm.threadPath(row.threadId)"
                                    :label="t('dm.openThread')"
                                    size="small"
                                    severity="secondary"
                                    variant="outlined"
                                    class="shrink-0"
                                    :aria-label="t('dm.openThreadWith', { nickname: row.user.nickname })"
                                    data-testid="dm-recipient-open"
                                />
                                <Button
                                    v-else
                                    type="button"
                                    :label="t('dm.pick')"
                                    size="small"
                                    class="shrink-0"
                                    :aria-label="t('dm.pickLabel', { nickname: row.user.nickname })"
                                    data-testid="dm-recipient-pick"
                                    @click="$emit('select', row.user)"
                                />
                            </li>
                        </ul>
                    </template>
                </template>
            </div>
        </template>
    </div>
</template>

<script setup lang="ts">
import type { DmPartner, DmRecipient } from '~/composables/dm/api/useDmApi.types';
import { ApiError } from '~/composables/common/useApiClient';
import UtilDm from '~/utils/util-dm';
import UtilDmMessages from '~/utils/util-dm-messages';
import UtilDmRules from '~/utils/util-dm-rules';

/**
 * 받는 사람 고르기 — 검색은 단추(또는 Enter)로만. 2~16자가 아니면 보내지 않고 그 자리에서 알린다. 서버가 `fields.q` 로 거절해도 그 문구를 보인다.
 * 서버 호출은 부르는 쪽이 `search` 로 넘긴다(컴포넌트는 API 를 모른다). 늦게 온 옛 응답은 버리고 새 검색은 앞 검색을 끊는다.
 */
interface Props {
    /** 고른 사람 — 있으면 칩 */
    selected?: DmPartner | null;
    /** 주소의 `?to=` 를 확인하는 중 */
    loading?: boolean;
    /** 받는 사람 찾기(`GET /api/dm/recipients?q=`) */
    search: (q: string, signal: AbortSignal) => Promise<{ items: DmRecipient[] }>;
}

const props = withDefaults(defineProps<Props>(), { selected: null, loading: false });

defineEmits<{ select: [user: DmPartner]; clear: [] }>();

const { t } = useI18n();
const status = ref<'idle' | 'loading' | 'ready' | 'error'>('idle');
const query = ref('');
const searched = ref('');
const results = ref<DmRecipient[]>([]);
const failure = ref('');
const problemText = ref('');

const uid = useId();
const inputId = `${uid}-q`;
const errorId = `${uid}-q-error`;

watch(query, () => (problemText.value = ''));

let seq = 0;
let abort: AbortController | null = null;

async function run() {
    const q = UtilDmRules.normalizeQuery(query.value);
    const problem = UtilDmRules.searchProblem(q);
    if (problem) {
        problemText.value = UtilDmMessages.searchProblem(t, problem);
        return;
    }
    problemText.value = '';
    const mine = ++seq;
    abort?.abort();
    const controller = new AbortController();
    abort = controller;
    status.value = 'loading';
    failure.value = '';
    try {
        const res = await props.search(q, controller.signal);
        if (mine !== seq) return;
        results.value = res.items;
        searched.value = q;
        status.value = 'ready';
    } catch (e) {
        if (mine !== seq) return;
        if (e instanceof ApiError && e.code === 'ABORTED') return;
        if (e instanceof ApiError && e.field('q')) {
            problemText.value = e.field('q') ?? '';
            status.value = 'idle';
            return;
        }
        failure.value = UtilDmMessages.errorOf(t, e);
        status.value = 'error';
    }
}

onBeforeUnmount(() => {
    seq++;
    abort?.abort();
});
</script>
