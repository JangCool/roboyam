<template>
    <!--
        AI 회원 아바타 고르기 — 계정 서랍(만들기·고치기)의 「아바타」 칸. 지금 아바타 동그라미 + [아바타 고르기] → 대화상자(카테고리 거르기 + 격자).
        고를 거리(`choices`)는 부르는 쪽이 AI 회원 고르기 목록(`GET /api/admin/ai-members/options` 의 `avatars` — 카탈로그 정지 아바타 전부, 무료 포함)으로 받아 내려 준다 — 서버를 부르지 않는다(components.md).
        권한이 없어 못 받았으면(`unavailable`) 단추를 끄고 한 줄 안내 — 지금 아바타는 그대로 남는다(서버는 `avatarId` 가 null 이면 바꾸지 않는다).
        그림은 `PointShopAvatarThumb`(`<img>` 로만).
    -->
    <div class="flex flex-wrap items-center gap-3">
        <PointShopAvatarThumb v-if="modelValue" :avatar-id="modelValue" size="lg" :alt="currentName" />
        <span v-else class="inline-grid size-20 place-items-center rounded-full bg-surface-100 text-xs text-muted-color dark:bg-surface-800">{{ t('aiMember.avatarNone') }}</span>
        <div class="flex min-w-0 flex-col items-start gap-1">
            <Button type="button" icon="pi pi-images" :label="t('aiMember.avatarPick')" size="small" severity="secondary" variant="outlined" :disabled="disabled || unavailable" :loading="loading" :aria-describedby="describedBy" @click="openDialog" />
            <span v-if="currentName" class="text-xs text-muted-color">{{ currentName }}</span>
            <span v-if="unavailable" class="text-xs text-muted-color">{{ t('aiMember.avatarUnavailable') }}</span>
        </div>

        <Dialog v-model:visible="dialogOpen" modal :header="t('aiMember.avatarPick')" :style="{ width: '44rem', maxWidth: 'calc(100vw - 2rem)' }">
            <div class="flex flex-col gap-3">
                <div class="flex flex-col gap-1.5">
                    <label :for="categoryId" class="text-xs font-medium text-muted-color">{{ t('aiMember.avatarCategory') }}</label>
                    <Select v-model="category" :input-id="categoryId" :options="categoryOptions" option-label="label" option-value="value" class="w-56" />
                </div>
                <ul class="m-0 grid max-h-[55vh] list-none grid-cols-[repeat(auto-fill,minmax(5.5rem,1fr))] gap-2 overflow-y-auto p-0">
                    <li v-for="item in visible" :key="item.avatarId">
                        <button
                            type="button"
                            class="flex w-full cursor-pointer flex-col items-center gap-1 rounded-lg border px-1 py-2 text-center hover:bg-surface-50 focus-visible:outline-2 focus-visible:outline-primary dark:hover:bg-surface-900"
                            :class="item.avatarId === modelValue ? 'border-primary' : 'border-(--line)'"
                            :aria-pressed="item.avatarId === modelValue"
                            @click="pick(item.avatarId)"
                        >
                            <PointShopAvatarThumb :avatar-id="item.avatarId" size="md" />
                            <span class="line-clamp-2 text-[11px] leading-tight">{{ item.name }}</span>
                        </button>
                    </li>
                </ul>
            </div>
        </Dialog>
    </div>
</template>

<script setup lang="ts">
import type { AiAvatarChoice } from '~/components/aimember/AiAvatarPicker.types';

const modelValue = defineModel<string | null>({ default: null });

interface Props {
    choices?: AiAvatarChoice[] | null;
    loading?: boolean;
    /** 카탈로그를 받을 수 없다(권한 없음 등) */
    unavailable?: boolean;
    disabled?: boolean;
    describedBy?: string;
}

const props = withDefaults(defineProps<Props>(), { choices: null, loading: false, unavailable: false, disabled: false, describedBy: undefined });

const emit = defineEmits<{ open: [] }>();

const { t, te } = useI18n();
const categoryId = useId();

const dialogOpen = ref(false);
const category = ref<string>('ALL');

const currentName = computed(() => (modelValue.value ? (props.choices?.find((c) => c.avatarId === modelValue.value)?.name ?? '') : ''));

function categoryLabel(id: string): string {
    const key = `pointShop.avatarCategory.${id}`;
    return te(key) ? t(key) : id;
}

const categoryOptions = computed(() => {
    const ids: string[] = [];
    for (const c of props.choices ?? []) if (c.category && !ids.includes(c.category)) ids.push(c.category);
    return [{ value: 'ALL', label: t('aiMember.avatarCategoryAll') }, ...ids.map((value) => ({ value, label: categoryLabel(value) }))];
});

const visible = computed(() => (props.choices ?? []).filter((c) => category.value === 'ALL' || c.category === category.value));

function openDialog(): void {
    // 고를 거리는 처음 열 때 부르는 쪽이 받는다
    emit('open');
    const current = props.choices?.find((c) => c.avatarId === modelValue.value);
    category.value = current?.category ?? 'ALL';
    dialogOpen.value = true;
}

function pick(avatarId: string): void {
    modelValue.value = avatarId;
    dialogOpen.value = false;
}
</script>
