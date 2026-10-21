<template>
    <!--
        사이트 기본 정보 구획의 **이미지 줄**(사이트 아이콘 · 링크 미리보기 이미지) — 로고 줄과 같은 결이다: 소제목 + "올리면 바로 적용" 표시 → [미리보기 타일][상태 한 줄 → 단추 열] → 규칙 한 줄 → (SVG 소스 칸 등 `default` 슬롯) → 거절 문구.
        서버는 부르지 않는다 — 고른 파일은 `pick`, SVG 소스 열기는 `openSvg`, 되돌리기는 `reset`, 불러오기 재시도는 `retry` 로 알리고, 검사·호출·확인창은 페이지가 맡는다. 문구는 모두 부모가 준다(줄마다 달라서) — 줄 공통 문구만 `site.image*` 키.
        소제목·표시는 불러오는 중·불러오지 못함일 때도 남는다. 미리보기 타일은 `<button>` 이라 누르면(Enter·Space) 파일 선택 창이 열린다. 권한이 없으면(`editable=false`) 단추를 감추고 타일은 눌러지지 않는 읽기 전용이다.
        올리기·되돌리기는 이름·소개의 [저장]과 따로 즉시 적용된다 — 그 차이를 소제목 옆 표시가 말한다. 그림은 `<img src>` 하나로만 그린다(`v-html` 금지).
    -->
    <div role="group" :aria-labelledby="titleId" class="flex flex-col gap-1.5">
        <div class="flex flex-wrap items-center gap-x-2 gap-y-1">
            <h3 :id="titleId" class="text-sm font-medium">{{ title }}</h3>
            <span class="rounded-full bg-surface-100 px-2 py-0.5 text-[0.6875rem] font-medium text-muted-color dark:bg-surface-800">{{ t('site.imageImmediate') }}</span>
        </div>
        <p v-if="help" class="text-xs leading-relaxed text-muted-color">{{ help }}</p>

        <div v-if="loading" class="flex items-start gap-3" role="status" :aria-label="t('common.loading')">
            <Skeleton :width="shape === 'wide' ? '8rem' : '4rem'" :height="shape === 'wide' ? '4.2rem' : '4rem'" />
            <Skeleton width="min(16rem, 60%)" height="2rem" />
        </div>

        <div v-else-if="loadError" role="alert" class="flex flex-wrap items-center gap-x-2 gap-y-1 text-xs text-red-600 dark:text-red-400">
            <span>{{ loadFailedTitle }}<template v-if="loadError"> — {{ loadError }}</template></span>
            <Button type="button" :label="t('common.retry')" icon="pi pi-refresh" size="small" variant="text" @click="emit('retry')" />
        </div>

        <template v-else>
            <div class="flex items-start gap-3">
                <!-- 미리보기를 누르면 바로 그림을 고른다 — 누를 수 있다는 건 눌러 보기 전엔 모르니 단추도 함께 둔다 -->
                <button
                    v-tooltip.top="editable ? t('site.imageTileTooltip') : undefined"
                    type="button"
                    class="flex shrink-0 items-center justify-center overflow-hidden rounded-xl border border-(--line) bg-surface-100 transition hover:border-primary focus-visible:outline-2 focus-visible:outline-primary disabled:cursor-default disabled:hover:border-(--line) dark:bg-surface-800"
                    :class="shape === 'wide' ? 'aspect-[1200/630] w-32' : 'size-16'"
                    :disabled="!editable || busy"
                    :aria-label="tileLabel"
                    :aria-describedby="hintId"
                    @click="pickFile"
                >
                    <img v-if="imageUrl && !broken" :src="imageUrl" alt="" class="size-full object-contain" @error="broken = true" />
                    <i v-else class="pi pi-image text-lg text-muted-color" aria-hidden="true" />
                </button>

                <div class="flex min-w-0 flex-col gap-1.5">
                    <p class="text-xs font-medium">{{ statusLabel }}</p>
                    <div v-if="editable" class="flex flex-wrap gap-2">
                        <Button type="button" :label="t('site.imageUpload')" icon="pi pi-upload" size="small" variant="outlined" :loading="busy" @click="pickFile" />
                        <Button v-if="svg" type="button" :label="svgLabel" icon="pi pi-code" size="small" variant="outlined" :disabled="busy" :aria-expanded="svgOpen" @click="emit('openSvg')" />
                        <!-- 올려 둔 게 있을 때만 — 기본이면 되돌릴 것이 없다 -->
                        <Button v-if="custom" type="button" :label="resetLabel" icon="pi pi-undo" size="small" severity="danger" variant="text" :disabled="busy" @click="emit('reset')" />
                    </div>
                    <p :id="hintId" class="text-xs leading-relaxed text-muted-color">{{ hint }}</p>
                    <input ref="fileInput" type="file" :accept="UtilSiteImage.ACCEPT" class="hidden" tabindex="-1" :aria-label="title" @change="onFileChange" />
                </div>
            </div>

            <slot />

            <!-- 서버가 거절한 문구는 가공하지 않고 그대로 보인다(화면이 먼저 거른 문구도 같은 자리) -->
            <div aria-live="polite">
                <p v-if="error" role="alert" class="text-xs leading-relaxed text-red-600 dark:text-red-400">{{ error }}</p>
            </div>
        </template>
    </div>
