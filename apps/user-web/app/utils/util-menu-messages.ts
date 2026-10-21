import type { Translate } from '#layers/i18n/utils/util-i18n';

/**
 * 메뉴(왼쪽·오른쪽·상단 메뉴, 햄버거, 모바일 상단 탭, 게시판 모음)에 보이는 문구 — 시안 `design/screens/25-menu.html` 7절.
 * **문구 본문은 `i18n/locales/{ko,en}/menu.ts`** 에 있다. 상수 문구(상태·단추·접근성 이름)는 컴포넌트가 `t('menu.xxx')` 를 바로 부르고, 여기는 계산이 끼는 것만 남는다.
 * 번역 함수 `t` 를 **첫 인자로 받는다**(`.claude/guides/frontend/i18n.md` — 메시지 유틸 변환 규약).
 *
 * 메뉴 **항목·그룹의 글자는 여기 없다** — 서버가 메뉴 구성과 서비스 이름을 반영해 `GET /api/menu` 로 준다(화면이 만들지 않는다).
 */

export default class UtilMenuMessages {
    /** 펼친 그룹 제목 옆의 표시 */
    static readonly EXPANDED_MARK = '▾';

    /**
     * 접힌 그룹 제목 옆의 표시 — 항목 수가 함께 보인다(언어와 무관한 기호라 번역하지 않는다).
     * @param {number} count - 그 그룹 아래 항목 수.
     * @returns {string} 예: "▸ 2".
     */
    static collapsedMark(count: number): string {
        return `▸ ${count}`;
    }

    /**
     * 접을 수 있는 그룹 제목 단추의 이름 — 접힘 상태는 단추의 `aria-expanded` 가 알리므로 글자는 제목 그대로 두고, 이 글자는 항목 수만 보탠다.
     * @param {Translate} t - 번역 함수(`useI18n().t`).
     * @param {string} title - 그룹 제목.
     * @param {number} count - 그 그룹 아래 항목 수.
     * @returns {string} 예: "어울림 광장, 항목 2개".
     */
    static groupButtonLabel(t: Translate, title: string, count: number): string {
        return t('menu.groupButtonLabel', { title, count });
    }
}
