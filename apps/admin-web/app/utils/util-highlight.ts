import type { AdminPostRef } from '~/composables/content/api/usePopularApi.types';
import type { HighlightCommentCounts, HighlightItem, HighlightSaveRequest, HighlightStatus } from '~/composables/content/api/useHighlightApi.types';
import UtilSiteSetting from '~/utils/util-site-setting';

/**
 * 하이라이트 화면의 순수 계산 — 서버 규칙을 **미리 알려 주는 용도**이고 판정은 서버다(`forms.md`).
 * 서버 규칙 이름: `HighlightAdminService`(제목 40자 · 요약 100자 — 앞뒤 공백을 떼고 비면 null, 끝은 시작보다 늦어야 한다), 글 찾기 `post-search`(숫자 15~19자리 = 글 번호, 아니면 제목 2자 이상),
 * 후보 `candidates`(days 0~365 · minLikes 1~1000), 대표 이미지는 로고와 같은 검사(`FileUploadService` — PNG·JPG·GIF·WEBP 2MB, `UtilSiteSetting.logoFile` 재사용).
 */

/** 만들기·고치기 입력 — 기간은 순간 ISO 글자, 비면 '' (상시) */
export interface HighlightForm {
    post: AdminPostRef | null;
    title: string;
    summary: string;
    imageFileId: string | null;
    /** 미리보기 주소 — 저장된 이미지면 서버 주소, 방금 올린 것이면 브라우저 임시 주소 */
    imageUrl: string | null;
    visible: boolean;
    showFrom: string;
    showUntil: string;
    debate: boolean;
}

export type HighlightFormField = 'postId' | 'title' | 'summary' | 'showUntil' | 'imageFileId';
export type HighlightFormProblem = 'POST_REQUIRED' | 'TITLE_TOO_LONG' | 'SUMMARY_TOO_LONG' | 'PERIOD_ORDER';

export type HighlightImageProblem = 'EMPTY' | 'TYPE' | 'SIZE';

/** 후보 찾기 조건 칸 문제 */
export type HighlightCandidateProblem = 'NOT_INTEGER' | 'OUT_OF_RANGE';

export default class UtilHighlight {
    static readonly TITLE_MAX = 40;
    static readonly SUMMARY_MAX = 100;
    /** 글 찾기 최소 글자 수(제목) */
    static readonly SEARCH_MIN = 2;
    /** 후보 — 서버 기본값과 범위 */
    static readonly DAYS_DEFAULT = 7;
    static readonly DAYS_MAX = 365;
    static readonly MIN_LIKES_DEFAULT = 3;
    static readonly MIN_LIKES_MIN = 1;
    static readonly MIN_LIKES_MAX = 1000;
    /** 후보 기간 고르개(0 = 전체) */
    static readonly DAY_OPTIONS: readonly number[] = [1, 3, 7, 30, 90, 365, 0];
    /** 의견 한 쪽 크기 */
    static readonly OPINION_PAGE_SIZE = 20;
    static readonly IMAGE_ACCEPT = UtilSiteSetting.LOGO_ACCEPT;
    static readonly IMAGE_MAX_BYTES = UtilSiteSetting.LOGO_MAX_BYTES;

    /** 상태 딱지 색 */
    static statusSeverity(status: HighlightStatus): 'success' | 'info' | 'secondary' | 'warn' | 'danger' {
        if (status === 'SHOWING') return 'success';
        if (status === 'SCHEDULED') return 'info';
        if (status === 'BLOCKED') return 'danger';
        if (status === 'OFF') return 'warn';
        return 'secondary';
    }

    static statusIcon(status: HighlightStatus): string {
        if (status === 'SHOWING') return 'pi pi-eye';
        if (status === 'SCHEDULED') return 'pi pi-clock';
        if (status === 'BLOCKED') return 'pi pi-ban';
        if (status === 'OFF') return 'pi pi-eye-slash';
        return 'pi pi-calendar-times';
    }

    /** 보이는 제목 — 하이라이트 제목이 없으면 글 제목 */
    static displayTitle(item: Pick<HighlightItem, 'title' | 'post'>): string {
        return item.title ?? item.post?.title ?? '';
    }

