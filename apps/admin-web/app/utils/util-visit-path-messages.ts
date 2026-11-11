import type { Translate } from '#layers/i18n/utils/util-i18n';

/** 이름을 고를 줄 — 페이지 순위·서랍·이동 흐름·실시간·대시보드 「인기 페이지」의 줄이 모두 이 모양을 가진다(이동의 「바로 들어옴」은 `path` 가 null) */
export interface VisitPathLike {
    path: string | null;
    title?: string | null;
    titleEn?: string | null;
}

/** 동적 이름 고르기(`useLocalizedName().localizedName`) — 컴포넌트가 넘긴다 */
type LocalizedName = (name: string, nameEn: string | null | undefined) => string;

/**
 * 묶음 주소(3단계 결정 ② — 서버가 user-web 라우트 기준으로 동적 자리를 `{id}` 로 바꿔 쌓는다) → 화면 이름 i18n 키의 끝 마디(`visitAnalytics.route.<끝>`).
 * user-web `app/pages` 의 동적 라우트(2026-11-11)를 그대로 옮겼다 — 게시판 `[slug]`·가젯 페이지 `[key]` 는 서버가 값을 남기고 제목(게시판·페이지 이름)을 주므로 여기 없다.
 * 서버 라우트 표(api `VisitCollector`)와 같이 고친다. 표에 없는 주소는 이름 없이 주소 그대로 보인다. 모르는 주소는 서버가 `/{unknown}` 한 줄로 모은다.
 */
const ROUTE_NAMES: Readonly<Record<string, string>> = {
    '/posts/{id}': 'post',
    '/posts/{id}/edit': 'postEdit',
    '/group-buys/{id}': 'groupBuy',
    '/group-buys/{id}/order': 'groupBuyOrder',
    '/groups/{id}': 'group',
    '/groups/{id}/write': 'groupWrite',
    '/groups/{id}/events/new': 'groupEventNew',
    '/groups/{id}/events/past': 'groupEventsPast',
    '/groups/{id}/events/{id}': 'groupEvent',
    '/groups/{id}/events/{id}/edit': 'groupEventEdit',
    '/groups/{id}/manage': 'groupManage',
    '/groups/{id}/manage/applications': 'groupManageApplications',
    '/groups/{id}/manage/chat': 'groupManageChat',
    '/groups/{id}/manage/edit': 'groupManageEdit',
    '/groups/{id}/manage/members': 'groupManageMembers',
    '/groups/{id}/manage/publish-requests': 'groupManagePublishRequests',
    '/users/{id}': 'user',
    '/point-shop/{id}': 'pointShopItem',
    '/robotikon/{id}': 'robotikonPack',
    '/robotikon/studio/{id}': 'robotikonStudio',
    '/messages/{id}': 'messageThread',
    '/me/orders/{id}': 'order',
    '/business/applications/{id}': 'businessApplication',
    '/{unknown}': 'unknown',
};

/**
 * 「방문자 통계」·대시보드의 페이지 이름 — 서버 제목(글 제목·게시판 이름 …)이 있으면 그것, 없고 묶음 주소면 화면 이름(「글 보기」 …), 둘 다 없으면 주소 그대로.
 * 묶음 줄은 서버가 `title`·`titleEn` 을 null 로 준다(계약 3단계 결정 ②). 주소 → 이름 표는 이 파일 한 곳이다.
 */
export default class UtilVisitPathMessages {
    /**
     * 묶음 주소의 화면 이름.
     * @param {Translate} t - 번역 함수.
     * @param {string | null} path - 주소.
     * @returns {string | null} 이름 — 표에 없으면 null.
     */
    static routeName(t: Translate, path: string | null): string | null {
        if (path === null || !Object.prototype.hasOwnProperty.call(ROUTE_NAMES, path)) return null;
        const key = ROUTE_NAMES[path];
        return key ? t(`visitAnalytics.route.${key}`) : null;
    }

    /**
     * 이름이 있는 줄인가 — 없으면 화면이 주소를 고정폭 글자로 보인다(이름 아래 작은 주소도 이름이 있을 때만).
     * @param {VisitPathLike} row - 줄.
     */
    static named(row: VisitPathLike): boolean {
        return !!row.title || (row.path !== null && Object.prototype.hasOwnProperty.call(ROUTE_NAMES, row.path));
    }

    /**
     * 줄의 이름.
     * @param {Translate} t - 번역 함수.
     * @param {LocalizedName} localizedName - 동적 이름 고르기(`useLocalizedName`).
     * @param {VisitPathLike & { path: string }} row - 줄(「바로 들어옴」은 부르는 쪽이 먼저 가른다).
     * @returns {string} 서버 제목 · 화면 이름 · 주소 차례.
     */
    static label(t: Translate, localizedName: LocalizedName, row: VisitPathLike & { path: string }): string {
        if (row.title) return localizedName(row.title, row.titleEn);
        return UtilVisitPathMessages.routeName(t, row.path) ?? row.path;
    }
}
