import UtilBusiness from '~/utils/util-business';
import UtilBusinessMessages from '~/utils/util-business-messages';
import UtilEmoticon from '~/utils/util-emoticon';
import UtilPoint from '~/utils/util-point';
import UtilPointMessages from '~/utils/util-point-messages';
import UtilPointShop from '~/utils/util-point-shop';
import UtilProfile from '~/utils/util-profile';

/** 메뉴 한 줄 */
export interface MeMenuEntry {
    to: string;
    icon: string;
    label: string;
    /** 오른쪽 작은 글씨(등급 이름·보유 포인트) */
    hint: string;
    /** 안 읽은 수(빨간 점 — 눈으로만, 수는 `ariaLabel` 에) */
    badge: string;
    ariaLabel?: string;
    testid: string;
    /** 지금 보고 있는 화면(또는 그 아래)인가 */
    active: boolean;
}

/** 묶음 — 이름이 없으면 줄만 */
export interface MeMenuGroup {
    key: string;
    title: string;
    entries: MeMenuEntry[];
}

/** 메뉴를 띄우는 화면 표시 — `definePageMeta({ meMenu: true })`, 공개 프로필은 `'self'`(내 프로필일 때만) */
export type MeMenuMeta = true | 'self';

/**
 * 내 정보 메뉴 — 데스크톱은 셸이 오른쪽 열에 두고(`MeFrame`), 모바일은 `/me` 화면이 목록으로 그린다. 두 곳이 같은 줄을 쓴다.
 * 묶음: 내 정보 · 내 활동(활동 현황 · 등급 안내 · 사업자 센터 · 내 로보티콘) · 포인트(사업자 전용 계정 제외) · 알림·쪽지.
 * 설정은 이 메뉴에 두지 않는다(사용자 결정 2026-11-05) — 헤더 계정 메뉴의 「설정」으로 가고, 설정 화면들은 설정 메뉴(`pages/settings.vue`)만 보인다.
 */
export function useMeMenu() {
    const { t } = useI18n();
    const route = useRoute();
    const { user, emailVerified, restriction } = useAuth();
    const { summary } = usePointSummary();
    const badge = useNotificationBadge();
    const dmBadge = useDmBadge();
    const { term } = useSiteTerms();

    const businessAccount = computed(() => UtilBusiness.isBusinessAccount(user.value?.role));

    /** 지금 주소가 그 화면(또는 그 아래)인가 — `/me` 는 그 화면만 */
    function isActive(to: string): boolean {
        const path = route.path.replace(/\/$/, '') || '/';
        if (to === '/me') return path === '/me';
        return path === to || path.startsWith(`${to}/`);
    }

    function entry(to: string, icon: string, label: string, testid: string, extra: Partial<MeMenuEntry> = {}): MeMenuEntry {
        return { to, icon, label, hint: '', badge: '', testid, active: isActive(to), ...extra };
    }

    const groups = computed<MeMenuGroup[]>(() => {
        const list: MeMenuGroup[] = [{ key: 'home', title: '', entries: [entry('/me', 'pi pi-user', t('me.title'), 'me-entry-home')] }];

        // 내 공개 프로필(남이 보는 모양) · 등급 안내 — `docs/features/member-activity-contract.md`
        const activity: MeMenuEntry[] = [];
        if (user.value) activity.push(entry(UtilProfile.path(user.value.id), 'pi pi-id-card', t('me.entryProfile'), 'me-entry-profile'));
        activity.push(entry('/me/grade', 'pi pi-star', t('me.entryGrade'), 'me-entry-grade', { hint: user.value?.role === 'USER' ? user.value.gradeLabel : '' }));
        // 사업자 센터 — 신청했거나 사업자 전용 계정이면 「사업자 센터」, 일반 회원이고 신청 전이면 「사업자 신청」(운영진은 줄 없음, 셸 메뉴와 같은 규칙 `UtilBusiness.entryOf`)
        const business = UtilBusiness.entryOf(user.value);
        if (business) {
            activity.push(entry(UtilBusiness.CENTER_PATH, UtilBusinessMessages.ENTRY_CENTER_ICON, business === 'CENTER' ? t('business.centerEntry') : t('business.applyEntry'), 'me-entry-business'));
        }
        // 내 로보티콘(받은·산 팩 · 구매 내역 — `docs/features/emoticon-contract.md`). 무료 팩도 있어 사업자 전용 계정도 줄이 있다
        activity.push(entry(UtilEmoticon.MY_PATH, 'pi pi-face-smile', t('emoticon.myTitle'), 'me-entry-emoticons'));
        list.push({ key: 'activity', title: t('me.menuGroupActivity'), entries: activity });

        if (!businessAccount.value) {
            // 포인트·출석체크(커머스 1-3) — 출석 기능이 있으면 「포인트·출석체크」, 오른쪽에 보유 포인트(+ 오늘 출석 전이면 「· 출석 +10P」)
            const s = summary.value;
            const hint = s ? UtilPointMessages.entryHint(t, s.balance, UtilPoint.attendable(s, { emailVerified: emailVerified.value, restriction: restriction.value }), s.rules.attendance) : '';
            list.push({
                key: 'point',
                title: t('me.menuGroupPoint'),
                entries: [
                    entry('/me/points', 'pi pi-wallet', s?.attendanceAvailable ? t('point.entry') : t('point.history'), 'me-entry-points', { hint }),
                    // 포인트 상점 · 내 아이템(`commerce-stage1-contract.md` 「1-2 화면 목업」) — 공동구매(주문 내역)와 따로
                    entry(UtilPointShop.PATH, 'pi pi-shopping-bag', term('POINT_SHOP'), 'me-entry-point-shop'),
                    entry(UtilPointShop.MY_ITEMS_PATH, 'pi pi-box', t('pointShop.myItems'), 'me-entry-my-items'),
                ],
            });
        }

        list.push({
            key: 'inbox',
            title: t('me.menuGroupInbox'),
            entries: [
                entry('/notifications', 'pi pi-bell', t('notification.title'), 'me-entry-notifications', { badge: badge.text.value, ariaLabel: badge.label.value }),
                // 쪽지함 — 안 읽은 대화 수(`docs/features/member-social-contract.md`)
                entry('/messages', 'pi pi-envelope', t('notification.dmTitle'), 'me-entry-messages', { badge: dmBadge.text.value, ariaLabel: dmBadge.label.value }),
            ],
        });

        return list;
    });

    return { groups };
}
