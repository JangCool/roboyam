<template>
    <!-- 프로필 탭의 목록 — 처음 불러오기(스켈레톤)·오류(다시 시도)·빈 상태(슬롯)·「더보기」(커서)를 한 곳에서 한다. 한 줄 모양은 `item` 슬롯이 정한다(자기 `<li>` 를 그린다) -->
    <div :aria-busy="status === 'loading'">
        <FeedSkeleton v-if="status === 'loading'" :count="5" />

        <!-- 오류 — 머리(프로필)는 그대로 두고 이 목록만 다시 시도한다 -->
        <ErrorState v-else-if="status === 'error'" :title="errorTitle" :description="errorMessage" retry data-testid="feed-error" @retry="start" />

        <template v-else>
            <slot v-if="items.length === 0" name="empty" />

            <template v-else>
                <ul class="-mx-4 m-0 list-none p-0 md:mx-0">
                    <template v-for="item in items" :key="item.id"><slot name="item" :item="item" /></template>
                </ul>

                <div v-if="hasNext" class="py-4 text-center">
                    <Button type="button" :label="t('profile.feedMore')" severity="secondary" variant="outlined" :loading="loadingMore" data-testid="feed-more" @click="loadMore" />
                    <p v-if="moreError" class="mt-2 text-xs text-red-600 dark:text-red-400" role="alert">{{ moreError }}</p>
                </div>
            </template>
        </template>

        <!-- 스크린리더가 불러온 개수를 읽는다. 안내 영역은 처음부터 있어야 바뀐 내용을 읽어 준다 -->
        <p role="status" aria-live="polite" class="sr-only">{{ summary }}</p>
    </div>
</template>

<script setup lang="ts" generic="T extends { id: string }">
import UtilBoardMessages from '~/utils/util-board-messages';

/**
 * 회원 프로필의 작성글·댓글 목록 — 커서로 이어 읽는다(「더보기」). 두 탭이 같은 흐름이라 한 곳에 둔다(community `ProfileFeed`).
 * `load` 는 한 쪽을 가져오는 함수(`cursor` 가 없으면 첫 쪽)이고, 부르는 쪽이 `:key` 로 탭·회원이 바뀔 때마다 새로 만든다. 서버 호출은 부르는 쪽(페이지)의 함수다.
 * 늦게 온 옛 결과는 요청 번호로 버리고, 화면이 사라지면 요청을 끊는다.
 */
interface FeedPage {
    items: T[];
    hasNext: boolean;
    nextCursor: string | null;
}

interface Props {
    load: (cursor: string | undefined, signal: AbortSignal) => Promise<FeedPage>;
    /** 처음 불러오기에 실패했을 때의 제목 */
    errorTitle: string;
    /** 스크린리더용 이름 — 「작성글」「댓글」 */
    label: string;
}

const props = withDefaults(defineProps<Props>(), {});

const { t } = useI18n();
const status = ref<'loading' | 'ready' | 'error'>('loading');
const errorMessage = ref('');
const items = ref<T[]>([]) as Ref<T[]>;
const hasNext = ref(false);
const nextCursor = ref<string | null>(null);
const loadingMore = ref(false);
const moreError = ref('');

let controller: AbortController | null = null;
let request = 0;

async function start() {
    const mine = ++request;
    controller?.abort();
    controller = new AbortController();
    status.value = 'loading';
    moreError.value = '';
    loadingMore.value = false;
    try {
        const page = await props.load(undefined, controller.signal);
        if (mine !== request) return;
        items.value = page.items;
        hasNext.value = page.hasNext && page.nextCursor !== null;
        nextCursor.value = page.nextCursor;
        status.value = 'ready';
    } catch (e) {
        if (mine !== request) return;
        errorMessage.value = UtilBoardMessages.of(t, e);
        status.value = 'error';
    }
}

/** 이어서 한 쪽 더 — 실패해도 이미 보이는 목록은 그대로 두고 그 자리에서 다시 누를 수 있다 */
async function loadMore() {
    if (loadingMore.value || !hasNext.value || nextCursor.value === null) return;
    const mine = request;
    loadingMore.value = true;
    moreError.value = '';
    try {
        const page = await props.load(nextCursor.value, (controller ??= new AbortController()).signal);
        if (mine !== request) return;
        const seen = new Set(items.value.map((item) => item.id));
        items.value = [...items.value, ...page.items.filter((item) => !seen.has(item.id))];
        hasNext.value = page.hasNext && page.nextCursor !== null;
        nextCursor.value = page.nextCursor;
    } catch (e) {
        if (mine !== request) return;
        moreError.value = t('feed.moreFailed', { message: UtilBoardMessages.of(t, e) });
    } finally {
        if (mine === request) loadingMore.value = false;
    }
}

const summary = computed(() => {
    if (status.value === 'loading') return t('profile.feedLoading', { label: props.label });
    if (status.value === 'error') return t('profile.feedFailed', { label: props.label });
    if (items.value.length === 0) return '';
    return hasNext.value ? t('profile.feedCountMore', { label: props.label, count: items.value.length }) : t('profile.feedCount', { label: props.label, count: items.value.length });
});

void start();
onBeforeUnmount(() => {
    request++;
    controller?.abort();
});
</script>
