<template>
    <!--
        본문 하나 — 글 서랍(`PostDetailDrawer`)·신고 서랍(`ReportDetailDrawer` 의 지금 본문 · 신고 당시 내용)이 같이 쓴다. 크기·테두리 같은 모양은 부른 쪽이 `class` 로 준다(뿌리 요소에 붙는다).
        - HTML(에디터 글): 서버가 저장할 때 정화한 HTML 만 `v-html` 로 넣는다(`rich-text` 모양) — 다른 경로의 글자(제목·이름 등)는 절대 넣지 않는다.
          넣은 뒤 글자 노드의 토큰만 레이어 `UtilRichEmoticon`·`UtilRichJjal` 로 바꾼다(링크·코드 안 제외 — user-web 과 같은 규칙).
        - 평문: 줄바꿈 그대로, 토큰 자리만 그림(조각 사이에 공백이 끼지 않게 한 줄로 쓴다).
        토큰 그림은 상세가 준 `emoticons`·`jjals`(서버 `AdminBodyTokenImages`)로 찾는다 — 로보티콘은 그림, 짤은 공개 그림(`url`) ·
        내려간 짤(`url: null`·`blocked`)은 운영자 전용 원본(`adminUrl`)이 있으면 그 그림에 「내려간 짤 · 운영자만 보임」 딱지(`.jjal-admin-only`), 없으면 「내려간 짤」 자리,
        목록에 없으면 「[로보티콘]」·「[짤]」 짧은 말(마우스를 올리면 원래 토큰). 서버 호출은 하지 않는다.
    -->
    <!-- eslint-disable-next-line vue/no-v-html -->
    <div v-if="format === 'HTML'" ref="htmlRoot" class="rich-text" v-html="body" />
    <div v-else class="break-words whitespace-pre-line"><template v-for="(seg, i) in segments" :key="i"><template v-if="seg.kind === 'text'">{{ seg.text }}</template><template v-else-if="seg.kind === 'emoticon'"><img v-if="seg.image" :src="seg.image.url" :alt="seg.image.label" :title="seg.image.label" class="emoticon" loading="lazy" decoding="async" width="100" height="100" :data-emoticon-id="seg.id" /><span v-else :title="seg.raw">{{ t('post.emoticonFallback') }}</span></template><span v-else-if="seg.image && !seg.image.blocked && seg.adminOnly" class="jjal-admin-only" :title="t('post.jjalAdminOnly')"><img :src="seg.image.url" :alt="t('post.jjalAdminOnly')" class="jjal" loading="lazy" decoding="async" :width="seg.image.width || undefined" :height="seg.image.height || undefined" :data-jjal-id="seg.id" /><span class="jjal-admin-only__badge" aria-hidden="true">{{ t('post.jjalAdminOnly') }}</span></span><template v-else-if="seg.image && !seg.image.blocked"><img :src="seg.image.url" alt="" class="jjal" loading="lazy" decoding="async" :width="seg.image.width || undefined" :height="seg.image.height || undefined" :data-jjal-id="seg.id" /></template><span v-else-if="seg.image" class="jjal-blocked" :data-jjal-id="seg.id">{{ t('post.jjalBlocked') }}</span><span v-else :title="seg.raw">{{ t('post.jjalFallback') }}</span></template></div>
</template>

<script setup lang="ts">
import UtilRichEmoticon from '#layers/rich-editor/utils/util-rich-emoticon';
import UtilRichJjal from '#layers/rich-editor/utils/util-rich-jjal';
import type { AdminBodyEmoticonView, AdminBodyJjalView } from '~/composables/post/api/usePostApi.types';
import UtilBodyToken from '~/utils/util-body-token';

interface Props {
    /** 본문 — HTML 이면 서버 정화본 */
    body: string;
    /** `HTML` 이면 에디터 글, 그 밖은 평문 */
    format: string;
    /** 상세가 준 로보티콘 그림(없으면 모든 로보티콘이 「[로보티콘]」) */
    emoticons?: readonly AdminBodyEmoticonView[] | null;
    /** 상세가 준 짤(없으면 모든 짤이 「[짤]」) */
    jjals?: readonly AdminBodyJjalView[] | null;
}

const props = withDefaults(defineProps<Props>(), { emoticons: null, jjals: null });

const { t } = useI18n();

const emoticonResolve = computed(() => UtilBodyToken.emoticonResolver(props.emoticons));
const jjalResolve = computed(() => UtilBodyToken.jjalResolver(props.jjals));
/** 내려갔지만 운영자 전용 원본으로 그리는 짤 ID — 딱지를 단다 */
const jjalAdminOnly = computed(() => UtilBodyToken.jjalAdminOnlyIds(props.jjals));

/** 평문 조각 */
const segments = computed(() =>
    props.format === 'HTML' ? [] : UtilBodyToken.segments(props.body, emoticonResolve.value, jjalResolve.value, jjalAdminOnly.value),
);

/** HTML — `v-html` 이 DOM 에 넣은 뒤(`flush: 'post'`) 토큰을 바꾼다. 본문이 같으면 Vue 가 다시 넣지 않으니 이미 바꾼 자리는 그대로다(여러 번 불러도 안전) */
const htmlRoot = ref<HTMLElement | null>(null);
watch(
    [htmlRoot, () => props.body, emoticonResolve, jjalResolve, jjalAdminOnly],
    () => {
        const el = htmlRoot.value;
        if (!el || !UtilBodyToken.has(props.body)) return;
        UtilRichEmoticon.replaceIn(el, emoticonResolve.value, t('post.emoticonFallback'));
        UtilRichJjal.replaceIn(el, jjalResolve.value, t('post.jjalBlocked'), t('post.jjalFallback'));
        UtilBodyToken.markAdminOnly(el, jjalAdminOnly.value, t('post.jjalAdminOnly'));
    },
    { flush: 'post' },
);
</script>
