<template>
    <!--
        서식 에디터(TipTap) — community `frontend/app/components/write/RichEditor.vue` 를 레이어로 옮겼다. HTML 을 주고받는다(`v-model`) — 서버가 받은 HTML 을 다시 정화해 저장한다.
        수준(`level`)이 도구 모음과 확장을 정한다(`RichEditor.types.ts`). 수준은 만들 때 한 번 정해진다 — 바꾸려면 부르는 쪽이 `:key` 로 새로 만든다.
        고급 수준: 이미지(`imageUpload` + 올리기 함수 `upload` 가 있을 때 — 올리기 단추·드롭존·붙여넣기·끌어놓기)·유튜브·블록 끌기 손잡이.
        이모티콘·투표는 로보얌에 없어 뺐다(「없는 기능은 감춘다」).
        이미지는 올리기가 **끝난 뒤에** 문서에 들어간다(올리는 중·실패는 아래 목록에 남는다 — 문서는 늘 깨끗하다). 올리는 중이거나 실패가 남아 있으면 `media` 로 알려 부모가 저장을 막는다.
        빈 에디터는 `''` 를 낸다(`<p></p>` 가 아니다) — 부모의 「고쳤는가」·「비었는가」 판정이 평문과 같아진다.
    -->
    <div
        class="rounded-lg border bg-surface-0 dark:bg-surface-900"
        :class="invalid ? 'border-red-500 dark:border-red-400' : 'border-surface focus-within:border-primary'"
        :data-level="level"
        data-testid="rich-editor"
    >
        <RichEditorToolbar
            :editor="editor ?? undefined"
            :level="level"
            :disabled="disabled"
            :image-upload="imageOn"
            :link-active="linkOpen"
            :youtube-active="youtubeOpen"
            @link="openLink"
            @image="pickImages"
            @youtube="openYoutube"
        />
        <input v-if="imageOn" ref="fileInput" type="file" class="sr-only" multiple :accept="UtilRichMedia.ACCEPT" tabindex="-1" aria-hidden="true" data-testid="image-input" @change="onFilesPicked" />

        <!-- 이미지 올리기 띠 — 끌어다 놓거나 눌러서 고른다 -->
        <RichEditorDropZone v-if="imageOn" :disabled="disabled || !editor" @pick="pickImages" @files="onZoneFiles" />

        <div class="relative">
            <!-- 비어 있을 때 안내 글(에디터 안 글자가 아니다) -->
            <p v-if="isEmpty && placeholder" class="pointer-events-none absolute left-3 top-2.5 m-0 text-[0.9375rem] text-muted-color" :class="advanced ? 'md:left-8' : ''" aria-hidden="true">{{ placeholder }}</p>
            <EditorContent :editor="editor" />
            <RichEditorDragHandle v-if="advanced && editor && !disabled" :editor="editor" />
        </div>

        <!-- 올리는 중·올리지 못한 이미지 -->
        <RichEditorUploadList v-if="uploadItems.length" :items="uploadItems" @retry="retryUpload" @remove="removeUpload" />

        <!-- 첨부 수 -->
        <p v-if="counts.images > 0 || counts.videos > 0" class="m-0 border-t border-surface px-3 py-1.5 text-xs text-muted-color" data-testid="media-count">
            {{ t('richEditor.mediaCount', { images: counts.images, videos: counts.videos }) }}
        </p>
        <!-- 이미지를 받지 않는 에디터에 파일을 놓거나 붙여넣었다 — 잠깐 한 줄 -->
        <p v-if="notice" class="m-0 border-t border-surface px-3 py-1.5 text-xs text-amber-700 dark:text-amber-400" role="status" data-testid="editor-notice">{{ notice }}</p>
        <p class="sr-only" role="status" aria-live="polite">{{ announce }}</p>

        <RichEditorLinkDialog v-model:visible="linkOpen" :initial-url="linkInitial" :has-link="hasLink" :error="linkError" @apply="applyLink" @remove="removeLink" />
        <RichEditorYoutubeDialog v-if="advanced" v-model:visible="youtubeOpen" @insert="insertYoutube" />
    </div>
</template>

