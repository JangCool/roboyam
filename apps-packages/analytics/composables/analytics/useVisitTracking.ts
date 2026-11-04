import type { VisitTrackingOptions } from '#layers/analytics/composables/analytics/useVisitTracking.types';
import UtilVisitEnv from '#layers/analytics/utils/util-visit-env';
import UtilVisitSignal, { type VisitKind, type VisitSignal } from '#layers/analytics/utils/util-visit-signal';
import UtilVisitStore from '#layers/analytics/utils/util-visit-store';
import UtilVisitTransport from '#layers/analytics/utils/util-visit-transport';

/** 화면이 바뀐 순간에 잡아 두는 값 — 스위치를 기다리는 동안 주소가 또 바뀌어도 그때 값으로 보낸다 */
interface VisitMoment {
    path: string;
    kind: VisitKind;
    /** 그 주소의 쿼리(`?utm_…`) */
    search: string;
    at: number;
    /** 이 페이지를 연 첫 화면인가 — `document.referrer` 는 이때만 이 화면의 유입이다(SPA 안 이동에서는 바뀌지 않는다) */
    firstOfLoad: boolean;
}

/** 한 번만 건다 — HMR·두 번 부르기 막기 */
let started = false;

/**
 * 화면이 바뀔 때(첫 진입 포함) 방문 신호를 보낸다 — 앱의 클라이언트 플러그인이 앱 시작 때 한 번 부른다(Nuxt 문맥 안에서).
 *
 * 보내는 때와 안 보내는 때(계약 `docs/features/site-analytics-contract.md` 「수집·쓰기」):
 *  - 경로가 바뀐 화면 이동이 끝났을 때(첫 화면 포함). 쿼리·해시만 바뀐 이동(쪽 넘김·정렬·탭)은 같은 화면으로 보고 보내지 않는다. 실패·취소된 이동도 보내지 않는다.
 *  - `excluded()` 면(관리자 미리보기) 보내지 않는다. `collecting()` 이 false 면(수집 꺼짐·옛 서버·사이트 정보를 못 받음) 보내지 않고 브라우저에 아무 값도 만들지 않는다.
 *  - 이용자가 이 브라우저에서 수집을 거부했으면(`UtilVisitStore.optedOut` — 처리방침 화면의 스위치) 보내지 않고 아무 값도 만들지 않는다. 화면이 바뀔 때마다 보므로 거부·허용이 바로 따른다.
 *  - 세션 첫 페이지이고 `environment()` 가 true 면 환경 분류값(`env` — 브라우저·OS·화면 너비 구간, `UtilVisitEnv.current`)을 싣는다.
 *  - 같은 경로를 2초 안에 또 보내지 않는다(새로 고침 연타 — 세션 저장소에 마지막 경로·시각이 있어 새로 고침에도 이어진다).
 *  - 세션 첫 페이지에서만 `referrer`(외부 도메인일 때만 — 이 페이지를 연 첫 화면의 `document.referrer`)·`utm` 을 싣고, 그 밖에는 `prevPath`(같은 세션의 직전 경로)를 싣는다.
 *  - `newVisitor` 는 방문자 ID 를 이번에 처음 만든 신호 하나만 true.
 *
 * @param {VisitTrackingOptions} options - 켤지·뺄지·화면 종류.
 */
export function startVisitTracking(options: VisitTrackingOptions): void {
    if (started || typeof window === 'undefined') return;
    started = true;

    const router = useRouter();
    let firstOfLoad = true;
    // 신호 차례를 지킨다 — 스위치를 기다리는 동안 다음 화면이 와도 앞 신호부터(직전 경로가 맞게)
    let queue: Promise<void> = Promise.resolve();

    function capture(path: string, name: unknown, fullPath: string): void {
        const moment: VisitMoment = {
            path: UtilVisitSignal.cleanPath(path),
            kind: options.classify(path, typeof name === 'string' ? name : null),
            search: queryOf(fullPath),
            at: Date.now(),
            firstOfLoad,
        };
        firstOfLoad = false;
        queue = queue
            .then(async () => {
                if (options.excluded?.()) return;
                if (UtilVisitStore.optedOut()) return;
                if (!(await options.collecting())) return;
                // 거부 스위치는 기다리는 동안 바뀔 수 있다 — 보내기 직전에 한 번 더 본다
                if (UtilVisitStore.optedOut()) return;
                send(moment, (await options.environment?.()) === true);
            })
            .catch(() => undefined);
    }

    router.afterEach((to, from, failure) => {
        if (failure) return;
        // 쿼리·해시만 바뀌었다 — 같은 화면(처음 진입은 `from` 이 빈 시작 위치라 지나간다)
        if (from.matched.length > 0 && to.path === from.path) return;
        capture(to.path, to.name, to.fullPath);
    });

    // 이 함수를 부르기 전에 첫 이동이 이미 끝났으면 지금 화면을 바로 센다(아니면 위 `afterEach` 가 첫 화면을 받는다)
    const current = router.currentRoute.value;
    if (current.matched.length > 0) capture(current.path, current.name, current.fullPath);
}

/** 라우트 전체 주소(`/a?b=1#c`)의 쿼리 부분(`b=1`) — 없으면 빈 글자 */
function queryOf(fullPath: string): string {
    const q = fullPath.indexOf('?');
    if (q < 0) return '';
    const hash = fullPath.indexOf('#', q);
    return fullPath.slice(q + 1, hash < 0 ? undefined : hash);
}

/**
 * 신호 하나를 만들어 보낸다 — 2초 안 같은 경로면 버린다.
 * @param moment - 화면이 바뀐 순간의 값.
 * @param withEnv - 환경 수집이 켜졌나(세션 첫 페이지에만 싣는다).
 */
function send(moment: VisitMoment, withEnv: boolean): void {
    const { session, started: sessionStarted } = UtilVisitStore.session(moment.at);
    if (!sessionStarted && UtilVisitSignal.isDuplicate(session.lastPath, session.lastAt, moment.path, moment.at)) return;

    const visitor = UtilVisitStore.visitor();
    const signal: VisitSignal = {
        visitorId: visitor.id,
        sessionId: session.id,
        path: moment.path,
        kind: moment.kind,
        prevPath: sessionStarted ? null : session.lastPath,
        referrer: sessionStarted && moment.firstOfLoad ? UtilVisitSignal.externalReferrer(document.referrer, window.location.host) : null,
        utm: sessionStarted ? UtilVisitSignal.readUtm(moment.search) : null,
        newVisitor: visitor.created,
        env: sessionStarted && withEnv ? UtilVisitEnv.current() : null,
    };
    UtilVisitStore.saveSession({ id: session.id, lastAt: moment.at, lastPath: moment.path });
    UtilVisitTransport.send(signal);
}
