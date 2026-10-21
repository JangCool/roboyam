<template>
    <!-- 메뉴(세로) — 햄버거(모바일)와 왼쪽·오른쪽 메뉴(데스크톱)가 같은 데이터·같은 모양을 쓴다. 구성은 서버가 준 메뉴 트리 그대로다(`GET /api/menu`, 운영자가 메뉴 구성에서 정한다).
         연속된 최상위 항목(홈·공동구매)은 제목 없는 첫 구역, 최상위 그룹은 제목 있는 구역, 2단계 이하 그룹은 들여쓴 소제목이다(시안 25-menu M-1~M-4).
         구성(구역·활성 판정·접힘·상태)은 상단 메뉴(가로, `AppTopMenu`)와 같은 `useMenuItems` 를 쓴다 -->
    <nav :aria-label="t('menu.navLabel')" class="text-sm">
        <!-- 아직 못 받았다 — 홈·공동구매 고정 줄도 응답에서 오므로 줄 모양 스켈레톤을 그린다 -->
        <div v-if="state === 'loading'" class="space-y-3 px-4 py-3" role="status" :aria-label="t('menu.loadingLabel')">
            <div v-for="n in 8" :key="n" class="h-4 animate-pulse rounded bg-surface-200 dark:bg-surface-800" :class="n % 2 ? 'w-3/4' : 'w-1/2'" />
        </div>

        <!-- 보여 드릴 메뉴가 없다 — 로고·하단 탭은 그대로라 홈으로 가는 길만 둔다 -->
        <div v-else-if="state === 'empty'" class="px-4 py-3 text-xs text-muted-color" data-testid="menu-empty">
            <p>{{ t('menu.emptyTitle') }}</p>
            <Button type="button" :label="t('menu.emptyHome')" size="small" variant="outlined" severity="secondary" class="mt-2" @click="goHome" />
        </div>

        <template v-else>
            <!-- 서버 메뉴를 못 받았다 — 기본 메뉴를 그렸다는 안내(게시판 목록도 못 받았으면 고정 항목·소모임만 남아 오류 문구) + 다시 시도 -->
            <div
                v-if="state === 'fallback' || state === 'error'"
                class="px-4 py-3 text-xs text-muted-color"
                :role="state === 'error' ? 'alert' : 'status'"
                data-testid="menu-fallback-notice"
            >
                <p>{{ state === 'error' ? t('menu.errorTitle') : t('menu.fallbackNotice') }}</p>
                <Button type="button" :label="t('common.retry')" size="small" variant="outlined" severity="secondary" class="mt-2" :loading="loading" @click="reload" />
            </div>

            <template v-for="(section, index) in sections" :key="section.key">
                <!-- 제목 없는 구역 — 연속된 최상위 항목 -->
                <div v-if="section.group === null" :class="index > 0 ? 'mt-2' : ''">
                    <ul v-for="block in section.blocks" :key="block.key" class="m-0 list-none p-0">
                        <li v-for="row in block.items" :key="row.key">
                            <AppMenuLink
                                :node="row.node"
                                :class="UtilMenu.listItemClass(row.active)"
                                :aria-current="row.active ? 'page' : undefined"
                                @click="$emit('navigate')"
                            >
                                <span class="flex min-w-0 items-center">
                                    <i :class="UtilMenu.dotClass(row.color)" :style="UtilBoard.dotStyle(row.color)" aria-hidden="true" :data-testid="row.color ? 'menu-dot-color' : 'menu-dot-slot'" />
                                    <span class="truncate">{{ localizedName(row.node.label, row.node.nameEn) }}</span>
                                </span>
                                <small v-if="row.notice" class="shrink-0 text-xs text-muted-color">{{ t('menu.investmentNotice') }}</small>
                            </AppMenuLink>
                        </li>
                    </ul>
                </div>

                <!-- 제목 있는 구역 — 최상위 그룹. 접을 수 있으면 제목이 단추다 -->
                <section v-else :aria-labelledby="`${uid}-${section.key}`">
                    <h2 :id="`${uid}-${section.key}`" class="px-4 pb-1 pt-4 text-xs font-normal text-muted-color">
                        <button
                            v-if="section.collapsible"
                            type="button"
                            class="flex w-full items-center justify-between gap-2 rounded text-left focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
                            :aria-expanded="!section.collapsed"
                            :aria-controls="`${uid}-${section.key}-panel`"
                            :aria-label="UtilMenuMessages.groupButtonLabel(t, localizedName(section.title ?? '', section.titleEn), section.count)"
                            @click="toggle(section.group!)"
                        >
                            <span>{{ localizedName(section.title ?? '', section.titleEn) }}</span>
                            <span aria-hidden="true">{{ section.collapsed ? UtilMenuMessages.collapsedMark(section.count) : UtilMenuMessages.EXPANDED_MARK }}</span>
                        </button>
                        <template v-else>{{ localizedName(section.title ?? '', section.titleEn) }}</template>
                    </h2>
                    <div :id="`${uid}-${section.key}-panel`">
                        <template v-for="block in section.blocks" :key="block.key">
                            <!-- 하위 그룹의 소제목 — 들여쓴다. 이 그룹도 접을 수 있게 정했다면 단추다 -->
                            <h3 v-if="block.group" :id="`${uid}-${block.key}`" class="px-4 pb-0.5 pt-2 text-xs font-normal text-muted-color" :style="indent(block.level)">
                                <button
                                    v-if="block.collapsible"
                                    type="button"
                                    class="flex w-full items-center justify-between gap-2 rounded text-left focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
                                    :aria-expanded="!block.collapsed"
                                    :aria-controls="`${uid}-${block.key}-panel`"
                                    :aria-label="UtilMenuMessages.groupButtonLabel(t, localizedName(block.group.label, block.group.nameEn), block.count)"
                                    @click="toggle(block.group!)"
                                >
                                    <span>{{ localizedName(block.group.label, block.group.nameEn) }}</span>
                                    <span aria-hidden="true">{{ block.collapsed ? UtilMenuMessages.collapsedMark(block.count) : UtilMenuMessages.EXPANDED_MARK }}</span>
                                </button>
                                <template v-else>{{ localizedName(block.group.label, block.group.nameEn) }}</template>
                            </h3>
                            <ul :id="`${uid}-${block.key}-panel`" class="m-0 list-none p-0">
                                <li v-for="row in block.items" :key="row.key">
                                    <AppMenuLink
                                        :node="row.node"
                                        :class="UtilMenu.listItemClass(row.active)"
                                        :style="indent(row.level)"
                                        :aria-current="row.active ? 'page' : undefined"
                                        @click="$emit('navigate')"
                                    >
                                        <span class="flex min-w-0 items-center">
                                            <i :class="UtilMenu.dotClass(row.color)" :style="UtilBoard.dotStyle(row.color)" aria-hidden="true" :data-testid="row.color ? 'menu-dot-color' : 'menu-dot-slot'" />
                                            <span class="truncate">{{ localizedName(row.node.label, row.node.nameEn) }}</span>
                                        </span>
                                        <small v-if="row.notice" class="shrink-0 text-xs text-muted-color">{{ t('menu.investmentNotice') }}</small>
                                    </AppMenuLink>
                                </li>
                            </ul>
                        </template>
                    </div>
                </section>
            </template>
        </template>
    </nav>
</template>

<script setup lang="ts">
import UtilBoard from '~/utils/util-board';
import UtilMenu from '~/utils/util-menu';
import UtilMenuMessages from '~/utils/util-menu-messages';

const emit = defineEmits<{ navigate: [] }>();
const { t } = useI18n();

const { sections, state, loading, reload, toggle } = useMenuItems();
const uid = useId();
// 메뉴 이름은 운영자가 만든 동적 이름이다 — `label`(한국어)과 선택 영문명 `nameEn` 을 지금 언어로 고른다
const { localizedName } = useLocalizedName();

/** 하위 그룹 안(소제목·항목)을 들여쓴다 — 단계마다 0.75rem. 최상위 그룹의 직속 항목(0단계)은 지금과 같은 `px-4` 그대로다 */
function indent(level: number): Record<string, string> | undefined {
    return level > 0 ? { paddingLeft: `${1 + level * 0.75}rem` } : undefined;
}

/** 메뉴 자리가 비었을 때 — 로고를 누르는 것과 같이 홈으로 가고 햄버거는 닫는다 */
async function goHome() {
    emit('navigate');
    await navigateTo('/');
}
</script>
