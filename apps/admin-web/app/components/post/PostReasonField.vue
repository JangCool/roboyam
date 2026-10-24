<template>
    <!-- 조치 사유 칸(1~200자 필수) — 글 하나의 조치·이동·일괄 대화창이 같이 쓴다. 오류는 화면 검사 문구 또는 서버 `fields.reason` 문구 그대로 -->
    <LabeledField :label="t('post.reasonLabel')" :hint="t('post.reasonCounter', { n: UtilPost.reasonLength(reason), max: UtilPost.REASON_MAX })" :help="t('post.reasonHelp')" :error="error">
        <template #default="{ id, describedBy, invalid }">
            <Textarea :id="id" v-model="reason" name="reason" rows="3" auto-resize fluid :placeholder="t('post.reasonPlaceholder')" :disabled="disabled" :invalid="invalid" :aria-describedby="describedBy" />
        </template>
    </LabeledField>
</template>

<script setup lang="ts">
import UtilPost from '~/utils/util-post';

const reason = defineModel<string>({ required: true });

interface Props {
    error?: string;
    disabled?: boolean;
}

withDefaults(defineProps<Props>(), { error: '', disabled: false });

const { t } = useI18n();
</script>
