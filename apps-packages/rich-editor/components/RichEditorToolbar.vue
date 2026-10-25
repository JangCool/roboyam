<template>
    <!--
        에디터 도구 모음(community `RichEditorToolbar`) — 수준(`level`)별로 단추를 켜고 끈다. 단추가 많으면 줄을 바꾼다.
          간단: 굵게·기울임·밑줄·취소선 | 글머리 목록·번호 목록·인용 | 링크
          고급: [제목·글자색·배경색] + 간단 + [코드(인라인·블록)] + [구분선] | [이미지(허용될 때)·유튜브]
          문서: [제목] + 간단 + [구분선]
        이미지·유튜브·링크처럼 부모가 해야 하는 일은 이벤트로 올린다. 켜져 있는 서식은 에디터 상태에서 그때그때 읽는다(`@tiptap/vue-3` 가 트랜잭션마다 다시 그리게 한다).
    -->
    <div role="toolbar" :aria-label="t('richEditor.toolbar')" class="flex flex-wrap items-center gap-1 border-b border-surface p-1.5" :data-level="level" data-testid="rich-toolbar">
        <template v-if="structured">
            <RichEditorMenuButton :label="t('richEditor.heading')" icon="pi pi-heading" :items="headingItems()" :active="is('heading')" :disabled="off" data-testid="tool-heading" @select="onHeading" />
        </template>
        <template v-if="advanced">
            <RichEditorMenuButton :label="t('richEditor.textColor')" text="A" :tint="UtilRichFormats.colorToHex(attr('color'))" :items="textColorItems()" :active="Boolean(attr('color'))" :disabled="off" data-testid="tool-text-color" @select="onTextColor" />
            <RichEditorMenuButton :label="t('richEditor.backgroundColor')" icon="pi pi-palette" :fill="UtilRichFormats.colorToHex(attr('backgroundColor'))" :items="backgroundColorItems()" :active="Boolean(attr('backgroundColor'))" :disabled="off" data-testid="tool-background-color" @select="onBackgroundColor" />
        </template>
        <span v-if="structured" class="mx-0.5 h-5 w-px shrink-0 bg-surface-200 dark:bg-surface-700" aria-hidden="true" />

        <RichEditorToolButton :label="t('richEditor.bold')" icon="pi pi-bold" toggle :active="is('bold')" :disabled="off" data-testid="tool-bold" @click="chain((c) => c.toggleBold())" />
        <RichEditorToolButton :label="t('richEditor.italic')" icon="pi pi-italic" toggle :active="is('italic')" :disabled="off" data-testid="tool-italic" @click="chain((c) => c.toggleItalic())" />
        <RichEditorToolButton :label="t('richEditor.underline')" icon="pi pi-underline" toggle :active="is('underline')" :disabled="off" data-testid="tool-underline" @click="chain((c) => c.toggleUnderline())" />
        <RichEditorToolButton :label="t('richEditor.strike')" icon="pi pi-strikethrough" toggle :active="is('strike')" :disabled="off" data-testid="tool-strike" @click="chain((c) => c.toggleStrike())" />
        <span class="mx-0.5 h-5 w-px shrink-0 bg-surface-200 dark:bg-surface-700" aria-hidden="true" />

        <RichEditorToolButton :label="t('richEditor.bulletList')" icon="pi pi-list" toggle :active="is('bulletList')" :disabled="off" data-testid="tool-bullet-list" @click="chain((c) => c.toggleBulletList())" />
        <RichEditorToolButton :label="t('richEditor.orderedList')" icon="pi pi-list-ol" toggle :active="is('orderedList')" :disabled="off" data-testid="tool-ordered-list" @click="chain((c) => c.toggleOrderedList())" />
        <RichEditorToolButton :label="t('richEditor.blockquote')" icon="pi pi-block-quote" toggle :active="is('blockquote')" :disabled="off" data-testid="tool-blockquote" @click="chain((c) => c.toggleBlockquote())" />
        <RichEditorMenuButton v-if="advanced" :label="t('richEditor.code')" icon="pi pi-code" :items="codeItems()" :active="is('code') || is('codeBlock')" :disabled="off" data-testid="tool-code" @select="onCode" />
        <span class="mx-0.5 h-5 w-px shrink-0 bg-surface-200 dark:bg-surface-700" aria-hidden="true" />

        <RichEditorToolButton :label="t('richEditor.link')" icon="pi pi-link" toggle :active="is('link') || linkActive" :disabled="off" data-testid="tool-link" @click="emit('link')" />
        <RichEditorToolButton v-if="structured" :label="t('richEditor.horizontalRule')" icon="pi pi-minus" :disabled="off" data-testid="tool-hr" @click="chain((c) => c.setHorizontalRule())" />

        <template v-if="advanced">
            <span class="mx-0.5 h-5 w-px shrink-0 bg-surface-200 dark:bg-surface-700" aria-hidden="true" />
            <!-- 이미지 — 게시판이 허용하고 올리기 함수가 있을 때만(비회원 글쓰기·이미지 금지 게시판은 감춘다) -->
            <RichEditorToolButton v-if="imageUpload" :label="t('richEditor.image')" icon="pi pi-image" :disabled="off" data-testid="tool-image" @click="emit('image')" />
            <RichEditorToolButton :label="t('richEditor.youtube')" icon="pi pi-youtube" toggle :active="youtubeActive" :disabled="off" data-testid="tool-youtube" @click="emit('youtube')" />
        </template>
    </div>