</template>

<script setup lang="ts">
import UtilSiteImage from '~/utils/util-site-image';

/** 모양: 아이콘은 정사각(`square`), 링크 미리보기 이미지는 1200:630 가로로 긴 모양(`wide`) */
type Shape = 'square' | 'wide';

interface Props {
    /** 소제목 — 줄의 이름(`aria-labelledby`)이기도 하다 */
    title: string;
    /** 쓰이는 곳 한 줄 */
    help?: string;
    /** 규칙 한 줄 힌트 — 타일의 설명(`aria-describedby`)이기도 하다 */
    hint: string;
    shape?: Shape;
    /** 지금 보일 그림 주소 — 올려 둔 것이 있으면 그것, 없으면 기본 그림. 아직 모르면 빈 글자 */
    imageUrl?: string;
    /** 올려 둔 것이 있는가 — 있을 때만 [되돌리기]를 보인다 */
    custom?: boolean;
    /** 상태 한 줄("지금 쓰는 아이콘" · "지금은 기본 아이콘이에요") */
    statusLabel: string;
    /** 타일 단추의 접근성 이름 */
    tileLabel: string;
    /** [되돌리기] 단추 글자 */
    resetLabel: string;
    /** 바꿀 권한이 있는가(`site.manage`) — 없으면 단추를 감춘다 */
    editable?: boolean;
    /** 올리기·되돌리기 중 */
    busy?: boolean;
    /** 상태를 불러오는 중 */
    loading?: boolean;
    /** 상태를 불러오지 못했을 때 서버 문구 — 빈 글자면 오류가 아니다 */
    loadError?: string;
    /** 불러오지 못했을 때 제목. `loadError` 가 있을 때만 쓴다 */
    loadFailedTitle?: string;
    /** 이 줄 아래 거절 문구 — 없으면 빈 글자 */
    error?: string;
    /** [SVG 소스로 설정] 단추를 둔다(아이콘만) */
    svg?: boolean;
    svgLabel?: string;
    /** SVG 소스 칸이 열려 있다 */
    svgOpen?: boolean;
}

const props = withDefaults(defineProps<Props>(), {
    help: '',
    shape: 'square',
    imageUrl: '',
    custom: false,
    editable: false,
    busy: false,
    loading: false,
    loadError: '',
    loadFailedTitle: '',
    error: '',
    svg: false,
    svgLabel: '',
    svgOpen: false,
});

const emit = defineEmits<{ pick: [file: File]; openSvg: []; reset: []; retry: [] }>();

const { t } = useI18n();
const titleId = useId();
const hintId = useId();
const fileInput = useTemplateRef<HTMLInputElement>('fileInput');
/** 그림이 안 떠도(깨진 주소) 단추는 남긴다 — 다시 올릴 수 있어야 한다 */
const broken = ref(false);
watch(
    () => props.imageUrl,
    () => (broken.value = false),
);

function pickFile(): void {
    if (!props.editable || props.busy) return;
    fileInput.value?.click();
}

function onFileChange(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    // 고르기를 취소해도 change 가 오는 브라우저가 있다 — 그때 files 는 비어 있다
    if (file) emit('pick', file);
    // 같은 파일을 다시 고를 수 있게 비운다 — 안 비우면 change 가 안 온다
    input.value = '';
}
</script>
