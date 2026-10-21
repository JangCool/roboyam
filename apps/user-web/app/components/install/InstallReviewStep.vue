<template>
    <div class="flex flex-col gap-4">
        <!-- 설치가 끝나지 않았다 — 서버 문구 그대로(비밀 값은 담기지 않는다) -->
        <Message v-if="error || unplaced.length" severity="error" :closable="false" size="small" data-testid="install-error">
            <span v-if="error" class="block">{{ error }}</span>
            <span v-for="message in unplaced" :key="message" class="block">{{ message }}</span>
        </Message>

        <section v-for="section in sections" :key="section.step" class="rounded-xl border border-surface p-3.5" :aria-labelledby="`${uid}-${section.step}`" :data-step="section.step" data-testid="review-section">
            <header class="flex items-center gap-2">
                <h3 :id="`${uid}-${section.step}`" class="min-w-0 flex-1 text-sm font-semibold">{{ section.title }}</h3>
                <span v-if="section.skipped" class="shrink-0 rounded bg-surface-100 px-1.5 py-px text-xs font-semibold leading-5 text-surface-600 dark:bg-surface-800 dark:text-surface-300" data-testid="review-skipped">
                    {{ t('install.skipped') }}
                </span>
                <Button
                    type="button"
                    :label="section.skipped ? t('install.reviewFill') : t('common.edit')"
                    size="small"
                    variant="text"
                    :disabled="disabled"
                    :aria-label="section.skipped ? t('install.reviewFillLabel', { title: section.title }) : t('install.reviewEditLabel', { title: section.title })"
                    data-testid="review-edit"
                    @click="emit('edit', section.step)"
                />
            </header>
            <dl v-if="!section.skipped" class="m-0 mt-2 flex flex-col gap-1.5">
                <div v-for="row in section.rows" :key="row.label" class="flex gap-3 text-sm">
                    <dt class="w-28 shrink-0 text-muted-color">{{ row.label }}</dt>
                    <dd class="m-0 min-w-0 flex-1 break-all" :class="row.secret ? 'text-muted-color' : ''" :data-secret="row.secret ? 'true' : undefined" data-testid="review-value">
                        {{ row.value }}
                    </dd>
                </div>
            </dl>
        </section>

        <p class="text-xs leading-relaxed text-muted-color">{{ t('install.installNote') }}</p>
    </div>
</template>

<script setup lang="ts">
import type { InstallStepKey } from '~/utils/util-install';
import type { ReviewSection } from '~/utils/util-install-messages';

/**
 * 7단계 — 확인. 입력한 내용을 단계별로 요약해서 보여 준다. **비밀번호·시크릿은 값 대신 "입력함"** 만 — 값은 이 화면에 오지도 않는다(`UtilInstallMessages.review`가 가린다).
 * 건너뛴 항목은 "건너뜀" 이고 "입력하기" 로 그 단계에 돌아간다. 설치 버튼과 중복 클릭 방지는 부모의 단계 막대가 맡는다.
 */
interface Props {
    sections: ReviewSection[];
    /** 설치 요청이 거절된 까닭(서버 문구) */
    error?: string;
    /** 어느 단계에도 속하지 않는 서버 오류 문구 */
    unplaced: string[];
    /** 설치 중이라 수정을 잠근다 */
    disabled?: boolean;
}

const props = withDefaults(defineProps<Props>(), {
    error: '',
    disabled: false,
});

const emit = defineEmits<{ edit: [step: InstallStepKey] }>();

const uid = useId();
const { t } = useI18n();
</script>