</template>

<script setup lang="ts">
import type { ChainedCommands, Editor } from '@tiptap/vue-3';
import type { RichEditorLevel } from '#layers/rich-editor/components/RichEditor.types';
import type { RichMenuItem } from '#layers/rich-editor/components/RichEditorMenuButton.types';
import UtilRichFormats from '#layers/rich-editor/utils/util-rich-formats';

/**
 * 공개 사이트와 관리자 앱이 같이 쓰는 레이어 파일이다 — `~/`·앱 컴포저블을 import 하지 않는다. 앱마다 다른 것은 속성으로 받는다.
 * 글자색·배경색은 팔레트(`UtilRichFormats`)에서만 고른다(서버가 같은 목록으로 거른다).
 */
interface Props {
    editor?: Editor;
    level?: RichEditorLevel;
    disabled?: boolean;
    /** 이미지 넣기 단추를 그린다(고급 수준에서만 의미가 있다) */
    imageUpload?: boolean;
    /** 링크 입력 창이 열려 있다 — 링크 단추를 켠 모양으로 */
    linkActive?: boolean;
    /** 유튜브 입력 창이 열려 있다 */
    youtubeActive?: boolean;
}

const props = withDefaults(defineProps<Props>(), { editor: undefined, level: 'SIMPLE', disabled: false, imageUpload: false, linkActive: false, youtubeActive: false });

const emit = defineEmits<{ link: []; image: []; youtube: [] }>();

const { t } = useI18n();

const advanced = computed(() => props.level === 'ADVANCED');
/** 제목·구분선이 있는 수준 — 고급·문서 */
const structured = computed(() => advanced.value || props.level === 'DOCUMENT');
/** 에디터가 없거나 잠겼다 — 모든 도구가 꺼진다 */
const off = computed(() => props.disabled || !props.editor);

function chain(run: (commands: ChainedCommands) => ChainedCommands) {
    const e = props.editor;
    if (e) run(e.chain().focus()).run();
}

function is(name: string, attrs?: Record<string, unknown>): boolean {
    return props.editor?.isActive(name, attrs) ?? false;
}

/** 글자에 걸린 색 — 선택에 여러 값이 섞여 있으면 첫 글자의 값 */
function attr(name: 'color' | 'backgroundColor'): string | undefined {
    const value = props.editor?.getAttributes('textStyle')[name] as string | undefined;
    return value || undefined;
}

/* ── 제목 — 본문·H2·H3 ── */
function headingItems(): RichMenuItem[] {
    return [
        { key: '0', label: t('richEditor.paragraph'), badge: '¶', active: is('paragraph') },
        { key: '2', label: t('richEditor.heading2'), badge: 'H2', active: is('heading', { level: 2 }), sample: { fontWeight: '700', fontSize: '1.15em' } },
        { key: '3', label: t('richEditor.heading3'), badge: 'H3', active: is('heading', { level: 3 }), sample: { fontWeight: '700', fontSize: '1.05em' } },
    ];
}

function onHeading(key: string) {
    if (key === '2' || key === '3') {
        const level = key === '2' ? 2 : 3;
        chain((c) => c.toggleHeading({ level }));
    } else {
        chain((c) => c.setParagraph());
    }
}

/* ── 코드 — 인라인 코드와 코드 블록을 한 드롭다운으로 ── */
function codeItems(): RichMenuItem[] {
    return [
        { key: 'inline', label: t('richEditor.codeInline'), icon: 'pi pi-code', active: is('code') },
        { key: 'block', label: t('richEditor.codeBlock'), badge: '{ }', active: is('codeBlock') },
    ];
}

function onCode(key: string) {
    if (key === 'inline') chain((c) => c.toggleCode());
    else chain((c) => c.toggleCodeBlock());
}

/* ── 글자색·배경색 — 팔레트에서만. 견본은 항목 글자에 색을 입혀 보여 준다 ── */
function textColorItems(): RichMenuItem[] {
    const now = UtilRichFormats.colorToHex(attr('color'));
    return [
        { key: '', label: t('richEditor.colorDefault'), badge: 'A', active: !now },
        ...UtilRichFormats.TEXT_COLORS.map((o) => ({ key: o.value, label: t(`richEditor.color.${o.key}`), badge: 'A', active: now === o.value, sample: { color: o.value, fontWeight: '600' } })),
    ];
}

function onTextColor(key: string) {
    chain((c) => (key ? c.setColor(key) : c.unsetColor()));
}

function backgroundColorItems(): RichMenuItem[] {
    const now = UtilRichFormats.colorToHex(attr('backgroundColor'));
    return [
        { key: '', label: t('richEditor.backgroundNone'), badge: '가', active: !now },
        ...UtilRichFormats.BACKGROUND_COLORS.map((o) => ({ key: o.value, label: t(`richEditor.color.${o.key}`), badge: '가', active: now === o.value, sample: { backgroundColor: o.value, color: '#0f172a', padding: '0 0.375rem', borderRadius: '0.25rem' } })),
    ];
}

function onBackgroundColor(key: string) {
    chain((c) => (key ? c.setBackgroundColor(key) : c.unsetBackgroundColor()));
}
</script>
