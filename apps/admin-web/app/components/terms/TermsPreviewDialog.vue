<template>
    <!--
        약관 미리보기 — 지금 편집기 본문의 자리표시를 운영 주체 값으로 바꾼 모습(`UtilTermsPlaceholder.apply` — 화면이 바꿔 넘긴다). 시행일 `{{시행일}}` 은 게시 대화상자의 최소 시행일이다.
        모르는 자리표시(`{{…}}`)가 있으면 위에 경고한다(바꾸지 않고 글자 그대로 둔다). 서버를 부르지 않는다.
        ⚠ 본문은 이 운영자가 편집기에서 쓴 서식 HTML(편집기가 정규화한 값)에 이스케이프한 값만 끼운 것이다 — 공개 화면처럼 서버 정화본은 아니지만 관리자 자신의 글이라 `v-html` 로 그린다. 게시본은 서버가 정화한다.
    -->
    <Dialog v-model:visible="visible" modal :draggable="false" :header="t('terms.previewTitle', { name: kindLabel })" :style="{ width: 'min(48rem, calc(100vw - 2rem))' }">
        <div class="flex flex-col gap-3">
            <Message v-if="unknown.length" severity="warn" size="small" :closable="false" role="alert" data-testid="terms-preview-unknown">{{ unknownText }}</Message>
            <p class="m-0 text-xs leading-relaxed text-muted-color">{{ caption }}</p>
            <div class="max-h-[60vh] overflow-y-auto rounded-lg border border-surface p-4">
                <p v-if="!hasContent" class="m-0 text-sm text-muted-color">{{ t('terms.previewEmpty') }}</p>
                <!-- eslint-disable-next-line vue/no-v-html -->
                <div v-else class="rich-text text-sm" data-testid="terms-preview-body" v-html="html" />
            </div>
        </div>

        <template #footer>
            <Button type="button" :label="t('common.close')" severity="secondary" variant="text" @click="visible = false" />
        </template>
    </Dialog>
</template>

<script setup lang="ts">
import UtilRichText from '#layers/rich-editor/utils/util-rich-text';
interface Props {
    /** 종류 이름 */
    kindLabel: string;
    /** 자리표시를 바꾼 본문 */
    html: string;
    /** 모르는 자리표시 이름 */
    unknown?: string[];
    /** 위 안내 한 줄(어떤 운영 주체·시행일로 바꿨는지) */
    caption?: string;
}

const props = withDefaults(defineProps<Props>(), {
    unknown: () => [],
    caption: '',
});

const visible = defineModel<boolean>('visible', { required: true });

const { t } = useI18n();

const hasContent = computed(() => UtilRichText.hasContent(props.html));
/** 모르는 자리표시 목록 글자 — 템플릿 안에 중괄호 두 겹을 쓰면 끼움 글자로 읽혀 여기서 만든다 */
const unknownText = computed(() => t('terms.previewUnknown', { names: props.unknown.map((name) => `{{${name}}}`).join(', ') }));
</script>
