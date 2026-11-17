<template>
    <!--
        칭호 칩 — 도트 그림(`TitleIcon`) + 칭호 이름(시안 `member-title-icons-mockup.html` 의 `.tt` 알약). 칩 색은 서버 `chip`(보라 = 수여·심사 · 초록 = 자동).
        크기: 작성자 줄 1배(`scale` 1, 글자 11px) · 공개 프로필·내 정보 2배(`scale` 2, 글자 13px). 같은 칭호 여러 번이면 「이달의 작가 ×4」(`count`).
        읽는 프로그램은 칩 글자(칭호 이름)를 읽고 그림은 장식이다. 글자는 한글이 아래로 앉는 만큼(0.09em) 올린다(`components.md` 2-4).
    -->
    <span
        class="inline-flex max-w-full shrink-0 items-center rounded-md font-medium leading-none whitespace-nowrap"
        :class="[UtilMemberTitle.chipClass(title.chip), scale >= 2 ? 'gap-1.5 py-1 pr-2 pl-1 text-[0.8125rem]' : 'gap-1 py-0.5 pr-1.5 pl-1 text-[0.6875rem]']"
        :data-title-key="title.key"
        data-testid="title-chip"
    >
        <TitleIcon :icon="title.icon" :tier="title.tier" :scale="scale" />
        <span class="relative top-[-0.09em] truncate">{{ text }}</span>
    </span>
</template>

<script setup lang="ts">
import type { AuthorTitle } from '~/composables/title/api/useTitleApi.types';
import UtilMemberTitle from '~/utils/util-member-title';

interface Props {
    title: Pick<AuthorTitle, 'key' | 'label' | 'labelEn' | 'icon' | 'tier' | 'chip'>;
    /** 그림 배율 — 1 작성자 줄 · 2 공개 프로필·내 정보 */
    scale?: number;
    /** 받은 횟수 — 2 이상이면 「×N」 */
    count?: number;
}

const props = withDefaults(defineProps<Props>(), { scale: 1, count: 1 });

const { t } = useI18n();
const { localizedName } = useLocalizedName();
/** 「이달의 작가 ×4」 — 작성자 줄(첫 화면)에서도 쓰여 문구 유틸을 거치지 않고 키 하나만 쓴다*/
const text = computed(() => {
    const label = localizedName(props.title.label, props.title.labelEn ?? null);
    return props.count > 1 ? t('memberTitle.countLabel', { label, count: props.count }) : label;
});
</script>
