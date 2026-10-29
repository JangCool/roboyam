<template>
    <!--
        연결 대상 고르기 — 항목 시트(`MenuItemDialog`)와 폴더 시트(`MenuFolderDialog`)의 「누르면 가는 곳」이 같이 쓴다. 모양은 유형(`targetType`)이 정한다:
          · 게시판 — 게시판 목록(`linkedNote` 가 글자를 주면 비활성 + 그 안내, 꺼진 게시판은 딱지)
          · 사이트 화면 — [목록에서 고르기](`BUILTIN`, 카테고리별 앱 화면) · [주소 직접 입력](`PATH`). 둘을 오가도 고른 화면·적은 주소는 남는다(저장은 지금 유형의 칸만).
            직접 입력으로 넘어갈 때 주소가 비었으면 고른 화면의 주소를 채우고, 목록으로 돌아갈 때 주소가 목록의 화면과 같으면 그 화면을 고른다.
          · 외부 링크 — 주소 입력
        사이트 안 주소·외부 링크는 「새 탭에서 열기」를 같이 둔다.
    -->
    <div class="flex flex-col gap-3">
        <!-- 게시판 -->
        <div v-if="targetType === 'BOARD'" class="flex flex-col gap-1.5">
            <span :id="`${uid}-target`" class="text-sm font-medium">{{ t('menuConfig.field.board') }}</span>
            <Listbox
                v-model="targetId"
                :options="boardOptions"
                option-label="label"
                option-value="value"
                option-disabled="disabled"
                filter
                :filter-fields="['label', 'slug']"
                :filter-placeholder="t('menuConfig.boardSearch')"
                scroll-height="15rem"
                :invalid="Boolean(errors.target)"
                :aria-labelledby="`${uid}-target`"
                :empty-message="t('menuConfig.boardEmpty')"
            >
                <template #option="{ option }">
                    <span class="flex w-full min-w-0 items-center gap-2">
                        <span class="min-w-0 flex-1 truncate text-[13px]">{{ option.label }} <code class="text-[11px] text-muted-color">/boards/{{ option.slug }}</code></span>
                        <MenuTag v-if="!option.active" :value="t('menuConfig.boardInactive')" severity="warn" />
                        <span v-if="option.note" class="shrink-0 text-[11px] text-muted-color">{{ option.note }}</span>
                    </span>
                </template>
            </Listbox>
            <p v-if="errors.target" class="m-0 text-xs text-red-600 dark:text-red-400" role="alert">{{ errors.target }}</p>
        </div>

        <!-- 사이트 화면 — 목록에서 고르기 · 주소 직접 입력 -->
        <template v-else-if="targetType === 'BUILTIN' || targetType === 'PATH'">
            <SelectButton :model-value="siteMode" :options="siteModes" option-label="label" option-value="value" :allow-empty="false" size="small" :aria-label="t('menuConfig.siteModeLabel')" class="self-start" @update:model-value="setSiteMode" />

            <div v-if="targetType === 'BUILTIN'" class="flex flex-col gap-1.5">
                <span :id="`${uid}-target`" class="text-sm font-medium">{{ t('menuConfig.field.builtin') }}</span>
                <Listbox
                    :model-value="targetKey"
                    :options="builtinGroups"
                    option-label="label"
                    option-value="value"
                    option-disabled="disabled"
                    option-group-label="label"
                    option-group-children="items"
                    filter
                    :filter-fields="['label', 'path']"
                    :filter-placeholder="t('menuConfig.builtinSearch')"
                    scroll-height="15rem"
                    :invalid="Boolean(errors.target)"
                    :aria-labelledby="`${uid}-target`"
                    @update:model-value="pickBuiltin"
                >
                    <template #optiongroup="{ option }">
                        <span class="text-xs font-semibold text-muted-color">{{ option.label }}</span>
                    </template>
                    <template #option="{ option }">
                        <span class="flex w-full min-w-0 items-center gap-2">
                            <span class="min-w-0 flex-1 truncate text-[13px]">{{ option.label }} <code class="text-[11px] text-muted-color">{{ option.path }}</code></span>
                            <MenuTag v-if="option.login" :value="t('menuConfig.loginRequired')" icon="pi pi-lock" />
                            <span v-if="option.note" class="shrink-0 text-[11px] text-muted-color">{{ option.note }}</span>
                        </span>
                    </template>
                </Listbox>
                <p v-if="errors.target" class="m-0 text-xs text-red-600 dark:text-red-400" role="alert">{{ errors.target }}</p>
            </div>

            <LabeledField v-else :label="t('menuConfig.field.path')" :help="t('menuConfig.pathHelp')" :error="errors.path ?? ''">
                <template #default="{ id, describedBy, invalid }">
                    <InputText :id="id" v-model="path" :invalid="invalid" :aria-describedby="describedBy" :aria-required="true" placeholder="/boards/free" autocomplete="off" autocapitalize="off" spellcheck="false" fluid />
                </template>
            </LabeledField>
        </template>

        <!-- 외부 링크 -->
        <LabeledField v-else-if="targetType === 'URL'" :label="t('menuConfig.field.href')" :help="t('menuConfig.hrefHelp')" :error="errors.href ?? ''">
            <template #default="{ id, describedBy, invalid }">
                <InputText :id="id" v-model="href" :invalid="invalid" :aria-describedby="describedBy" :aria-required="true" :placeholder="HREF_PLACEHOLDER" autocomplete="off" autocapitalize="off" spellcheck="false" fluid />
            </template>
        </LabeledField>

        <div v-if="targetType === 'PATH' || targetType === 'URL'" class="flex items-center gap-2">
            <ToggleSwitch v-model="newTab" :input-id="`${uid}-newtab`" />
            <label :for="`${uid}-newtab`" class="cursor-pointer text-sm">{{ t('menuConfig.field.newTab') }}</label>
        </div>
    </div>
