<template>
    <!--
        게시판 레이아웃 미리보기 고르기 줄 — 테마 화면 「레이아웃 › 게시판」(2026-10-30). 레이아웃은 테마마다 하나라 모든 게시판에 같이 쓰고, 여기서는 **어느 게시판으로 미리 볼지**만 고른다.
          · 게시판: 공개 메뉴의 켜진 게시판(`useGadgetPageFolders().boards` — 메뉴 대상 고르기 값), 처음 = 첫 게시판. 주소 `?board=<slug>` 에 남긴다(부모가).
          · 화면: 「목록 | 글 보기」 — 글 보기는 그 게시판의 최신 공개 글 하나(`usePostApi().list` — `post.read` 가 없거나 글이 없으면 목록만). 주소 `?screen=post`.
          · 고른 결과를 실제 화면 경로(`/boards/{slug}` · `/posts/{id}`)로 알린다(`update:path`) — 부모가 `ThemeLivePreview` 에 넘긴다.
    -->
    <div class="flex flex-col gap-2" data-testid="board-layout-picker">
        <div v-if="loading && !options.length" class="flex gap-2" role="status" :aria-label="t('common.loading')">
            <Skeleton height="2rem" width="14rem" />
        </div>

        <Message v-else-if="error && !options.length" severity="error" size="small" :closable="false">
            <span class="flex flex-wrap items-center gap-2">
                {{ t('homeLayout.board_boardsFailed') }} — {{ error }}
                <Button type="button" :label="t('common.retry')" size="small" severity="secondary" variant="text" @click="load(true)" />
            </span>
        </Message>

        <p v-else-if="!options.length" class="m-0 rounded-lg border border-dashed border-(--line) px-4 py-3 text-sm text-muted-color" data-testid="board-layout-no-boards">{{ t('homeLayout.board_noBoards') }}</p>

        <div v-else class="flex flex-wrap items-center gap-2 rounded-lg border border-(--line) px-3 py-2">
            <label :for="`${uid}-board`" class="text-sm font-medium">{{ t('homeLayout.board_previewBoard') }}</label>
            <Select
                :model-value="slug"
                :input-id="`${uid}-board`"
                :options="options"
                option-label="label"
                option-value="value"
                filter
                :filter-placeholder="t('homeLayout.boardSearch')"
                size="small"
                class="w-60"
                @update:model-value="(v: string) => emit('update:slug', v)"
            />
            <SelectButton
                :model-value="screen"
                :options="screenOptions"
                option-label="label"
                option-value="value"
                option-disabled="disabled"
                :allow-empty="false"
                size="small"
                :aria-label="t('homeLayout.board_previewScreen')"
                @update:model-value="(v: BoardLayoutScreen) => emit('update:screen', v)"
            />
            <span v-if="screen === 'POST' && postLoading" class="text-xs text-muted-color" role="status"><i class="pi pi-spin pi-spinner text-[10px]" aria-hidden="true" /> {{ t('common.loading') }}</span>
            <span v-else-if="!postId && !postLoading" class="text-xs text-muted-color">{{ t('homeLayout.board_noPost') }}</span>
            <span class="flex-1" />
            <a v-if="path && userWebOrigin" :href="`${userWebOrigin}${path}`" target="_blank" rel="noopener" class="text-xs text-primary underline-offset-2 hover:underline">
                {{ path }} <i class="pi pi-external-link text-[10px]" aria-hidden="true" /><span class="sr-only">{{ t('gadgetPage.openNewTab') }}</span>
            </a>
        </div>
        <p class="m-0 text-xs leading-relaxed text-muted-color">{{ t('homeLayout.board_allBoardsHint') }}</p>
    </div>
</template>

<script setup lang="ts">
import type { BoardLayoutScreen } from '~/composables/board/api/useBoardLayoutApi.types';

interface Props {
    /** 미리 볼 게시판 slug(없으면 첫 게시판을 고른다) */
    slug: string | null;
    /** 미리 볼 화면 */
    screen: BoardLayoutScreen;
}
const props = withDefaults(defineProps<Props>(), {});
const emit = defineEmits<{
    (e: 'update:slug', value: string): void;
    (e: 'update:screen', value: BoardLayoutScreen): void;
    /** 실제 화면 경로 — 아직 정하지 못했으면(게시판 없음·읽는 중) null */
    (e: 'update:path', value: string | null): void;
}>();

const { t } = useI18n();
const uid = useId();
const { localizedName } = useLocalizedName();
const { can } = useAdminPermission();
const { origin: userWebOrigin } = useUserWebOrigin();
const { boards, loading, error, load } = useGadgetPageFolders();
const postApi = usePostApi();

/** 켜진 게시판만(꺼진 게시판은 공개 화면이 열리지 않는다) */
const active = computed(() => boards.value.filter((b) => b.active));
const options = computed(() => active.value.map((b) => ({ value: b.slug, label: localizedName(b.name, b.nameEn) })));
const current = computed(() => active.value.find((b) => b.slug === props.slug) ?? null);

/** 고른 게시판의 최신 공개 글 ID(없으면 null — 글 보기를 고를 수 없다) */
const postId = ref<string | null>(null);
const postLoading = ref(false);

const screenOptions = computed(() => [
    { value: 'LIST', label: t('homeLayout.board_screen_LIST'), disabled: false },
    { value: 'POST', label: t('homeLayout.board_screen_POST'), disabled: !postId.value },
]);

const path = computed(() => {
    const b = current.value;
    if (!b) return null;
    // 글 보기인데 글을 찾는 중이면 기다린다(목록을 한 번 띄웠다 다시 띄우지 않게)
    if (props.screen === 'POST' && postLoading.value) return null;
    return props.screen === 'POST' && postId.value ? `/posts/${encodeURIComponent(postId.value)}` : `/boards/${encodeURIComponent(b.slug)}`;
});

let seq = 0;
/** 최신 공개 글 하나 — 글 보기 미리보기용(게시글 조회 권한이 없거나 못 읽으면 없음으로) */
async function loadPost(boardId: string): Promise<void> {
    const mine = ++seq;
    postId.value = null;
    if (can('post.read')) {
        postLoading.value = true;
        try {
            const res = await postApi.list({ board: boardId, state: 'PUBLIC', sort: 'newest', page: 0, size: 1 });
            if (mine === seq) postId.value = res.items[0]?.id ?? null;
        } catch {
            if (mine === seq) postId.value = null;
        } finally {
            if (mine === seq) postLoading.value = false;
        }
    }
    // 글이 없으면 목록으로 돌린다
    if (mine === seq && !postId.value && props.screen === 'POST') emit('update:screen', 'LIST');
}

// 게시판 목록이 오면 고른 것이 없거나 목록에 없을 때 첫 게시판으로
watch(
    active,
    (list) => {
        if (list.length && !list.some((b) => b.slug === props.slug)) emit('update:slug', list[0]!.slug);
    },
    { immediate: true },
);
// 게시판이 바뀌면 그 게시판의 최신 글을 다시 찾는다
watch(
    () => current.value?.id ?? null,
    (id) => {
        if (id) void loadPost(id);
        else postId.value = null;
    },
    { immediate: true },
);
watch(path,(value) => emit('update:path', value), { immediate: true });

onMounted(() => void load());
</script>
