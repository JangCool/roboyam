import type { GroupManageBlockedReason, GroupVisibility, GroupVisibilityInfo, GroupVisibilityPreview } from '~/composables/group/api/useGroupApi.types';
import { ApiError } from '~/composables/common/useApiClient';
import UtilGroup from '~/utils/util-group';

/**
 * 소모임 공개 범위(공개 소모임·비공개 소모임, 5단계)의 순수 로직 — 값 읽기, 변경 시트의 쿨다운, 한마디·사유 글자 수, 공개 요청·처리 오류 가르기.
 * 계약은 community `docs/api-groups.md` 11절(`useGroupApi.ts` 머리말). **판정은 늘 서버다** — 여기 있는 것은 보내기 전에 미리 아는 용도다(community `UtilGroupVisibility` 를 옮겼다).
 * 문구는 `util-group-visibility-messages.ts` 와 `i18n/locales/{ko,en}/groupVisibility.ts`, 글 상세 패널의 상태는 `util-post-group-panel.ts`.
 */

/** 공개 요청·처리·돌리기·범위 변경이 실패한 까닭의 갈래 — 분기는 문구가 아니라 코드로 한다 */
export type PublishFailureKind =
    | 'exists' // 이미 요청 중 — 화면을 요청 중 상태로
    | 'decided' // 이미 처리된 요청
    | 'request-gone' // 요청이 없다(작성자가 취소했다)
    | 'retry-later' // 거절 뒤 아직 다시 요청할 수 없다
    | 'limit' // 같은 글 거절 한도
    | 'not-needed' // 모임장·매니저는 요청 없이 직접 공개한다
    | 'required' // 작성자 직접 공개 길이 닫혔다(요청을 보내야 한다)
    | 'already-public' // 이미 모두에게 보이는 글
    | 'members-only' // 모임장·매니저가 멤버 전용으로 돌려 둔 글
    | 'by-group' // 공개 소모임의 글은 작성자가 돌릴 수 없다
    | 'permission-denied' // 매니저인데 이 일을 맡지 않았다
    | 'leader-only' // 모임장(·매니저)만 하는 일
    | 'forbidden' // 그 일을 할 사람이 아니다
    | 'blocked' // 쓰기 자격(이메일 미인증·이용 제한·사업자 계정)
    | 'rate-limited' // 오늘 요청 한도
    | 'validation' // 입력 오류(확인 체크·글자 수)
    | 'disabled' // 공개 범위 기능이 꺼져 있다
    | 'cooldown' // 공개 범위를 바꾼 지 얼마 되지 않았다
    | 'not-member' // 이 소모임의 멤버가 아니다
    | 'inactive' // 열려 있지 않은 소모임
    | 'private' // 멤버에게만 보이는 글
    | 'gone' // 글·소모임이 없다
    | 'session' // 로그인이 끊겼다(401) — 통로가 로그인 안내를 띄운다
    | 'other';

const CODE_KIND: Readonly<Record<string, PublishFailureKind>> = {
    GROUP_PUBLISH_REQUEST_EXISTS: 'exists',
    GROUP_PUBLISH_REQUEST_DECIDED: 'decided',
    GROUP_PUBLISH_REQUEST_NOT_FOUND: 'request-gone',
    GROUP_PUBLISH_REQUEST_RETRY_LATER: 'retry-later',
    GROUP_PUBLISH_REQUEST_LIMIT: 'limit',
    GROUP_PUBLISH_REQUEST_NOT_NEEDED: 'not-needed',
    GROUP_PUBLISH_REQUEST_REQUIRED: 'required',
    GROUP_POST_ALREADY_PUBLIC: 'already-public',
    GROUP_POST_MEMBERS_ONLY: 'members-only',
    GROUP_POST_PUBLIC_BY_GROUP: 'by-group',
    GROUP_MANAGER_PERMISSION_DENIED: 'permission-denied',
    GROUP_LEADER_ONLY: 'leader-only',
    FORBIDDEN: 'forbidden',
    RATE_LIMITED: 'rate-limited',
    VALIDATION: 'validation',
    GROUP_VISIBILITY_DISABLED: 'disabled',
    GROUP_VISIBILITY_COOLDOWN: 'cooldown',
    GROUP_NOT_MEMBER: 'not-member',
    GROUP_INACTIVE: 'inactive',
    GROUP_POST_PRIVATE: 'private',
    POST_NOT_FOUND: 'gone',
    POST_DELETED: 'gone',
    GROUP_NOT_FOUND: 'gone',
};

export default class UtilGroupVisibility {
    /**
     * 공개 요청 한마디·거절 사유 글자 수의 **위쪽 한계이자 기본값** — DB 칸 길이(varchar(100)). 관리자 설정(`textMax` 10~100)으로 줄인 값은 서버가
     * 글 상세 `group.publishTextMax`·요청 목록 `publishTextMax` 로 준다(`textMaxOf`). 서버가 쓰기 때 다시 판정한다(`400 fields.note|reason`).
     */
    static readonly TEXT_MAX = 100;