    /** 의견(찬성·반대)이 있거나 의견 받기가 켜져 있으면 의견 관리를 연다 */
    static hasDebate(item: Pick<HighlightItem, 'debate' | 'commentCounts'>): boolean {
        return item.debate || UtilHighlight.opinionCount(item.commentCounts) > 0;
    }

    static opinionCount(counts: HighlightCommentCounts): number {
        return counts.pro + counts.con;
    }

    /** 글자 수 — 앞뒤 공백을 떼고 유니코드 한 글자씩(서버와 같다) */
    static length(text: string): number {
        return [...text.trim()].length;
    }

    static emptyForm(post: AdminPostRef | null = null): HighlightForm {
        return { post, title: '', summary: '', imageFileId: null, imageUrl: null, visible: true, showFrom: '', showUntil: '', debate: false };
    }

    static formOf(item: HighlightItem): HighlightForm {
        return {
            post: item.post,
            title: item.title ?? '',
            summary: item.summary ?? '',
            imageFileId: item.imageFileId,
            imageUrl: item.imageUrl,
            visible: item.visible,
            showFrom: item.showFrom ?? '',
            showUntil: item.showUntil ?? '',
            debate: item.debate,
        };
    }

    /** 입력의 문제 — 칸마다 하나 */
    static problems(form: HighlightForm, creating: boolean): Partial<Record<HighlightFormField, HighlightFormProblem>> {
        const out: Partial<Record<HighlightFormField, HighlightFormProblem>> = {};
        if (creating && !form.post) out.postId = 'POST_REQUIRED';
        if (UtilHighlight.length(form.title) > UtilHighlight.TITLE_MAX) out.title = 'TITLE_TOO_LONG';
        if (UtilHighlight.length(form.summary) > UtilHighlight.SUMMARY_MAX) out.summary = 'SUMMARY_TOO_LONG';
        if (form.showFrom && form.showUntil && Date.parse(form.showUntil) <= Date.parse(form.showFrom)) out.showUntil = 'PERIOD_ORDER';
        return out;
    }

    /** 요청 — 고칠 때도 모든 칸을 보낸다(`imageFileId` null = 대표 이미지 지우기) */
    static toRequest(form: HighlightForm): HighlightSaveRequest {
        const title = form.title.trim();
        const summary = form.summary.trim();
        return {
            postId: form.post?.id ?? null,
            title: title || null,
            summary: summary || null,
            imageFileId: form.imageFileId,
            visible: form.visible,
            showFrom: form.showFrom || null,
            showUntil: form.showUntil || null,
            debate: form.debate,
        };
    }

    /** 대표 이미지 파일의 문제 — 보내기 전 편의 검사(로고와 같은 규칙), 판정은 서버 */
    static imageProblem(file: { size: number; type: string }): HighlightImageProblem | null {
        return UtilSiteSetting.logoFile(file);
    }

    /** 글 찾기 검색어를 보낼 수 있나 — 숫자(글 번호)는 길이와 무관하게 보내고 서버가 판정한다 */
    static searchable(q: string): boolean {
        const text = q.trim();
        return /^\d+$/.test(text) || [...text].length >= UtilHighlight.SEARCH_MIN;
    }

    /** 순서를 한 칸 옮긴 새 ID 목록 — 옮길 수 없으면 null */
    static moved(ids: readonly string[], id: string, delta: -1 | 1): string[] | null {
        const from = ids.indexOf(id);
        const to = from + delta;
        if (from < 0 || to < 0 || to >= ids.length) return null;
        const next = [...ids];
        const [picked] = next.splice(from, 1);
        if (picked === undefined) return null;
        next.splice(to, 0, picked);
        return next;
    }

    /** 후보 최소 추천 칸 */
    static minLikesProblem(text: string): HighlightCandidateProblem | null {
        const cleaned = text.trim();
        if (!/^\d{1,6}$/.test(cleaned)) return 'NOT_INTEGER';
        const value = Number(cleaned);
        return value < UtilHighlight.MIN_LIKES_MIN || value > UtilHighlight.MIN_LIKES_MAX ? 'OUT_OF_RANGE' : null;
    }
}
