import UtilI18n from '#layers/i18n/utils/util-i18n';

/**
 * 동적 이름(운영자가 관리 화면에서 만들고 바꾸는 게시판·메뉴·말머리 이름)을 지금 언어로 고르는 함수 — 이름을 `{{ node.name }}` 처럼 직접 그리지 않고 이것을 거친다.
 * 규칙은 `UtilI18n.localizedName` 한 곳: 언어가 `en` 이고 `nameEn` 이 비어 있지 않으면 `nameEn`, 아니면 `name`(한국어).
 *
 * ```vue
 * <script setup lang="ts">
 * const { localizedName } = useLocalizedName();
 * </script>
 * <template>{{ localizedName(board.name, board.nameEn) }}</template>
 * ```
 *
 * 언어(`useI18n().locale`)를 **그릴 때 읽으므로** 언어가 바뀌면 화면의 이름도 바로 바뀐다. 컴포저블은 setup 에서 부른다.
 */
export function useLocalizedName() {
    const { locale } = useI18n();

    /** 지금 언어의 이름 */
    function localizedName(name: string, nameEn?: string | null): string {
        return UtilI18n.localizedName(name, nameEn, locale.value);
    }

    return { localizedName };
}
