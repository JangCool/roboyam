<template>
    <!--
        조회 조건 한 줄 — 왼쪽 라벨 열(고정폭) + 오른쪽 입력들(그누보드 회원 조회 필터의 줄 모양). 좁은 화면(md 미만)은 라벨이 위로 올라가 한 열이다.
        줄 전체가 `role="group"` 이고 라벨이 그 이름이다 — 체크·라디오 묶음과 입력 둘 이상인 줄(기간·범위)이 스크린리더에 "무엇의 입력"인지 전한다.
        입력이 하나뿐이면 `labelFor`(그 입력의 id)를 줘 라벨을 `<label>` 로 그린다. 입력 슬롯은 `labelId` 를 받아 안쪽 묶음의 `aria-labelledby` 로 쓸 수 있다.
    -->
    <div role="group" :aria-labelledby="labelId" class="grid items-start gap-x-4 gap-y-1 md:grid-cols-[7.5rem_minmax(0,1fr)]">
        <component :is="labelFor ? 'label' : 'span'" :id="labelId" :for="labelFor" class="text-[13px] leading-8 font-normal text-muted-color">{{ label }}</component>
        <div class="flex min-h-8 min-w-0 flex-wrap items-center gap-x-4 gap-y-2">
            <slot :label-id="labelId" />
        </div>
    </div>
</template>

<script setup lang="ts">
interface Props {
    /** 라벨 글자 */
    label: string;
    /** 입력이 하나뿐일 때 그 입력의 id — 라벨을 누르면 입력에 포커스가 간다 */
    labelFor?: string;
}

withDefaults(defineProps<Props>(), { labelFor: undefined });

const labelId = useId();
</script>
