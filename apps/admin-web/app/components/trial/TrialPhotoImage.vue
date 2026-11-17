<template>
    <!--
        비공개 체험단·구매 인증 사진 한 장(V243 — 점검 D3). 서버 `url` 은 권한 API(바이트·`no-store`)라 `<img src>` 로 바로 걸지 않고
        `useTrialPhotoSource` 가 받아 objectURL 로 그리고, 주소가 바뀌거나 이 부품이 사라지면 해제한다.
        `purged`(보관 기한이 지나 지움) 이거나 서버가 410 을 주면 「보관 기간 지남」 자리 표시, 받지 못하면 경고 자리 표시(마우스를 올리면 서버 문구).
        `zoomable` 이면 눌러 크게 본다(받은 그림을 그대로 — 다시 받지 않는다).
    -->
    <span class="inline-flex shrink-0">
        <span v-if="shown === 'purged'" role="img" :aria-label="t('trial.photoView.purged')" :title="t('trial.photoView.purged')" class="inline-flex flex-col items-center justify-center gap-0.5 rounded bg-surface-100 text-center text-muted-color dark:bg-surface-800" :class="boxClass">
            <i class="pi pi-image text-xs" aria-hidden="true" />
            <span class="px-0.5 text-[10px] leading-tight">{{ t('trial.photoView.purgedShort') }}</span>
        </span>
        <Skeleton v-else-if="shown === 'loading'" :class="boxClass" />
        <span v-else-if="shown === 'error'" role="img" :aria-label="t('trial.photoView.failed')" :title="errorMessage || t('trial.photoView.failed')" class="inline-flex items-center justify-center rounded bg-surface-100 text-red-600 dark:bg-surface-800 dark:text-red-400" :class="boxClass">
            <i class="pi pi-exclamation-circle text-xs" aria-hidden="true" />
        </span>
        <button v-else-if="zoomable" type="button" class="block overflow-hidden rounded border border-(--line) p-0 focus-visible:outline-2 focus-visible:outline-(--p-primary-color)" :class="boxClass" :aria-label="t('trial.photoView.open')" @click="zoomed = true">
            <img :src="src ?? undefined" :alt="alt" class="size-full" :class="fit === 'contain' ? 'object-contain' : 'object-cover'" />
        </button>
        <img v-else :src="src ?? undefined" :alt="alt" class="rounded" :class="[boxClass, fit === 'contain' ? 'object-contain' : 'object-cover']" />

        <Dialog v-if="zoomable" v-model:visible="zoomed" modal :header="t('trial.photoView.title')" :style="{ width: 'min(48rem, calc(100vw - 2rem))' }" :draggable="false">
            <p class="m-0 mb-2 text-xs text-muted-color">{{ t('trial.queue.photoPrivate') }}</p>
            <img v-if="src" :src="src" :alt="alt" class="max-h-[70vh] w-full object-contain" />
        </Dialog>
    </span>
</template>

<script setup lang="ts">
interface Props {
    /** 서버가 준 사진 주소(권한 API) — 없으면(파기) 자리 표시 */
    url: string | null;
    /** 보관 기한이 지나 지운 사진(서버 `purged`) */
    purged?: boolean;
    alt?: string;
    /** 그림 상자 크기 클래스(예: `size-14`) */
    boxClass?: string;
    fit?: 'cover' | 'contain';
    /** 눌러서 크게 보기 */
    zoomable?: boolean;
}

const props = withDefaults(defineProps<Props>(), { purged: false, alt: '', boxClass: 'size-14', fit: 'cover', zoomable: false });

const { t } = useI18n();
const zoomed = ref(false);
const { src, state, errorMessage } = useTrialPhotoSource(() => (props.purged ? null : props.url));

const shown = computed<'purged' | 'loading' | 'error' | 'ready'>(() => {
    if (props.purged || state.value === 'purged' || (state.value === 'none' && !props.url)) return 'purged';
    if (state.value === 'error') return 'error';
    if (state.value === 'ready' && src.value) return 'ready';
    return 'loading';
});
</script>