    /** 공개 요청 목록 한 쪽 — 서버 `size` 1~50 */
    static readonly PAGE_SIZE = 20;

    /** 지금 적용되는 한마디·사유 글자 수 한도 — 1~100 의 정수면 그 값, 칸이 없거나 이상하면 기본(`TEXT_MAX`) */
    static textMaxOf(raw: unknown): number {
        return typeof raw === 'number' && Number.isInteger(raw) && raw >= 1 && raw <= UtilGroupVisibility.TEXT_MAX ? raw : UtilGroupVisibility.TEXT_MAX;
    }

    /** 응답 칸을 공개 범위로 읽는다 — 모르는 값·`null`·칸 없음은 null(그리지 않는다) */
    static of(raw: unknown): GroupVisibility | null {
        return UtilGroup.visibilityOf(raw);
    }

    /** 반대 값 — 변경 시트가 「바꾸기」 단추 하나로 보내는 값 */
    static opposite(value: GroupVisibility): GroupVisibility {
        return value === 'PUBLIC' ? 'PRIVATE' : 'PUBLIC';
    }

    /** 비공개→공개로 바꿀 수 없는 쿨다운 안인가 — `visibilityInfo.canChangeToPublicAt` 이 지금보다 뒤다(시각 비교만, 표시는 `useDisplayTime`) */
    static inCooldown(info: GroupVisibilityInfo | null | undefined, now: number): boolean {
        const until = info?.canChangeToPublicAt ? Date.parse(info.canChangeToPublicAt) : Number.NaN;
        return !Number.isNaN(until) && until > now;
    }

    /** 미리보기 응답이 말하는 쿨다운 안인가 — `canChangeAt` 이 지금보다 뒤다 */
    static previewInCooldown(preview: Pick<GroupVisibilityPreview, 'canChangeAt'> | null | undefined, now: number): boolean {
        const until = preview?.canChangeAt ? Date.parse(preview.canChangeAt) : Number.NaN;
        return !Number.isNaN(until) && until > now;
    }

    /** 대기 수 — `null`(볼 수 없음)과 0(없음)을 가른다. 이상한 값은 null */
    static pendingCountOf(raw: unknown): number | null {
        return typeof raw === 'number' && Number.isFinite(raw) && raw >= 0 ? Math.floor(raw) : null;
    }

    /* ── 글자 수 ── */

    /** 글자 수 — 앞뒤 공백을 뗀 코드포인트(서버도 코드포인트로 센다) */
    static length(text: string): number {
        return Array.from(text.trim()).length;
    }

    /** 한마디·사유가 한도를 넘었는가 */
    static tooLong(text: string, max: number = UtilGroupVisibility.TEXT_MAX): boolean {
        return UtilGroupVisibility.length(text) > max;
    }

    /* ── 서버 오류 ── */

    /** 실패한 까닭의 갈래 — `ApiError` 가 아니면 `other`(네트워크 등) */
    static failureKind(e: unknown): PublishFailureKind {
        if (!(e instanceof ApiError)) return 'other';
        if (e.status === 401) return 'session';
        if (UtilGroup.blockedReasonOf(e)) return 'blocked';
        return CODE_KIND[e.code] ?? 'other';
    }

    /** 쓰기 자격 오류(이메일 미인증·이용 제한·사업자 계정)의 까닭 — 아니면 null */
    static blockedOf(e: unknown): GroupManageBlockedReason | null {
        return UtilGroup.blockedReasonOf(e);
    }

    /** 열어 둔 채 권한이 바뀐 오류인가(체크를 잃음·모임장이 아님) — 상세를 다시 받아 권한을 맞춘다 */
    static isStalePermission(kind: PublishFailureKind): boolean {
        return kind === 'permission-denied' || kind === 'leader-only' || kind === 'forbidden';
    }

    /** 서버가 준 대기 시간(초) — `429 GROUP_PUBLISH_REQUEST_RETRY_LATER`·`409 GROUP_VISIBILITY_COOLDOWN` 의 `retryAfterSeconds`. 없으면 null */
    static retryAfterOf(e: unknown): number | null {
        if (!(e instanceof ApiError)) return null;
        const seconds = e.retryAfterSeconds;
        return typeof seconds === 'number' && seconds > 0 ? seconds : null;
    }

    /** 칸 오류 문구 — `fields.<칸>` 이 있으면 그것, 아니면 null */
    static fieldOf(e: unknown, field: string): string | null {
        return e instanceof ApiError ? (e.field(field) ?? null) : null;
    }

    /**
     * 남은 시간을 시간 단위로 올림한다 — 「{n}시간 뒤 다시 요청할 수 있어요」. 이미 지났으면 0, 시각을 읽을 수 없으면 null.
     * @param iso 다시 요청할 수 있는 시각(`canRetryAt`)
     * @param now 지금(밀리초)
     */
    static hoursUntil(iso: string | null | undefined, now: number): number | null {
        const at = iso ? Date.parse(iso) : Number.NaN;
        if (Number.isNaN(at)) return null;
        if (at <= now) return 0;
        return Math.max(1, Math.ceil((at - now) / 3_600_000));
    }
}
