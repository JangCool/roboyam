<template>
    <!-- 링크 넣기(community `RichEditor` 의 링크 시트) — 앱의 시트 부품(`AppSheet`)은 레이어가 쓸 수 없어 PrimeVue `Dialog` 로 그린다. 주소 검사·적용은 부모가 한다 -->
    <Dialog v-model:visible="open" modal :header="t('richEditor.linkTitle')" :style="{ width: '28rem' }" :breakpoints="{ '640px': 'calc(100vw - 2rem)' }" :draggable="false">
        <form class="flex flex-col gap-3" novalidate @submit.prevent="emit('apply', url)">
            <p class="m-0 text-sm text-muted-color">{{ t('richEditor.linkDescription') }}</p>
            <label :for="inputId" class="sr-only">{{ t('richEditor.linkUrl') }}</label>
            <InputText
                :id="inputId"
                v-model="url"
                type="url"
                inputmode="url"
                placeholder="https://"
                autocapitalize="off"
                spellcheck="false"
                fluid
                autofocus
                :invalid="Boolean(error)"
                :aria-describedby="error ? errorId : undefined"
            />
            <p v-if="error" :id="errorId" class="m-0 text-xs text-red-600 dark:text-red-400" role="alert">{{ error }}</p>
            <div class="flex gap-2">
                <Button v-if="hasLink" type="button" :label="t('richEditor.linkRemove')" severity="secondary" variant="outlined" @click="emit('remove')" />
                <Button type="button" :label="t('common.cancel')" severity="secondary" variant="text" class="ml-auto" @click="open = false" />
                <Button type="submit" :label="t('common.apply')" />
            </div>
        </form>
    </Dialog>
</template>

<script setup lang="ts">
interface Props {
    /** 열 때 채워 둘 주소(이미 링크가 걸린 글자) */
    initialUrl?: string;
    /** 이미 링크가 걸려 있다 — 「링크 해제」 단추 */
    hasLink?: boolean;
    error?: string;
}

const props = withDefaults(defineProps<Props>(), { initialUrl: '', hasLink: false, error: '' });

const open = defineModel<boolean>('visible', { required: true });

const emit = defineEmits<{ apply: [url: string]; remove: [] }>();

const { t } = useI18n();
const inputId = useId();
const errorId = useId();
const url = ref('');

// 열 때마다 지금 링크로 채운다
watch(open, (visible) => {
    if (visible) url.value = props.initialUrl;
});
</script>
