<template>
    <!--
        가젯 페이지 고르기 줄 — 테마 화면 「레이아웃 › 페이지」(사용자 결정 2026-10-29: 홈처럼 가젯 격자로 꾸미는 페이지를 여러 개 — 첫 페이지 「커뮤니티」, 다음 「주제」).
        목록(`useGadgetPageApi().list`)에서 하나를 고르면 아래 실제 화면이 그 페이지(`/p/{key}`)가 되고 편집기가 그 페이지의 고른 테마 배치를 편집한다.
        [새 페이지] · [페이지 정보](이름·설명·영문 주소·기준 폴더·글쓰기 단추 고치기 · 지우기)는 `GadgetPageDialog`. [공개 화면 열기]는 새 탭.
        페이지가 없으면 빈 안내 + [새 페이지]. 고른 페이지는 주소 `?page=<id>` 로 남긴다(부모가). 다른 페이지로 가기 전에 부모의 `beforeSwitch` 로 묻는다(저장하지 않은 배치).
        권한: 조회 `site.read` · 만들기·고치기·지우기 `site.manage`(없으면 단추가 없다). 막는 것은 서버다.
    -->
    <div class="flex flex-col gap-2" data-testid="gadget-page-picker">
        <div v-if="status === 'loading' && !pages.length" class="flex gap-2" role="status" :aria-label="t('common.loading')">
            <Skeleton height="2rem" width="14rem" />
        </div>

        <Message v-else-if="status === 'error'" severity="error" size="small" :closable="false">
            <span class="flex flex-wrap items-center gap-2">
                {{ t('gadgetPage.loadFailed') }} — {{ loadError }}
                <Button type="button" :label="t('common.retry')" size="small" severity="secondary" variant="text" @click="load" />
            </span>
        </Message>

        <div v-else-if="!pages.length" class="flex flex-wrap items-center gap-3 rounded-lg border border-dashed border-(--line) px-4 py-3" data-testid="gadget-page-empty">
            <span class="min-w-0 flex-1 basis-64 text-sm leading-relaxed text-muted-color">{{ t('gadgetPage.empty') }}</span>
            <Button v-if="canManage" type="button" :label="t('gadgetPage.new')" icon="pi pi-plus" size="small" @click="openCreate" />
        </div>

        <div v-else class="flex flex-wrap items-center gap-2 rounded-lg border border-(--line) px-3 py-2">
            <label :for="`${uid}-page`" class="text-sm font-medium">{{ t('gadgetPage.pick') }}</label>
            <Select :model-value="modelValue" :input-id="`${uid}-page`" :options="pageOptions" option-label="label" option-value="value" size="small" class="w-64" @update:model-value="(v: string) => select(v)">
                <template #option="{ option }">
                    <span class="flex min-w-0 flex-col">
                        <span class="truncate text-sm">{{ option.label }}</span>
                        <code class="text-[11px] text-muted-color">{{ option.path }}</code>
                    </span>
                </template>
            </Select>
            <a v-if="current && userWebOrigin" :href="`${userWebOrigin}${UtilGadgetPage.path(current.key)}`" target="_blank" rel="noopener" class="text-xs text-primary underline-offset-2 hover:underline">
                {{ UtilGadgetPage.path(current.key) }} <i class="pi pi-external-link text-[10px]" aria-hidden="true" /><span class="sr-only">{{ t('gadgetPage.openNewTab') }}</span>
            </a>
            <span class="flex-1" />
            <span v-if="current?.folderName" class="text-xs text-muted-color">{{ t('gadgetPage.folderOf', { folder: current.folderName }) }}</span>
            <template v-if="canManage">
                <Button v-if="current" type="button" :label="t('gadgetPage.info')" icon="pi pi-pencil" size="small" severity="secondary" variant="outlined" @click="openEdit" />
                <Button type="button" :label="t('gadgetPage.new')" icon="pi pi-plus" size="small" severity="secondary" variant="outlined" @click="openCreate" />
            </template>
        </div>

        <GadgetPageDialog v-model:visible="dialogOpen" :page="editing" :saving="saving" :error="saveError" @submit="submit" @delete="removePage" />
    </div>
</template>

