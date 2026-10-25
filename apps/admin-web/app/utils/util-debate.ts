import type { DebateItem, DebateOpinionListView, DebateOpinionPage, DebateSaveRequest, DebateStance } from '~/composables/content/api/useDebateApi.types';

/** 토론 설정 대화창의 값 — 「중립도 받기」·「입장 바꾸기 허용」(새로 열 때 둘 다 켬) */
export interface DebateSettingsForm {
    neutralEnabled: boolean;
    stanceChangeAllowed: boolean;
}

/**
 * 찬반 토론(게시글 기능)의 순수 계산 — 글 번호로 쓰는 재사용 부품(설정 대화창 `DebateSettingsDialog`·의견 서랍 `DebateOpinionsDrawer`)과 하이라이트 화면이 같이 쓴다.
 * 서버 규칙을 **미리 알려 주는 용도**이고 판정은 서버다(`forms.md`). 고정 요청 거절 사유 0~100자(앞뒤 공백을 떼고 비면 없음). 계약: `docs/features/post-debate-contract.md`.
 */
export default class UtilDebate {
    /** 입장 차례 — 찬성 · 반대 · 중립 */
    static readonly STANCES: readonly DebateStance[] = ['PRO', 'CON', 'NEUTRAL'];
    /** 의견 한 쪽 크기 */
    static readonly OPINION_PAGE_SIZE = 20;
    /** 고정 요청 거절 사유 최대 글자 수 */
    static readonly REJECT_REASON_MAX = 100;

    /** 입장 → 수 칸 이름(`pro`·`con`·`neutral`) */
    static countKey(stance: DebateStance): 'pro' | 'con' | 'neutral' {
        return stance === 'PRO' ? 'pro' : stance === 'CON' ? 'con' : 'neutral';
    }

    /** 입장 글자색 — 찬성 파랑 · 반대 빨강 · 중립 초록(`main.css` 「토론 입장 색」, 공개 사이트와 같은 토큰) */
    static stanceTextClass(stance: DebateStance): string {
        return stance === 'PRO' ? 'stance-pro-text' : stance === 'CON' ? 'stance-con-text' : 'stance-neutral-text';
    }

    /** 입장 배지(옅은 칩) 색 */
    static stanceSoftClass(stance: DebateStance): string {
        return stance === 'PRO' ? 'stance-pro-soft' : stance === 'CON' ? 'stance-con-soft' : 'stance-neutral-soft';
    }

    /** 토론 줄에 중립 수를 보이는가 — 중립을 받거나, 끄기 전에 쌓인 중립 표가 있을 때 */
    static showNeutral(item: Pick<DebateItem, 'neutralEnabled' | 'votes'>): boolean {
        return item.neutralEnabled || item.votes.neutral > 0;
    }

    /** 보일 입장 — 중립은 받거나 쌓인 표가 있을 때만 */
    static stancesOf(item: Pick<DebateItem, 'neutralEnabled' | 'votes'>): DebateStance[] {
        return UtilDebate.STANCES.filter((stance) => stance !== 'NEUTRAL' || UtilDebate.showNeutral(item));
    }

    /** 의견 관리 서랍에 중립 탭을 보이는가 — 서버 안내대로 `neutralEnabled || neutral.total > 0` */
    static showNeutralTab(view: Pick<DebateOpinionListView, 'neutralEnabled' | 'neutral'>): boolean {
        return view.neutralEnabled || view.neutral.total > 0;
    }

    /** 의견 목록 응답에서 그 입장의 쪽 */
    static pageOf(view: DebateOpinionListView, stance: DebateStance): DebateOpinionPage {
        return stance === 'PRO' ? view.pro : stance === 'CON' ? view.con : view.neutral;
    }

    /** 고정 요청 거절 사유가 너무 긴가(앞뒤 공백을 뗀 유니코드 한 글자씩) */
    static rejectReasonTooLong(reason: string): boolean {
        return UtilDebate.length(reason) > UtilDebate.REJECT_REASON_MAX;
    }

    /** 거절 사유 → 요청 값(비면 null) */
    static rejectReasonOf(reason: string): string | null {
        return reason.trim() || null;
    }

    /** 글자 수 — 앞뒤 공백을 떼고 유니코드 한 글자씩(서버와 같다) */
    static length(text: string): number {
        return [...text.trim()].length;
    }

    /** 새로 열 때의 설정 — 둘 다 켬 */
    static emptySettings(): DebateSettingsForm {
        return { neutralEnabled: true, stanceChangeAllowed: true };
    }

    static settingsOf(item: Pick<DebateItem, 'neutralEnabled' | 'stanceChangeAllowed'>): DebateSettingsForm {
        return { neutralEnabled: item.neutralEnabled, stanceChangeAllowed: item.stanceChangeAllowed };
    }

    /** 열기·설정 요청 */
    static openRequest(form: DebateSettingsForm): DebateSaveRequest {
        return { enabled: true, neutralEnabled: form.neutralEnabled, stanceChangeAllowed: form.stanceChangeAllowed };
    }

    /** 닫기 요청 */
    static closeRequest(): DebateSaveRequest {
        return { enabled: false, neutralEnabled: null, stanceChangeAllowed: null };
    }
}