<script setup lang="ts">
import { EditorContent, useEditor } from '@tiptap/vue-3';
import type { Editor, Extensions } from '@tiptap/vue-3';
import type { RichEditorLevel, RichEditorMediaState, RichImageUploader, RichImageUploadResult } from '#layers/rich-editor/components/RichEditor.types';
import { richFormatExtensions } from '#layers/rich-editor/editor/richExtensions';
import { ImageBlock } from '#layers/rich-editor/editor/imageBlock';
import { YoutubeBlock } from '#layers/rich-editor/editor/youtubeBlock';
import { UploadSlots, releaseSlot, reserveSlot, takeSlot } from '#layers/rich-editor/editor/uploadSlots';
import { countMedia } from '#layers/rich-editor/editor/mediaCount';
import UtilRichFormats from '#layers/rich-editor/utils/util-rich-formats';
import UtilRichMedia from '#layers/rich-editor/utils/util-rich-media';

interface Props {
    level?: RichEditorLevel;
    /** 이미지 올리기를 허용한다(게시판 `imageUploadAllowed` · 회원 글쓰기) — 고급 수준이고 `upload` 가 있을 때만 켜진다. 막는 것은 서버(`422 IMAGE_UPLOAD_NOT_ALLOWED`)이고 이것은 편의다 */
    imageUpload?: boolean;
    /** 이미지 한 장을 올리는 함수 — 앱의 API 컴포저블로 만든다(레이어는 서버를 부르지 않는다) */
    upload?: RichImageUploader | null;
    placeholder?: string;
    disabled?: boolean;
    invalid?: boolean;
    /** 라벨(보이는 글) 요소의 id — 스크린리더가 에디터 이름을 읽는다. 없으면 `ariaLabel` */
    labelledby?: string;
    describedby?: string;
    ariaLabel?: string;
    /** 낮은 높이(댓글) */
    compact?: boolean;
}

const props = withDefaults(defineProps<Props>(), {
    level: 'SIMPLE',
    imageUpload: false,
    upload: null,
    placeholder: '',
    disabled: false,
    invalid: false,
    labelledby: '',
    describedby: '',
    ariaLabel: '',
    compact: false,
});

const html = defineModel<string>({ default: '' });

const emit = defineEmits<{
    /** 처음 만들어졌을 때의 정규화된 HTML — "고쳤는지"를 가르는 기준(고치기 화면) */
    ready: [html: string];
    /** 첨부 상태 — 이미지·유튜브 수, 올리는 중·실패한 이미지 수 */
    media: [state: RichEditorMediaState];
}>();

const { t } = useI18n();

const advanced = props.level === 'ADVANCED';
/** 이미지 올리기가 켜졌다 — 고급 + 허용 + 올리기 함수 */
const imageOn = computed(() => advanced && props.imageUpload && props.upload !== null);

/* ── 잠깐 보이는 안내 한 줄 ── */
const notice = ref('');
let noticeTimer: ReturnType<typeof setTimeout> | null = null;
function showNotice(text: string): void {
    notice.value = text;
    if (noticeTimer) clearTimeout(noticeTimer);
    noticeTimer = setTimeout(() => (notice.value = ''), 4000);
}

/* ── 이미지 올리기 ── */
const counts = reactive({ images: 0, videos: 0 });

const {
    items: uploadItems,
    active: activeUploads,
    failed: failedUploads,
    announce,
    add: addUploads,
    retry: retryUpload,
    remove: removeUpload,
} = useRichImageUploads({
    uploader: () => (imageOn.value ? props.upload : null),
    onUploaded: insertUploaded,
    onGone: (id) => {
        if (editor.value) releaseSlot(editor.value, id);
    },
});

const media = computed<RichEditorMediaState>(() => ({ images: counts.images, videos: counts.videos, uploading: activeUploads.value.length, failed: failedUploads.value.length }));
watch(media, (state) => emit('media', state), { immediate: true, deep: true });

function refreshCounts(): void {
    const e = editor.value;
    if (!e) return;
    const next = countMedia(e.state.doc);
    counts.images = next.images;
    counts.videos = next.videos;
}

/** 빈 문서는 `''` — 「비었는가」·「고쳤는가」가 평문과 같게. 색은 `#rrggbb` 로 되돌린다(TipTap 이 `rgb(...)` 로 직렬화한다) */
function htmlOf(e: Pick<Editor, 'isEmpty' | 'getHTML'>): string {
    if (e.isEmpty) return '';
    return advanced ? UtilRichFormats.normalizeColors(e.getHTML()) : e.getHTML();
}

