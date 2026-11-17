import type { PurchaseSource } from '~/composables/board/api/usePostApi.types';
import type { MyTrial, TrialListStatus } from '~/composables/trial/api/useTrialApi.types';

/** 내 체험단 한 줄의 지금 단계 */
export type MyTrialStage = 'APPLIED' | 'NOT_SELECTED' | 'CANCELLED' | 'WAIT_SHIP' | 'SHIPPED' | 'WRITE' | 'RETURN' | 'UNRETURNED' | 'DONE';

/**
 * 체험단·구입 경로 화면의 순수 규칙(trial-group-contract) — 주소 · 탭 · 내 체험단 단계 · 구입 경로 칩. 문구가 낀 것은 `UtilTrialMessages`.
 * 신청 동의 항목·리뷰어 먼저 신청 시각·할 수 있는 일(`canReceive`·`canWrite`·`canObject`)은 서버가 준다 — 판정은 서버다.
 */
export default class UtilTrial {
    /** 체험단 페이지(메뉴 바로 가기 내장 화면 `TRIALS`) */
    static readonly LIST_PATH = '/trials';
    /** 내 정보 › 내 체험단 */
    static readonly MY_PATH = '/me/trials';
    /** 사업자 센터 › 체험단 */
    static readonly BIZ_PATH = '/business/trials';

    /** 탭 차례 */
    static readonly TABS: readonly TrialListStatus[] = ['RECRUITING', 'RUNNING', 'CLOSED'];

    /** 이의 제기·설명 글자 한도(서버 `trial_damage_reports.objection`·`description` varchar(1000)) */
    static readonly TEXT_MAX = 1000;
    /** 구매 인증 거절 사유 한도(서버 `purchase_proofs.reject_reason` varchar(300)) */
    static readonly REASON_MAX = 300;
    /** 사진 한 장 최대(10MB — 서버 미디어 저장소 규칙과 같다고 가정) */
    static readonly PHOTO_MAX_BYTES = 10 * 1024 * 1024;
    static readonly PHOTO_ACCEPT = 'image/jpeg,image/png,image/webp';

    static detailPath(id: string): string {
        return `/trials/${encodeURIComponent(id)}`;
    }

    static bizDetailPath(id: string): string {
        return `${UtilTrial.BIZ_PATH}/${encodeURIComponent(id)}`;
    }

    /** 게시판 스텝 「구매 인증」 탭 */
    static staffPath(slug: string): string {
        return `/boards/${encodeURIComponent(slug)}/purchase-proofs`;
    }

    /** 「쓰러 가기」 — 사용기 게시판 글쓰기가 체험단 연결·말머리를 넣은 채 열린다 */
    static writeLocation(boardSlug: string, participantId: string): { path: string; query: Record<string, string> } {
        return { path: '/write', query: { board: boardSlug, trialParticipantId: participantId } };
    }

    /** 주소의 탭(`?tab=running|closed`) — 모르는 값이면 모집 중 */
    static tabOf(value: unknown): TrialListStatus {
        const v = typeof value === 'string' ? value.toUpperCase() : '';
        return v === 'RUNNING' || v === 'CLOSED' ? v : 'RECRUITING';
    }

    /** 리뷰어 몫·일반 몫 인원(반올림 — 서버 `reviewerSlots` 와 같다고 가정) */
    static split(recruitCount: number, reviewerShare: number): { reviewer: number; general: number } {
        const reviewer = Math.round((recruitCount * reviewerShare) / 100);
        return { reviewer, general: Math.max(0, recruitCount - reviewer) };
    }

    /** 내 체험단 한 줄의 지금 단계 */
    static stageOf(row: MyTrial): MyTrialStage {
        if (row.applicationStatus === 'APPLIED') return 'APPLIED';
        if (row.applicationStatus === 'NOT_SELECTED') return 'NOT_SELECTED';
        if (row.applicationStatus === 'CANCELLED') return 'CANCELLED';
        const p = row.participation;
        if (!p) return 'WAIT_SHIP';
        if (p.unreturnedAt) return 'UNRETURNED';
        if (!p.shippedAt) return 'WAIT_SHIP';
        if (!p.receivedAt) return 'SHIPPED';
        if (!p.reviewPostId) return 'WRITE';
        if (row.trial.form === 'LEND' && !p.returnedAt) return 'RETURN';
        return 'DONE';
    }

    /** 기한이 지났나 */
    static overdue(iso: string | null, now = Date.now()): boolean {
        return !!iso && Date.parse(iso) < now;
    }

    /** 남은 날(올림, 지났으면 0) */
    static daysLeft(iso: string, now = Date.now()): number {
        return Math.max(0, Math.ceil((Date.parse(iso) - now) / 86_400_000));
    }

    /** 목록 칩 종류 — 체험단 · 구매 인증 · 직접 구매 · 협찬(계약 「구입 경로」 — 대여는 칩 없음) */
    static chipOf(source: PurchaseSource | null | undefined, proofApproved: boolean | undefined): 'TRIAL' | 'PROOF' | 'SELF' | 'SPONSORED' | null {
        if (source === 'TRIAL') return 'TRIAL';
        if (source === 'SELF') return proofApproved ? 'PROOF' : 'SELF';
        if (source === 'SPONSORED') return 'SPONSORED';
        return null;
    }

    /** 이미지 파일 검사 — 종류·크기. 문제 없으면 null */
    static photoProblem(file: File): 'type' | 'size' | null {
        if (!UtilTrial.PHOTO_ACCEPT.split(',').includes(file.type)) return 'type';
        if (file.size > UtilTrial.PHOTO_MAX_BYTES) return 'size';
        return null;
    }
}
