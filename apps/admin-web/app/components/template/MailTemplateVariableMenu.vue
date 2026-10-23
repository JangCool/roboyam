<template>
    <!--
        「변수 넣기」 단추 + 변수 목록(떠 있는 메뉴) — 고르면 `pick` 으로 변수 이름을 알린다. 넣는 일(제목 칸의 커서 자리 · 에디터)은 부르는 쪽이 한다.
        항목: `{{이름}}` · 설명 · 견본 값, 필수 변수는 「필수」, 링크 변수는 링크 아이콘. 메일 템플릿 편집 서랍(제목·본문)과 공통 레이아웃 구획이 같이 쓴다.
    -->
    <Button type="button" :label="label || t('mailTemplate.insertVariable')" icon="pi pi-plus" size="small" severity="secondary" variant="text" :disabled="disabled || !variables.length" aria-haspopup="true" :aria-controls="menuId" @click="toggle" />
    <Menu :id="menuId" ref="menu" :model="items" popup class="max-h-80 w-80 max-w-[calc(100vw-2rem)] overflow-y-auto">
        <template #item="{ item, props: itemProps }">
            <a v-bind="itemProps.action" class="flex min-w-0 items-start gap-2 px-3 py-2">
                <i :class="item.link ? 'pi pi-link' : 'pi pi-code'" class="mt-0.5 text-xs text-muted-color" aria-hidden="true" />
                <span class="min-w-0 flex-1">
                    <span class="flex items-center gap-1.5">
                        <code class="text-[12px]">{{ item.token }}</code>
                        <span v-if="item.required" class="inline-flex items-center justify-center rounded-full bg-amber-100 px-1.5 py-0.5 text-[10px] leading-none text-amber-800 dark:bg-amber-950 dark:text-amber-300">
                            <span class="mail-variable-badge">{{ t('mailTemplate.requiredVariable') }}</span>
                        </span>
                    </span>
                    <span class="block text-[11px] text-muted-color">{{ item.description }}</span>
                    <span v-if="item.sample" class="block truncate text-[11px] text-muted-color">{{ t('mailTemplate.sampleValue', { value: item.sample }) }}</span>
                </span>
            </a>
        </template>
    </Menu>
</template>

<script setup lang="ts">
import type { MenuItem } from 'primevue/menuitem';
import type { MailVariableView } from '~/composables/template/api/useMailTemplateApi.types';
import UtilMailTemplate from '~/utils/util-mail-template';

interface Props {
    variables: MailVariableView[];
    /** 단추 글자 — 없으면 「변수 넣기」 */
    label?: string;
    disabled?: boolean;
}

const props = withDefaults(defineProps<Props>(), { label: '', disabled: false });
const emit = defineEmits<{ pick: [name: string] }>();

const { t } = useI18n();
const menuId = useId();
const menu = ref<{ toggle: (event: Event) => void } | null>(null);

const items = computed<MenuItem[]>(() =>
    props.variables.map((variable) => ({
        key: variable.name,
        token: UtilMailTemplate.token(variable.name),
        description: variable.description,
        sample: variable.sample,
        required: variable.required,
        link: variable.link,
        command: () => emit('pick', variable.name),
    })),
);

function toggle(event: Event): void {
    menu.value?.toggle(event);
}
</script>

<style scoped>
/* 한글 글꼴 보정 — 알약 안에서 글자가 아래로 붙어 보이지 않게 글자만 올린다(components.md 2-4) */
.mail-variable-badge {
    position: relative;
    top: -0.09em;
}
</style>