function extensions(): Extensions {
    const list = richFormatExtensions(props.level);
    if (!advanced) return list;
    return [
        ...list,
        // 이미 글에 있는 이미지는 올리기가 꺼져 있어도 보이고 지울 수 있다
        ImageBlock.configure({ labels: { image: t('richEditor.imageAlt'), remove: t('richEditor.imageRemove'), broken: t('richEditor.imageBroken') } }),
        YoutubeBlock.configure({ labels: { video: t('richEditor.youtubeLabel'), remove: t('richEditor.youtubeRemove') } }),
        UploadSlots,
    ];
}

/** 이미지를 받지 않는 에디터에 파일이 왔다 */
function refuseFiles(): void {
    showNotice(t('richEditor.imageNotAllowed'));
}

const editor = useEditor({
    content: html.value,
    extensions: extensions(),
    editable: !props.disabled,
    editorProps: {
        attributes: {
            class: [
                'rich-text py-2.5 pl-3 pr-3 text-[0.9375rem]',
                props.compact ? 'min-h-[5.5rem]' : 'min-h-[14rem] md:min-h-[20rem]',
                advanced ? 'md:pl-8' : '',
            ].join(' '),
            role: 'textbox',
            'aria-multiline': 'true',
            spellcheck: 'false',
            ...(props.labelledby ? { 'aria-labelledby': props.labelledby } : { 'aria-label': props.ariaLabel || t('richEditor.body') }),
            ...(props.describedby ? { 'aria-describedby': props.describedby } : {}),
        },
        // 다른 곳에서 복사한 글의 팔레트 밖 색·다른 style 은 지운다(저장하면 어차피 사라지므로 에디터에서도 미리 없앤다)
        transformPastedHTML: (pasted) => (advanced ? UtilRichFormats.cleanPastedStyles(pasted) : pasted),
        // 이미지 파일 붙여넣기 — 글자가 함께 들어 있으면(워드·엑셀 복사) 글자 붙여넣기를 그대로 둔다
        handlePaste: (view, event) => {
            const data = event.clipboardData;
            if (!data || props.disabled) return false;
            const images = Array.from(data.files).filter((f) => f.type.startsWith('image/'));
            if (!images.length || data.getData('text/plain').trim()) return false;
            if (!imageOn.value) {
                refuseFiles();
                return true;
            }
            queueFiles(images, view.state.selection.to);
            return true;
        },
        // 파일 끌어다 놓기 — 에디터 안에서 노드를 옮기는 끌기(`moved`)는 건드리지 않는다.
        // 파일이면 종류와 상관없이 가로챈다(놓친 파일을 브라우저가 열어 버려 쓰던 글이 사라지는 것을 막는다)
        handleDrop: (view, event, _slice, moved) => {
            if (moved || props.disabled) return false;
            const dropped = Array.from(event.dataTransfer?.files ?? []);
            if (!dropped.length) return false;
            if (!imageOn.value) {
                refuseFiles();
                return true;
            }
            const at = view.posAtCoords({ left: event.clientX, top: event.clientY });
            queueFiles(dropped, at?.pos ?? view.state.selection.to);
            return true;
        },
    },
    onCreate: ({ editor: e }) => {
        refreshCounts();
        emit('ready', htmlOf(e));
    },
    onUpdate: ({ editor: e }) => {
        refreshCounts();
        html.value = htmlOf(e);
    },
});

const isEmpty = computed(() => editor.value?.isEmpty ?? true);

/**
 * 이미지는 블록이라 문단 글자 끝에 넣으면 ProseMirror 가 그 문단 **뒤**로 옮겨 넣는다. 예약한 자리가 문단 안쪽이면 여러 장의 순서가 뒤집히므로
 * 자리를 처음부터 "그 문단 바로 뒤"(블록 경계)로 정해 둔다 — 여러 장이 같은 경계에 예약되면 먼저 들어간 이미지 뒤로 밀린다(community 와 같다).
 */
function blockBoundary(e: Editor, pos: number): number {
    const $pos = e.state.doc.resolve(Math.min(Math.max(pos, 0), e.state.doc.content.size));
    return $pos.parent.isTextblock ? $pos.after() : $pos.pos;
}

/** 고른 파일들을 올리기 목록에 넣고, 다 올라오면 들어갈 자리를 정해 둔다 */
function queueFiles(files: File[], pos: number): void {
    const e = editor.value;
    if (!e || !files.length) return;
    if (!imageOn.value) {
        refuseFiles();
        return;
    }
    const ids = addUploads(files);
    const at = blockBoundary(e, pos);
    for (const id of ids) reserveSlot(e, id, at);
}

