<template>
    <!--
        공개 사이트 메뉴 항목·폴더의 「글자 색」·「강조」 칸 — 항목 창(`MenuItemDialog`)·폴더 창(`MenuFolderDialog`)이 쓴다(menu-contract 「메뉴 항목 글자 색·강조」).
        글자 색: 공용 색 고르기 줄(`PresetColorPicker` — [기본] + 13색 원 + 직접 고르기, 게시판 표시색과 같은 부품·규칙).
          글자 칸은 입력 중 틀린 글자도 값으로 들고 있다가 항목 검사(`TEXT_COLOR_FORMAT`)가 막는다 — 칸을 비우면 기본으로 돌아간다.
        강조: [굵게] 체크 · [배지] 글자 칸(4자까지, 배지 색 = 글자 색). 아래에 지금 고른 모양의 이름 견본.
        이름 앞 점(●)은 게시판 표시색을 따른다는 안내를 맨 아래에 둔다(메뉴의 글자 색과 따로) — 점이 없는 폴더 창은 `dotNote` 를 끈다.
    -->
    <div class="flex flex-col gap-4">
        <div class="flex flex-col gap-1.5">
            <span :id="`${idBase}-color`" class="text-sm font-medium">{{ t('menuConfig.field.textColor') }}</span>
            <PresetColorPicker
                v-model="model"
                :labelledby="`${idBase}-color`"
                :default-label="t('menuConfig.textColorDefault')"
                :custom-label="t('menuConfig.textColorCustom')"
                :pick-label="t('menuConfig.textColorCustomPick')"
                :input-label="t('menuConfig.textColorCustomInput')"
                testid-prefix="menu-text-color"
            />
            <p class="m-0 text-xs leading-relaxed" :class="textColorError ? 'text-red-600 dark:text-red-400' : 'text-muted-color'" :role="textColorError ? 'alert' : undefined">
                {{ textColorError || t('menuConfig.textColorHelp') }}
            </p>
        </div>

        <div class="flex flex-col gap-1.5">
            <span class="text-sm font-medium">{{ t('menuConfig.field.emphasis') }}</span>
            <div class="flex items-center gap-2">
                <Checkbox v-model="bold" binary :input-id="`${idBase}-bold`" />
                <label :for="`${idBase}-bold`" class="cursor-pointer text-sm font-semibold">{{ t('menuConfig.field.bold') }}</label>
            </div>
            <LabeledField :label="t('menuConfig.field.badge')" :hint="t('menuConfig.badgeCount', { n: badgeLength, max: UtilMenu.LIMITS.badgeMax })" :help="t('menuConfig.badgeHelp')" :error="badgeError">
                <template #default="{ id, describedBy, invalid }">
                    <InputText :id="id" v-model="badge" :invalid="invalid" :aria-describedby="describedBy" maxlength="8" placeholder="NEW" autocomplete="off" class="w-40" />
                </template>
            </LabeledField>
        </div>

        <!-- 견본 — 지금 고른 글자 색·굵게·배지로 그린 이름(관리자 화면 테마 기준, 공개 화면은 라이트·다크에 맞춰 칠한다) -->
        <div class="flex items-center gap-2 rounded-lg border border-(--line) px-3 py-2" data-testid="menu-text-style-sample">
            <span class="shrink-0 text-xs text-muted-color">{{ t('menuConfig.textStyleSample') }}</span>
            <span class="inline-flex min-w-0 items-center gap-1.5 text-sm">
                <span class="truncate" :class="bold ? 'font-semibold' : ''" :style="UtilMenu.textStyle({ textColor: model })">{{ sampleName || t('menuConfig.textStyleSampleEmpty') }}</span>
                <MenuNodeBadge :badge="badge" :text-color="model" />
            </span>
        </div>

        <p v-if="dotNote" class="m-0 flex items-start gap-1.5 text-xs leading-relaxed text-muted-color">
            <i class="pi pi-info-circle mt-0.5 text-[11px]" aria-hidden="true" />
            <span>{{ t('menuConfig.textColorDotNote') }}</span>
        </p>
    </div>
</template>

<script setup lang="ts">
import UtilMenu from '~/utils/util-menu';

/** 글자 색 — null 기본 · 13색 이름 · `#RRGGBB`(입력 중이면 틀린 글자일 수 있다) */
const model = defineModel<string | null>('textColor', { required: true });
const bold = defineModel<boolean>('bold', { required: true });
const badge = defineModel<string>('badge', { required: true });

interface Props {
    /** 견본에 보일 이름(지금 이름 또는 따라가는 대상 이름) */
    sampleName?: string;
    /** 글자 색 칸 오류(화면 검사 또는 서버 `…textColor`) */
    textColorError?: string;
    /** 배지 칸 오류(화면 검사 또는 서버 `…badge`) */
    badgeError?: string;
    /** 이름 옆 점(●)은 게시판 표시색을 따른다는 안내를 보일까(점이 있는 항목 창만) */
    dotNote?: boolean;
}

withDefaults(defineProps<Props>(), { sampleName: '', textColorError: '', badgeError: '', dotNote: true });

const { t } = useI18n();
const idBase = useId();

const badgeLength = computed(() => Array.from(badge.value.trim()).length);
</script>
