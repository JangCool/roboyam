import type { VisitSignal } from './util-visit-signal';

/**
 * ★ 방문 신호를 **보내는 곳은 이 파일 하나**다 — 이 파일만 바꾸면 수집 위치·도구를 바꿀 수 있다.
 *   (다른 수집 서버로 · 외부 분석 도구로 · 여러 곳에 함께 — 신호 모양 `VisitSignal` 과 언제 보낼지는 그대로 두고 `send` 만 고친다.)
 *
 * 지금: 공개 `POST /api/visits`(로그인·CSRF 없음 — `docs/features/site-analytics-contract.md` 「API (확정)」). 서버는 형식이 틀려도 204 로 무시한다.
 *  - `fetch(…, { keepalive: true })` — 화면을 떠나는 중에도 요청이 끝까지 간다(`sendBeacon` 과 같은 효과, JSON 머리를 붙일 수 있다).
 *  - 쿠키를 싣지 않는다(`credentials: 'omit'`) — 신호는 로그인과 무관하고, 서버가 회원과 잇지 않는다.
 *  - 실패는 조용히 버린다(다시 보내지 않는다) — 통계라 몇 개 잃어도 된다. 화면에 오류를 띄우지 않는다.
 *
 * ⚠ 레이어 규약(`layers.md` — 레이어는 서버를 부르지 않는다)의 예외: 방문 신호는 응답을 쓰지 않는 단방향 신호라 앱의 API 클라이언트(`useApiClient` — 오류 처리·CSRF)를 거치지 않고,
 *   수집 위치를 한곳에서 바꾸려고(계약 「옮기기 쉽게」) 이 레이어에 둔다. 이 파일은 `vue`·Nuxt 자동 임포트를 쓰지 않는다.
 */

/** 신호를 받는 주소 — 같은 출처(개발 서버는 `/api` 프록시가 api 서버로 잇는다) */
const ENDPOINT = '/api/visits';

export default class UtilVisitTransport {
    /**
     * 신호 하나를 보낸다 — 기다리지 않고, 실패해도 던지지 않는다.
     * @param {VisitSignal} signal - 보낼 신호(평범한 객체).
     */
    static send(signal: VisitSignal): void {
        try {
            void fetch(ENDPOINT, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(signal),
                keepalive: true,
                credentials: 'omit',
            }).catch(() => undefined);
        } catch {
            // fetch 가 없거나 keepalive 한도(64KB)를 넘는 등 — 신호 하나를 버린다
        }
    }
}