/** 한 장이 올라왔다 — 고른 자리에 이미지를 넣는다 */
function insertUploaded(id: string, result: RichImageUploadResult): void {
    const e = editor.value;
    if (!e) return;
    const pos = takeSlot(e, id) ?? blockBoundary(e, e.state.selection.to);
    // updateSelection: false — 넣은 이미지가 "선택된 노드"가 되면 다음에 치는 글자가 그 이미지를 대신해 버린다
    e.chain()
        .insertContentAt(pos, { type: 'image', attrs: { imageId: result.id, src: result.url, width: result.width, height: result.height } }, { updateSelection: false })
        .run();
}

/* ── 파일 고르기 ── */
const fileInput = ref<HTMLInputElement | null>(null);

function pickImages(): void {
    if (!editor.value || props.disabled || !imageOn.value) return;
    fileInput.value?.click();
}

/** 올리기 띠에 파일을 놓았다 — 커서 자리를 기준으로 넣는다 */
function onZoneFiles(files: File[]): void {
    const e = editor.value;
    if (!e || props.disabled) return;
    queueFiles(files, e.state.selection.to);
}

function onFilesPicked(event: Event): void {
    const input = event.target as HTMLInputElement;
    const picked = Array.from(input.files ?? []);
    input.value = ''; // 같은 파일을 다시 골라도 change 가 나오게
    const e = editor.value;
    if (!e || !picked.length) return;
    queueFiles(picked, e.state.selection.to);
}

/* ── 유튜브 ── */
const youtubeOpen = ref(false);

function openYoutube(): void {
    if (!editor.value || props.disabled) return;
    youtubeOpen.value = true;
}

function insertYoutube(videoId: string): void {
    const e = editor.value;
    if (!e) return;
    // 글자나 이미지가 골라져 있어도 그것을 지우지 않고 그 뒤에 넣는다
    e.chain().focus().insertContentAt(e.state.selection.to, { type: 'youtube', attrs: { videoId } }, { updateSelection: false }).run();
}

/* ── 링크 ── */
const linkOpen = ref(false);
const linkInitial = ref('');
const linkError = ref('');
const hasLink = ref(false);

function openLink(): void {
    const e = editor.value;
    if (!e || props.disabled) return;
    hasLink.value = e.isActive('link');
    linkInitial.value = (e.getAttributes('link').href as string | undefined) ?? '';
    linkError.value = '';
    linkOpen.value = true;
}

/** 주소를 검사한다 — 스킴을 빼고 쓰면 `https://` 를 붙여 주고, http·https·mailto 만 받는다 */
function normalizeUrl(raw: string): string | null {
    const value = raw.trim();
    if (!value) return null;
    const withProtocol = /^[a-z][a-z0-9+.-]*:/i.test(value) ? value : `https://${value}`;
    try {
        const url = new URL(withProtocol);
        return ['http:', 'https:', 'mailto:'].includes(url.protocol) ? url.toString() : null;
    } catch {
        return null;
    }
}

function applyLink(raw: string): void {
    const e = editor.value;
    if (!e) return;
    const href = normalizeUrl(raw);
    if (!href) {
        linkError.value = t('richEditor.linkInvalid');
        return;
    }
    const { from, to } = e.state.selection;
    if (from === to && !e.isActive('link')) {
        // 골라 둔 글자가 없으면 주소 자체를 글자로 넣고 링크를 건다
        e.chain().focus().insertContent({ type: 'text', text: href, marks: [{ type: 'link', attrs: { href } }] }).run();
    } else {
        e.chain().focus().extendMarkRange('link').setLink({ href }).run();
    }
    linkOpen.value = false;
}

function removeLink(): void {
    editor.value?.chain().focus().extendMarkRange('link').unsetLink().run();
    linkOpen.value = false;
}

/* ── 밖에서 바뀐 값·잠금 ── */
watch(html, (value) => {
    const e = editor.value;
    if (e && value !== htmlOf(e)) {
        e.commands.setContent(value, { emitUpdate: false });
        refreshCounts();
    }
});

watch(
    () => props.disabled,
    // 잠글 때는 'update' 를 내지 않는다(기본값은 낸다) — 내면 저장 중에 서버 오류 표시가 지워진다
    (disabled) => editor.value?.setEditable(!disabled, false),
);

onBeforeUnmount(() => {
    if (noticeTimer) clearTimeout(noticeTimer);
    editor.value?.destroy();
});

defineExpose({
    focus: () => editor.value?.commands.focus('end'),
});
</script>
