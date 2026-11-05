<template>
    <!--
        댓글 평문 입력칸 아래의 이모티콘 미리보기 — 입력칸에는 토큰 글자(`:emo-ID:`)가 들어가 보기 어려우므로 넣은 이모티콘을 작은 그림 칩으로 보인다(community 4절).
        칩의 ✕ 는 그 토큰을 본문에서 지운다(부르는 쪽이 `remove(차례)` 로). 한 본문 20개를 넘으면 경고 글.
    -->
    <div v-if="ids.length" class="flex flex-wrap items-center gap-1.5" data-testid="emoticon-token-preview">
        <span class="text-xs text-muted-color">{{ t('emoticon.inBody', { count: ids.length }) }}</span>
        <span v-for="(id, i) in ids" :key="`${id}-${i}`" class="inline-flex items-center gap-0.5 rounded-lg border border-surface bg-surface-0 p-0.5 dark:bg-surface-900">
            <img v-if="resolve(id)" :src="resolve(id)!.url" :alt="resolve(id)!.label" :title="resolve(id)!.label" class="size-8 object-contain" loading="lazy" />
            <span v-else class="px-1 text-[0.6875rem] text-muted-color">{{ UtilRichEmoticon.token(id) }}</span>
            <button
                type="button"
                class="grid size-5 place-items-center rounded text-muted-color hover:bg-surface-100 hover:text-color focus-visible:outline-2 focus-visible:outline-primary dark:hover:bg-surface-800"
                :aria-label="t('emoticon.removeFromBody', { name: resolve(id)?.label ?? id })"
                @click="$emit('remove', i)"
            >
                <i class="pi pi-times text-[0.625rem]" aria-hidden="true" />
            </button>
        </span>
        <span v-if="ids.length > UtilRichEmoticon.MAX_PER_BODY" role="alert" class="w-full text-xs text-red-600 dark:text-red-400">{{ t('emoticon.tooManyInBody', { max: UtilRichEmoticon.MAX_PER_BODY }) }}</span>
    </div>
</template>

<script setup lang="ts">
import UtilRichEmoticon from '#layers/rich-editor/utils/util-rich-emoticon';

interface Props {
    text: string;
}

const props = defineProps<Props>();
defineEmits<{ remove: [index: number] }>();

const { t } = useI18n();
const catalog = useEmoticonCatalog();
const resolve = catalog.resolve;

const ids = computed(() => UtilRichEmoticon.ids(props.text));
watch(
    () => ids.value.length > 0,
    (has) => {
        if (has) void catalog.ensureLoaded();
    },
    { immediate: true },
);
</script>