</template>

<script setup lang="ts">
import type { MenuContext, MenuProblemField } from '~/utils/util-menu';
import UtilMenu from '~/utils/util-menu';

type SiteMode = 'LIST' | 'INPUT';

interface Props {
    ctx: MenuContext;
    /** 이미 연결된 대상의 안내(`BOARD:<id>` · `BUILTIN:<key>`) — 글자가 있으면 그 줄을 비활성으로 둔다. 폴더 연결은 겹쳐도 되므로 넘기지 않는다 */
    linkedNote?: (id: string) => string;
    /** 칸 문제 문구 — 보여 줄 때만 넘긴다 */
    errors?: Partial<Record<MenuProblemField, string>>;
}

const props = withDefaults(defineProps<Props>(), { linkedNote: () => '', errors: () => ({}) });
const emit = defineEmits<{ (e: 'builtin', key: string | null): void }>();

const targetType = defineModel<string>('targetType', { required: true });
const targetId = defineModel<string | null>('targetId', { required: true });
const targetKey = defineModel<string | null>('targetKey', { required: true });
const path = defineModel<string>('path', { required: true });
const href = defineModel<string>('href', { required: true });
const newTab = defineModel<boolean>('newTab', { required: true });

const { t } = useI18n();
const { localizedName } = useLocalizedName();
const uid = useId();
/** 외부 링크 칸의 자리표시 — 템플릿의 정적 속성에 두 빗금을 쓰면 타입 검사가 그 뒤를 주석으로 읽는다 */
const HREF_PLACEHOLDER = 'https://';

const siteMode = computed<SiteMode>(() => (targetType.value === 'PATH' ? 'INPUT' : 'LIST'));
const siteModes = computed(() => (['LIST', 'INPUT'] as const).map((value) => ({ value, label: t(`menuConfig.siteMode.${value}`) })));

const boardOptions = computed(() =>
    props.ctx.boards.map((board) => {
        const note = props.linkedNote(`BOARD:${board.id}`);
        return { value: board.id, label: localizedName(board.name, board.nameEn), slug: board.slug.toLowerCase(), active: board.active, note, disabled: note !== '' };
    }),
);

const builtinGroups = computed(() =>
    UtilMenu.builtinCategoryOrder(props.ctx.placement).map((category) => ({
        label: t(`menuConfig.builtinCategory.${category}`),
        items: UtilMenu.BUILTINS.filter((def) => def.category === category).map((def) => {
            const note = props.linkedNote(`BUILTIN:${def.key}`);
            return { value: def.key, label: t(def.labelKey), path: def.path, login: def.loginRequired, note, disabled: note !== '' };
        }),
    })),
);

function pickBuiltin(key: string | null) {
    targetKey.value = key;
    emit('builtin', key);
}

/** 목록 ↔ 직접 입력 — 고른 화면과 적은 주소는 서로 이어 준다 */
function setSiteMode(mode: SiteMode | null) {
    if (!mode || mode === siteMode.value) return;
    if (mode === 'INPUT') {
        const def = UtilMenu.builtinOf(targetKey.value);
        if (!path.value.trim() && def) path.value = def.path;
        targetType.value = 'PATH';
        return;
    }
    const same = UtilMenu.builtinByPath(path.value);
    if (same && !props.linkedNote(`BUILTIN:${same.key}`)) targetKey.value = same.key;
    targetType.value = 'BUILTIN';
    emit('builtin', targetKey.value);
}
</script>
