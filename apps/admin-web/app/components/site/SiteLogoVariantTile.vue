<template>
    <!--
        로고 4칸 중 한 칸(일반 화면 「로고」) — 칸 이름 → [가로로 넓은 미리보기 타일] → 상태 한 줄 → 단추 열(이미지 업로드 · SVG 소스로 설정 · 제거) → 거절 문구.
        미리보기 바탕은 칸 성격대로 — 밝은 바탕용 = 흰 면(`surface-0`), 어두운 바탕용 = 진한 회색 면(`surface-900`, 야간 모드·주 색상 바를 대신하는 한 색).
        칸이 비어 있으면 실제로 대신 쓰이는 그림(대체 순서 `UtilSiteLogo.resolve`)을 흐리게, 그것도 없으면 사이트 이름 글자를 흐리게 보인다 — 무엇이 대신 쓰이는지는 상태 한 줄이 말한다.
        서버는 부르지 않는다 — 고른 파일은 `pick`, SVG 소스 열기는 `openSvg`, 떼기는 `remove` 로 알리고 검사·호출·확인창은 페이지가 맡는다(`SiteImageField` 와 같은 결).
        미리보기 타일은 `<button>`(누르면 파일 선택). 권한이 없으면(`editable=false`) 단추를 감추고 타일은 눌러지지 않는다. 그림은 `<img src>` 하나로만 그린다(`v-html` 금지).
    -->
    <div role="group" :aria-labelledby="titleId" class="flex min-w-0 flex-col gap-1.5 rounded-xl border border-(--line) p-3">
        <h4 :id="titleId" class="text-xs font-semibold">{{ title }}</h4>

        <button
            v-tooltip.top="editable ? t('site.logoPreviewTooltip') : undefined"
            type="button"
            class="flex h-16 w-full items-center justify-center overflow-hidden rounded-lg border border-(--line) px-3 transition hover:border-primary focus-visible:outline-2 focus-visible:outline-primary disabled:cursor-default disabled:hover:border-(--line)"
            :class="tone === 'dark' ? 'bg-surface-900 text-surface-0' : 'bg-surface-0 text-surface-900'"
            :disabled="!editable || busy"
            :aria-label="tileLabel"
            :aria-describedby="statusId"
            @click="pickFile"
        >
            <img v-if="previewUrl && !broken" :src="previewUrl" alt="" class="block h-8 w-auto max-w-full object-contain" :class="fallback ? 'opacity-35' : ''" @error="broken = true" />
            <!-- 대신 쓸 그림도 없으면 공개 헤더처럼 이름 글자 -->
            <span v-else-if="fallback && !previewUrl" class="min-w-0 truncate text-base font-bold tracking-wider opacity-50">{{ nameText }}</span>
            <i v-else class="pi pi-image text-lg opacity-60" aria-hidden="true" />
        </button>

        <p :id="statusId" class="text-xs leading-relaxed" :class="fallback ? 'text-muted-color' : 'font-medium'">{{ statusLabel }}</p>

        <!-- 모바일 미리보기 — 공개 헤더가 모바일(md 미만)에서 그리는 그대로(같은 그림 + `#wordmark`, 높이 28px). 영역이 없으면 그림 전체가 보이는 것도 그대로 -->
        <div v-if="previewUrl && !broken" class="flex min-w-0 items-center gap-2">
            <span class="shrink-0 text-xs text-muted-color">{{ t('site.logoMobile') }}</span>
            <span
                class="flex h-10 min-w-0 items-center overflow-hidden rounded-md border border-(--line) px-2"
                :class="tone === 'dark' ? 'bg-surface-900' : 'bg-surface-0'"
                data-testid="logo-mobile-preview"
            >
                <img :src="mobileUrl" alt="" class="block h-7 w-auto max-w-[45vw] object-contain" :class="fallback ? 'opacity-35' : ''" />
            </span>
            <span class="min-w-0 text-xs leading-snug text-muted-color">{{ wordmark ? t('site.logoMobileWordmark') : t('site.logoMobileFull') }}</span>
        </div>

        <div v-if="editable" class="flex flex-wrap gap-1.5">
            <Button type="button" :label="t('site.logoUpload')" icon="pi pi-upload" size="small" variant="outlined" :loading="busy" @click="pickFile" />
            <Button type="button" :label="t('site.logoFromSvg')" icon="pi pi-code" size="small" variant="outlined" :disabled="busy" :aria-expanded="svgOpen" @click="emit('openSvg')" />
            <!-- 이 칸에 올려 둔 게 있을 때만 — 비어 있으면 뗄 것이 없다 -->
            <Button v-if="!fallback" type="button" :label="t('site.logoRemove')" icon="pi pi-trash" size="small" severity="danger" variant="text" :disabled="busy" @click="emit('remove')" />
        </div>
        <input ref="fileInput" type="file" :accept="UtilSiteSetting.LOGO_ACCEPT" class="hidden" tabindex="-1" :aria-label="tileLabel" @change="onFileChange" />

        <!-- 화면이 먼저 거른 문구도, 서버가 거절한 문구(그대로)도 같은 자리 -->
        <div aria-live="polite">
            <p v-if="error" role="alert" class="text-xs leading-relaxed text-red-600 dark:text-red-400">{{ error }}</p>
        </div>
    </div>
