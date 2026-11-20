<template>
    <!--
        평문 속 이모티콘 토큰(`:emo-ID:`)·내 짤 토큰(`:jjal-ID:`)을 그림으로 그린다 — 댓글 평문·평문 글 본문. 글자를 조각으로 나눠 Vue 로 그린다(v-html 을 쓰지 않는다 — XSS 방지).
        카탈로그에 없는 ID(또는 아직 못 받음)는 글자 그대로. 이모티콘은 `img.emoticon`(100px · 모바일 80px), 짤은 `img.jjal`(긴 변 200px · 모바일 160px, `main.css`).
        짤을 누르면(휴대폰은 길게) 짤 메뉴(`useJjalPress` — 이벤트 위임). 내려간 짤은 「내려간 짤」 자리.
    -->
    <span v-on="jjalPress">
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
            <template v-else-if="seg.kind === 'jjal' && jjal.image(seg.id)">
                <span v-if="jjal.image(seg.id)!.blocked" :class="UtilRichJjal.BLOCKED_CLASS" :data-jjal-id="seg.id">{{ t('jjal.blockedLabel') }}</span>
                <img
                    v-else
                    :src="jjal.image(seg.id)!.url"
                    alt=""
                    :class="UtilRichJjal.CLASS"
                    :width="jjal.image(seg.id)!.width || undefined"
                    :height="jjal.image(seg.id)!.height || undefined"
                    loading="lazy"
                    decoding="async"
                    :data-jjal-id="seg.id"
                />
            </template>
            <template v-else>{{ seg.kind === 'text' ? seg.text : seg.raw }}</template>
        </template>
    </span>
</template>

<script setup lang="ts">
import type { RichEmoticonSegment } from '#layers/rich-editor/utils/util-rich-emoticon';
import type { RichJjalSegment } from '#layers/rich-editor/utils/util-rich-jjal';
import UtilRichEmoticon from '#layers/rich-editor/utils/util-rich-emoticon';
import UtilRichJjal from '#layers/rich-editor/utils/util-rich-jjal';

interface Props {
    text: string;
}

const props = defineProps<Props>();

const { t } = useI18n();
const catalog = useEmoticonCatalog();
const resolve = catalog.resolve;
const jjal = useJjalCatalog();
/** 위에서 놓은 짤의 글 맥락(글 상세·댓글) — 그 글 맥락으로 담기 가능을 묻는다. 없으면 그림만 */
const jjalContext = useJjalContext();
const jjalPress = useJjalPress(jjalContext);

/** 이모티콘으로 먼저 나누고, 남은 글자 조각을 짤로 한 번 더 나눈다 */
const segments = computed<(RichEmoticonSegment | RichJjalSegment)[]>(() =>
    UtilRichEmoticon.split(props.text).flatMap<RichEmoticonSegment | RichJjalSegment>((s) => (s.kind === 'text' ? UtilRichJjal.split(s.text) : [s])),
);
/** 토큰이 있을 때만 카탈로그를 받는다 */
const hasToken = computed(() => segments.value.some((s) => s.kind === 'emoticon'));
watch(
    hasToken,
    (has) => {
        if (has) void catalog.ensureLoaded();
    },
    { immediate: true },
);
/** 짤 토큰의 ID 들 — 있을 때만 모르는 것을 묻는다 */
const jjalIds = computed(() => segments.value.flatMap((s) => (s.kind === 'jjal' ? [s.id] : [])));
watch(
    () => jjalIds.value.join(','),
    () => {
        if (jjalIds.value.length) void jjal.ensure(jjalIds.value, jjalContext?.postId());
    },
    { immediate: true },
);
</script>
