import type { Translate } from '#layers/i18n/utils/util-i18n';
import type { MemberNotificationType } from '~/composables/notification/api/useNotificationApi.types';

/**
 * 알림 표시 중 셸(헤더의 종·하단 탭의 알림·알림 패널)이 쓰는 것 — 안 읽은 수 배지, 패널 한 줄의 아이콘·이동 주소 검사.
 * 알림 문장(`title`·`body`)은 서버가 요청 언어로 풀어 주므로 여기서 만들지 않는다(종류별 문구를 화면이 만드는 community 와 다른 점).
 * 쪽지함 배지 이름(`dmBadgeLabel`)도 여기 있다 — 헤더 쪽지 아이콘(`AppMessageLink`)·내 정보 메뉴의 「쪽지함」 줄(`useDmBadge`)이 쓴다.
 */
export default class UtilNotification {
    /** 알림 점·숫자에 그대로 보이는 가장 큰 수 — 넘으면 "99+" */
    static readonly BADGE_MAX = 99;

    /** 안 읽은 알림 수를 다시 묻는 간격(탭이 보이는 동안) */
    static readonly POLL_INTERVAL_MS = 60_000;

    /**
     * 서버가 준 안 읽은 수를 0 이상의 정수로 다듬습니다. 숫자가 아니면 0 입니다.
     * @param {unknown} value - 응답의 `count`·`unreadCount`.
     * @returns {number} 0 이상의 정수.
     */
    static clampCount(value: unknown): number {
        return typeof value === 'number' && Number.isFinite(value) ? Math.max(0, Math.floor(value)) : 0;
    }

    /**
     * 안 읽은 알림 수를 점·숫자에 보일 글자로 바꿉니다.
     * @param {number} count - 안 읽은 알림 수(서버 상한 100).
     * @returns {string} 0 이면 빈 문자열, 99 를 넘으면 "99+", 아니면 숫자.
     */
    static badgeText(count: number): string {
        const n = UtilNotification.clampCount(count);
        if (n === 0) return '';
        return n > UtilNotification.BADGE_MAX ? `${UtilNotification.BADGE_MAX}+` : String(n);
    }

    /**
     * 알림 링크·버튼의 이름(스크린리더용). 점·숫자는 시각 요소(`aria-hidden`)라서 이름에 숫자를 넣습니다.
     * @param {Translate} t - 번역 함수(`useTranslate()` / `useI18n().t`).
     * @param {number} count - 안 읽은 알림 수.
     * @returns {string} "알림" · "알림, 안 읽은 알림 3개" · "알림, 안 읽은 알림 99개 이상".
     */
    static badgeLabel(t: Translate, count: number): string {
        const n = UtilNotification.clampCount(count);
        if (n === 0) return t('notification.title');
        return n > UtilNotification.BADGE_MAX ? t('notification.badgeUnreadOver', { max: UtilNotification.BADGE_MAX }) : t('notification.badgeUnread', { count: n });
    }

    /**
     * 쪽지함 링크의 이름(스크린리더용). 점·숫자는 시각 요소라서 이름에 안 읽은 대화 수를 넣습니다.
     * @param {Translate} t - 번역 함수(`useTranslate()` / `useI18n().t`).
     * @param {number} count - 안 읽은 대화 수.
     * @returns {string} "쪽지함" · "쪽지함, 안 읽은 대화 3개" · "쪽지함, 안 읽은 대화 99개 이상".
     */
    static dmBadgeLabel(t: Translate, count: number): string {
        const n = UtilNotification.clampCount(count);
        if (n === 0) return t('notification.dmTitle');
        return n > UtilNotification.BADGE_MAX ? t('notification.dmBadgeUnreadOver', { max: UtilNotification.BADGE_MAX }) : t('notification.dmBadgeUnread', { count: n });
    }

    /**
     * 알림 하나를 읽었을 때의 안 읽은 수(낙관적). 이미 읽은 알림이었으면 그대로입니다.
     * @param {number} count - 지금 보이는 안 읽은 수.
     * @param {boolean} wasUnread - 누른 알림이 안 읽은 상태였는가.
     * @returns {number} 줄어든 수(0 아래로 내려가지 않는다).
     */
    static afterRead(count: number, wasUnread: boolean): number {
        const n = UtilNotification.clampCount(count);
        return wasUnread ? Math.max(0, n - 1) : n;
    }

