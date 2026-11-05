<template>
    <!--
        평문 속 이모티콘 토큰(`:emo-ID:`)을 그림으로 그린다 — 댓글 평문·평문 글 본문. 글자를 조각으로 나눠 Vue 로 그린다(v-html 을 쓰지 않는다 — XSS 방지).
        카탈로그에 없는 ID(또는 아직 못 받음)는 글자 그대로. 그림은 `img.emoticon`(100px · 모바일 80px, `main.css`).
    -->
    <span>
        <template v-for="(seg, i) in segments" :key="i">
            <img
                v-if="seg.kind === 'emoticon' && resolve(seg.id)"
                :src="resolve(seg.id)!.url"
                :alt="resolve(seg.id)!.label"
                :title="resolve(seg.id)!.label"
                class="emoticon"
                width="100"
                height="100"
                loading="lazy"
                decoding="async"
                :data-emoticon-id="seg.id"
            />
            <template v-else>{{ seg.kind === 'text' ? seg.text : seg.raw }}</template>
        </template>
    </span>
</template>

<script setup lang="ts">
import UtilRichEmoticon from '#layers/rich-editor/utils/util-rich-emoticon';

interface Props {
    text: string;
}

const props = defineProps<Props>();

const catalog = useEmoticonCatalog();
const resolve = catalog.resolve;

const segments = computed(() => UtilRichEmoticon.split(props.text));
/** 토큰이 있을 때만 카탈로그를 받는다 */
const hasToken = computed(() => segments.value.some((s) => s.kind === 'emoticon'));
watch(
    hasToken,
    (has) => {
        if (has) void catalog.ensureLoaded();
    },
    { immediate: true },
);
</script>
