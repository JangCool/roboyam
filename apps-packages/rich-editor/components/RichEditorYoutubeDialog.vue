<template>
    <!-- 유튜브 넣기(community `YoutubeSheet`) — 주소 입력 → 미리보기 → 본문에 넣기. 로보얌은 서버의 주소 확인 API 가 없어 화면이 주소에서 영상 ID 를 읽는다(`UtilRichMedia.youtubeIdOf`).
         글에 실리는 것은 `<div data-youtube-id="ID">` 뿐이고 서버가 저장 때 형식을 다시 본다 -->
    <Dialog v-model:visible="open" modal :header="t('richEditor.youtubeTitle')" :style="{ width: '30rem' }" :breakpoints="{ '640px': 'calc(100vw - 2rem)' }" :draggable="false">
        <form class="flex flex-col gap-3" novalidate @submit.prevent="onSubmit">
            <label :for="inputId" class="sr-only">{{ t('richEditor.youtubeUrl') }}</label>
            <InputText
                :id="inputId"
                v-model="url"
                type="url"
                inputmode="url"
                placeholder="https://youtu.be/…"
                autocapitalize="off"
                autocomplete="off"
                spellcheck="false"
                fluid
                autofocus
                :invalid="Boolean(error)"
                :aria-describedby="messageId"
                @input="onInput"
            />
            <p v-if="error" :id="messageId" class="m-0 text-xs text-red-600 dark:text-red-400" role="alert">{{ error }}</p>
            <p v-else :id="messageId" class="m-0 text-xs text-muted-color">{{ t('richEditor.youtubeHelp') }}</p>

            <!-- 미리보기: 글 보기에서도 누르기 전에는 이 썸네일만 보인다 -->
            <div v-if="videoId" class="rt-yt" data-testid="youtube-preview">
                <img v-if="!thumbFailed" :src="UtilRichMedia.youtubeThumbnail(videoId)" :alt="t('richEditor.youtubePreview')" decoding="async" referrerpolicy="no-referrer" @error="thumbFailed = true" />
                <span class="rt-yt-icon" aria-hidden="true" />
            </div>

            <div class="flex gap-2">
                <Button type="button" :label="t('common.cancel')" severity="secondary" variant="text" class="ml-auto" @click="open = false" />
                <Button v-if="!videoId" type="submit" :label="t('common.confirm')" :disabled="!url.trim()" />
                <Button v-else type="submit" :label="t('richEditor.youtubeInsert')" />
            </div>
        </form>
    </Dialog>
</template>

<script setup lang="ts">
import UtilRichMedia from '#layers/rich-editor/utils/util-rich-media';

const open = defineModel<boolean>('visible', { required: true });

const emit = defineEmits<{ insert: [videoId: string] }>();

const { t } = useI18n();
const inputId = useId();
const messageId = useId();

const url = ref('');
const error = ref('');
const videoId = ref<string | null>(null);
const thumbFailed = ref(false);

// 열 때마다 처음 상태로
watch(open, (visible) => {
    if (!visible) return;
    url.value = '';
    onInput();
});

/** 주소를 고치면 지난 확인 결과는 버린다 — 다시 확인해야 넣을 수 있다 */
function onInput() {
    error.value = '';
    videoId.value = null;
    thumbFailed.value = false;
}

function onSubmit() {
    if (videoId.value) {
        emit('insert', videoId.value);
        open.value = false;
        return;
    }
    if (!url.value.trim()) {
        error.value = t('richEditor.youtubeRequired');
        return;
    }
    const id = UtilRichMedia.youtubeIdOf(url.value);
    if (!id) {
        error.value = t('richEditor.youtubeInvalid');
        return;
    }
    videoId.value = id;
}
</script>
