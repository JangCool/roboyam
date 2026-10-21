import type { Translate } from '#layers/i18n/utils/util-i18n';

/**
 * 댓글 지연 노출(비회원)의 화면 문구 — 가려진 본문 자리·접근성 이름·로그인 안내 시트. **문구 본문은 `i18n/locales/{ko,en}/prompt.ts`** 에 있다.
 * 설계서 `docs/specs/COM-001.md` 3-1 ③⑤⑥ · 3-3 · 3-4. 계산은 `util-comment-delay.ts`.
 *
 * 회원 문구에는 운영 주체가 나오지 않는다(규칙 22). 비회원에게는 시간 숫자(N분)를 알리지 않는다(②) — 시트 설명에도 숫자가 없다.
 * 상수 문구는 이 클래스에 두지 않는다: 가려진 자리의 한 줄은 `t('prompt.delayPlaceholder')`, 안내 시트(종류 `view`)는 `prompt.viewTitle`·`prompt.viewDescription`.
 */
export default class UtilCommentDelayMessages {
    /**
     * 가려진 본문 자리 단추의 접근성 이름 — 아이콘은 `aria-hidden` 이라 이 이름이 읽힌다.
     * @param {Translate} t - 번역 함수(`useI18n().t`).
     * @param {string | null | undefined} nickname - 댓글쓴이 닉네임.
     * @returns {string} "{닉네임}님의 댓글 — 로그인하면 바로 볼 수 있어요". 닉네임이 없으면 안내만.
     */
    static placeholderLabel(t: Translate, nickname: string | null | undefined): string {
        const who = (nickname ?? '').trim();
        return who ? t('prompt.delayPlaceholderLabel', { name: who }) : t('prompt.delayPlaceholder');
    }
}