    /**
     * "모두 읽음"을 눌렀을 때의 안 읽은 수(낙관적). 화면에 불러온 안 읽은 만큼만 줄이고 나머지는 서버 값으로 맞춥니다.
     * @param {number} count - 지금 보이는 안 읽은 수.
     * @param {number} unreadShown - 화면에 불러온 알림 중 안 읽은 개수.
     * @returns {number} 줄어든 수(0 아래로 내려가지 않는다).
     */
    static afterReadAll(count: number, unreadShown: number): number {
        return Math.max(0, UtilNotification.clampCount(count) - UtilNotification.clampCount(unreadShown));
    }

    /**
     * 알림 한 줄의 아이콘 — 댓글은 말풍선, 답글은 답장, 포인트 회수는 지갑, 포인트 소멸 예정은 모래시계, 포인트 상점 환불 신청 수락은 되돌림·거절은 X 동그라미, 신고로 숨김은 가린 눈, 글·댓글 삭제는 휴지통, 신고 처리 결과는 깃발, 쪽지는 봉투, 닉네임 이미지 심사는 이미지, 기능 제한은 금지 표시. 모르는 종류는 말풍선.
     * @param {MemberNotificationType} type - 알림 종류.
     * @returns {string} PrimeIcons 클래스.
     */
    static icon(type: MemberNotificationType): string {
        if (type === 'DM') return 'pi pi-envelope';
        if (type === 'NICKNAME_IMAGE_REVIEWED') return 'pi pi-image';
        if (type === 'POINTS_REVOKED') return 'pi pi-wallet';
        if (type === 'POINTS_EXPIRING') return 'pi pi-hourglass';
        if (type === 'POINT_SHOP_REFUND_APPROVED') return 'pi pi-replay';
        if (type === 'POINT_SHOP_REFUND_REJECTED') return 'pi pi-times-circle';
        if (type === 'POST_HIDDEN' || type === 'COMMENT_HIDDEN') return 'pi pi-eye-slash';
        if (type === 'FEATURE_RESTRICTED') return 'pi pi-ban';
        if (type === 'POST_REMOVED' || type === 'COMMENT_REMOVED') return 'pi pi-trash';
        if (type === 'REPORT_RESULT') return 'pi pi-flag';
        if (type === 'OPINION_PIN_REQUESTED' || type === 'OPINION_PIN_REVIEWED') return 'pi pi-thumbtack';
        if (type === 'REPLY') return 'pi pi-reply';
        // 소모임 매니저(3단계) — 임명·맡은 일 바뀜은 방패, 해임·사퇴는 사람 빼기, 매니저가 강퇴함은 사람 빼기
        if (type === 'GROUP_MANAGER_APPOINTED' || type === 'GROUP_MANAGER_PERMISSIONS_CHANGED') return 'pi pi-shield';
        if (type === 'GROUP_MANAGER_REMOVED' || type === 'GROUP_MANAGER_RESIGNED' || type === 'GROUP_KICKED_BY_MANAGER') return 'pi pi-user-minus';
        // 소모임 일정(4단계) — 등록·수정·취소 모두 달력
        if (type === 'GROUP_EVENT') return 'pi pi-calendar';
        // 소모임 공개 범위(5단계) — 공개 요청 받음은 보내기, 승인·직접 공개·다시 공개는 지구, 거절은 X 동그라미, 비공개로 되돌림·멤버 전용은 자물쇠, 공개 범위 바뀜은 눈
        if (type === 'GROUP_PUBLISH_REQUESTED') return 'pi pi-send';
        if (type === 'GROUP_PUBLISH_APPROVED' || type === 'GROUP_POST_PUBLISHED' || type === 'GROUP_POST_RELEASED') return 'pi pi-globe';
        if (type === 'GROUP_PUBLISH_REJECTED') return 'pi pi-times-circle';
        if (type === 'GROUP_POST_MEMBERS_ONLY' || type === 'GROUP_POST_UNPUBLISHED') return 'pi pi-lock';
        if (type === 'GROUP_VISIBILITY_CHANGED') return 'pi pi-eye';
        // 공동구매(커머스 2b-4) — 주문·관심 알림은 장바구니
        if (type === 'GROUP_BUY') return 'pi pi-shopping-cart';
        if (type === 'BUSINESS') return 'pi pi-briefcase';
        if (type.startsWith('GROUP_')) return 'pi pi-users';
        return 'pi pi-comment';
    }

    /**
     * 누르면 갈 주소 — 서버가 준 공개 사이트 경로(`/posts/123`)만 받는다. 바깥 주소(`https:`·`//host`)나 빈 값은 null(읽음 처리만 한다).
     * @param {string | null | undefined} link - 알림의 `link`.
     * @returns {string | null} 앱 안 경로 또는 null.
     */
    static internalLink(link: string | null | undefined): string | null {
        if (typeof link !== 'string' || !link.startsWith('/') || link.startsWith('//') || link.includes('\\')) return null;
        return link;
    }
}