</template>

<script setup lang="ts">
import UtilSiteLogo, { type SiteLogoTone } from '~/utils/util-site-logo';
import UtilSiteSetting from '~/utils/util-site-setting';

interface Props {
    /** 칸 이름(① 한글 · 밝은 바탕용 …) — 묶음의 이름(`aria-labelledby`)이기도 하다 */
    title: string;
    /** 미리보기 바탕 — 칸의 성격 */
    tone: SiteLogoTone;
    /** 미리보기에 보일 그림 — 이 칸 것, 비었으면 대신 쓰이는 칸의 것. 그것도 없으면 null */
    previewUrl?: string | null;
    /** 이 칸이 비어 있다(미리보기는 대신 쓰이는 그림 — 흐리게) */
    fallback?: boolean;
    /** 상태 한 줄("이 칸의 로고를 써요" · "비어 있음 — ①을 대신 써요" …) */
    statusLabel: string;
    /** 대신 쓸 그림도 없을 때 미리보기에 보일 이름 글자 */
    nameText: string;
    /** 타일 단추의 접근성 이름 */
    tileLabel: string;
    /** 바꿀 권한이 있는가(`site.manage`) — 없으면 단추를 감춘다 */
    editable?: boolean;
    /** 올리기·떼기 중 */
    busy?: boolean;
    /** 이 칸의 SVG 소스 칸이 열려 있다 */
    svgOpen?: boolean;
    /** 이 칸 아래 거절 문구 — 없으면 빈 글자 */
    error?: string;
}

const props = withDefaults(defineProps<Props>(), {
    previewUrl: null,
    fallback: false,
    editable: false,
    busy: false,
    svgOpen: false,
    error: '',
});

const emit = defineEmits<{ pick: [file: File]; openSvg: []; remove: [] }>();

const { t } = useI18n();
const titleId = useId();
const statusId = useId();
const fileInput = useTemplateRef<HTMLInputElement>('fileInput');
/** 그림이 안 떠도(깨진 주소) 단추는 남긴다 — 다시 올릴 수 있어야 한다 */
const broken = ref(false);
watch(
    () => props.previewUrl,
    () => (broken.value = false),
);

/** 모바일 미리보기 주소 — 같은 그림에 `#wordmark` */
const mobileUrl = computed(() => (props.previewUrl ? UtilSiteLogo.wordmarkUrl(props.previewUrl) : ''));
/** 모바일에서 글자 부분만 보이나(SVG 안 영역 확인) — 안내 한 줄을 고른다 */
const wordmark = ref(false);
watch(
    () => props.previewUrl,
    async (url) => {
        wordmark.value = false;
        if (!url) return;
        const ok = await UtilSiteLogo.wordmarkOf(url);
        // 그 사이 그림이 바뀌었으면 옛 결과는 버린다
        if (props.previewUrl === url) wordmark.value = ok;
    },
    { immediate: true },
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