<script setup lang="ts">
import type { ApiError } from '~/composables/common/useApiClient';
import type { GadgetPage, GadgetPageCreateRequest } from '~/composables/page/api/useGadgetPageApi.types';
import UtilGadgetPage from '~/utils/util-gadget-page';

interface Props {
    /** 고른 페이지 ID */
    modelValue: string | null;
    canManage: boolean;
    /** 다른 페이지로 가기(·지우기) 전에 묻는다 — false 면 그대로 */
    beforeSwitch?: () => Promise<boolean>;
}
const props = withDefaults(defineProps<Props>(), { beforeSwitch: () => Promise.resolve(true) });
const emit = defineEmits<{ (e: 'update:modelValue', value: string | null): void; (e: 'update:page', value: GadgetPage | null): void }>();

const { t } = useI18n();
const uid = useId();
const api = useGadgetPageApi();
const toast = useAppToast();
const toApiError = useApiErrorOf();
const { confirm } = useConfirmDialog();
const { localizedName } = useLocalizedName();
const userWebOrigin = String(useRuntimeConfig().public.userWebOrigin ?? '').replace(/\/+$/, '');

const pages = ref<GadgetPage[]>([]);
const status = ref<'loading' | 'ready' | 'error'>('loading');
const loadError = ref('');

const current = computed(() => pages.value.find((p) => p.id === props.modelValue) ?? null);
const pageOptions = computed(() => pages.value.map((p) => ({ value: p.id, label: localizedName(p.name, p.nameEn), path: UtilGadgetPage.path(p.key) })));

watch(current, (value) => emit('update:page', value), { immediate: true });

async function load(): Promise<void> {
    status.value = 'loading';
    loadError.value = '';
    try {
        pages.value = (await api.list()).pages;
        status.value = 'ready';
        // 고른 페이지가 없거나 지워졌으면 첫 페이지
        if (!current.value) emit('update:modelValue', pages.value[0]?.id ?? null);
    } catch (e) {
        loadError.value = toApiError(e).message;
        status.value = 'error';
    }
}

async function select(id: string): Promise<void> {
    if (id === props.modelValue || !(await props.beforeSwitch())) return;
    emit('update:modelValue', id);
}

/* ── 만들기·고치기·지우기 ── */

const dialogOpen = ref(false);
const editing = ref<GadgetPage | null>(null);
const saving = ref(false);
const saveError = ref<ApiError | null>(null);

function openCreate(): void {
    editing.value = null;
    saveError.value = null;
    dialogOpen.value = true;
}

function openEdit(): void {
    editing.value = current.value;
    saveError.value = null;
    dialogOpen.value = true;
}

async function submit(body: GadgetPageCreateRequest): Promise<void> {
    if (saving.value) return;
    const target = editing.value;
    // 새 페이지로 넘어가기 전에 저장하지 않은 배치를 묻는다
    if (!target && !(await props.beforeSwitch())) return;
    saving.value = true;
    saveError.value = null;
    try {
        const saved = target ? await api.update(target.id, body) : await api.create(body);
        pages.value = target ? pages.value.map((p) => (p.id === saved.id ? saved : p)) : [...pages.value, saved];
        dialogOpen.value = false;
        if (!target) emit('update:modelValue', saved.id);
        toast.success(target ? t('gadgetPage.saved', { name: saved.name }) : t('gadgetPage.created', { name: saved.name }));
    } catch (e) {
        saveError.value = toApiError(e);
    } finally {
        saving.value = false;
    }
}

async function removePage(page: GadgetPage): Promise<void> {
    const ok = await confirm({
        title: t('gadgetPage.deleteTitle', { name: page.name }),
        message: page.menuLinks ? t('gadgetPage.deleteMessageLinked', { count: page.menuLinks }) : t('gadgetPage.deleteMessage'),
        confirmLabel: t('gadgetPage.delete'),
        cancelLabel: t('common.cancel'),
        tone: 'danger',
    });
    if (!ok) return;
    saving.value = true;
    try {
        await api.remove(page.id);
        pages.value = pages.value.filter((p) => p.id !== page.id);
        dialogOpen.value = false;
        emit('update:modelValue', pages.value[0]?.id ?? null);
        toast.success(t('gadgetPage.deleted', { name: page.name }));
    } catch (e) {
        saveError.value = toApiError(e);
    } finally {
        saving.value = false;
    }
}

onMounted(() => void load());
</script>
