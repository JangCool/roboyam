import UtilDisplayValues from '#layers/display-time/utils/util-display-values';
import UtilTime, { type DisplayValues } from '#layers/display-time/utils/util-time';

/**
 * 표시 기본값(시간대·시간 형식·날짜 형식) 배선의 **순수 규칙** — 서버 응답 읽기, 이 브라우저에 마지막 효과값을 기억하는 글자 다루기,
 * 기억값을 쓸지 판정, 로그인한 사람이 바뀔 때의 결정. 상태도 요청도 없다(상태는 공유 레이어의 `useDisplay` 와 `useDisplaySettings`, 서버 호출은 `useDisplayApi`).
 * 값 검사(`sanitize`)는 공유 레이어의 `UtilDisplayValues` 를 쓴다. (community `UtilDisplaySettings` 와 같은 규칙이다.)
 *
 * **왜 기억하나**: 화면은 서버 효과값을 받기 전에도 시각을 그려야 한다. 마지막으로 받은 효과값을 이 브라우저에 기억해 두었다가
 * 다음 방문의 첫 화면부터 그것으로 그린다(서버 값이 오면 바뀐다 — 처음 기본값과 다른 사이트에서 첫 화면이 깜빡이지 않게).
 *
 * **다른 사람의 기억값을 쓰지 않는다**: 기억값에는 그 값을 받은 사람의 id(비로그인은 없음)를 같이 적는다. 지금 사람과 **다르면 버리고**
 * 시스템 폴백(`UtilTime.FALLBACK_DISPLAY`)으로 시작한다 — 개인 값은 사람마다 다르다. 서버에서 못 받거나 받은 값이 깨졌으면(서버에 API 가 없어 `404` 인 경우 포함)
 * 던지지 않고 지금 쓰던 값(기억값 → 폴백)을 그대로 둔다.
 */

/** 이 브라우저에 기억한 효과값 — 값과 그 값을 받은 사람(비로그인은 null) */
export type RememberedDisplay = { values: DisplayValues; userId: string | null };

/** 화면이 지금 쓰는 표시 값과 그 값이 누구 것인지(비로그인은 null, 아직 아무도 배선하지 않았으면 undefined) */
export type DisplayWiring = { values: DisplayValues; owner: string | null | undefined };

/** `refreshPlan` 의 결과 — 지금부터 쓸 값(주인 포함)과 서버에 효과값을 다시 물을지 */
export type DisplayRefreshPlan = { wiring: { values: DisplayValues; owner: string | null }; load: boolean };

function isObject(value: unknown): value is Record<string, unknown> {
    return typeof value === 'object' && value !== null && !Array.isArray(value);
}

export default class UtilDisplaySettings {
    /** 이 브라우저에 마지막 효과값을 남기는 저장 키 — 값은 JSON(`{ values, userId? }`) */
    static readonly STORAGE_KEY = 'roboyam:display';

    /** 상태 값이 누구 것인지를 담는 상태 키(`useState`) — 효과값 자체의 상태는 공유 레이어가 갖는다 */
    static readonly OWNER_KEY = 'display-owner';

    /**
     * `GET /api/display/effective` 응답에서 표시 값을 꺼낸다.
     * @param {unknown} raw - 응답.
     * @returns {DisplayValues | null} 표시 값. 응답이 없거나 깨졌으면 null.
     */
    static fromEffectiveResponse(raw: unknown): DisplayValues | null {
        return isObject(raw) ? UtilDisplayValues.sanitize(raw.values) : null;
    }

    /**
     * `GET`·`PUT`·`DELETE /api/me/display` 응답에서 효과값(`effective`)을 꺼낸다 — 저장·되돌리기 직후 화면에 바로 적용하는 값이다.
     * @param {unknown} raw - 응답.
     * @returns {DisplayValues | null} 효과값. 응답이 깨졌으면 null.
     */
    static fromMyResponse(raw: unknown): DisplayValues | null {
        return isObject(raw) ? UtilDisplayValues.sanitize(raw.effective) : null;
    }

    /**
     * 응답에서 서비스 시간대를 꺼낸다 — 없거나 깨졌으면 코어의 서비스 시간대 상수.
     * @param {unknown} raw - `GET /api/display/effective` 응답.
     * @returns {string} 서비스 시간대 이름.
     */
    static serviceZoneOf(raw: unknown): string {
        const zone = isObject(raw) && typeof raw.serviceZoneId === 'string' ? raw.serviceZoneId.trim() : '';
        return zone !== '' && zone.length <= 64 ? zone : UtilTime.SERVICE_ZONE;
    }

    /**
     * 저장해 둔 글자를 읽는다 — 깨졌거나 모르는 모양이면 null. `userId` 가 없거나 null 이면 비로그인으로 받은 값이다.
     * @param {string | null | undefined} raw - 저장소에서 읽은 글자.
     * @returns {RememberedDisplay | null} 기억값. 쓸 수 없으면 null.
     */
    static parseStored(raw: string | null | undefined): RememberedDisplay | null {
        if (!raw) return null;
        let parsed: unknown;
        try {
            parsed = JSON.parse(raw);
        } catch {
            return null;
        }
        if (!isObject(parsed)) return null;
        const values = UtilDisplayValues.sanitize(parsed.values);
        if (!values) return null;
        const id = parsed.userId;
        if (id !== undefined && id !== null && typeof id !== 'string') return null;
        return { values, userId: typeof id === 'string' && id !== '' ? id : null };
    }

    /**
     * 저장소에 남길 글자 — 비로그인이면 `userId` 칸을 적지 않는다.
     * @param {DisplayValues} values - 효과값.
     * @param {string | null} userId - 이 값을 받은 사람. 비로그인은 null.
     * @returns {string} 저장할 글자(JSON).
     */
    static serialize(values: DisplayValues, userId: string | null): string {
        const clean = UtilDisplayValues.copy(values);
        return JSON.stringify(userId ? { values: clean, userId } : { values: clean });
    }

    /**
     * 기억값을 이 사람에게 쓸 수 있나 — 받은 사람이 지금 사람과 **같을 때만**(둘 다 비로그인도 같다) 값을 돌려준다.
     * @param {string | null | undefined} raw - 저장소에서 읽은 글자.
     * @param {string | null} userId - 지금 사람. 비로그인은 null.
     * @returns {DisplayValues | null} 쓸 수 있는 기억값. 없거나 다른 사람 것이면 null.
     */
    static rememberedFor(raw: string | null | undefined, userId: string | null): DisplayValues | null {
        const stored = UtilDisplaySettings.parseStored(raw);
        return stored && stored.userId === (userId || null) ? stored.values : null;
    }

    /**
     * 앱이 시작할 때 쓸 값 — 이 사람의 기억값, 없으면(다른 사람 것·깨짐·저장소 없음) 시스템 폴백.
     * @param {string | null | undefined} raw - 저장소에서 읽은 글자.
     * @param {string | null} userId - 지금 사람. 비로그인은 null.
     * @returns {{ values: DisplayValues; owner: string | null }} 쓸 값과 그 주인.
     */
    static initial(raw: string | null | undefined, userId: string | null): { values: DisplayValues; owner: string | null } {
        return { values: UtilDisplaySettings.rememberedFor(raw, userId) ?? UtilDisplayValues.copy(UtilTime.FALLBACK_DISPLAY), owner: userId || null };
    }

    /**
     * 갱신 시점의 결정 — 로그인한 사람이 정해졌거나 바뀌었을 때(시작·로그인·로그아웃·다른 계정) 지금부터 쓸 값과 서버에 다시 물을지를 정한다.
     *  - 지금 값의 주인과 사람이 같으면 값을 그대로 둔다. **다르면**(처음 배선하는 때 포함) 지금 값은 다른 사람 것이므로 버리고, 저장해 둔 기억값이 이 사람 것이면 그것을, 아니면 시스템 폴백을 쓴다.
     *  - 설치 전(`pending`)에는 서버가 `503 NOT_INSTALLED` 라 묻지 않는다. 그 밖에는(`failed` 포함) 묻는다 — 실패해도 앱은 지금 값으로 뜬다.
     * @param {DisplayWiring} current - 지금 쓰는 값과 그 주인.
     * @param {string | null} userId - 지금 사람. 비로그인은 null.
     * @param {string | null | undefined} raw - 저장소에서 읽은 글자.
     * @param {string} installPhase - 설치 상태(`unknown` · `pending` · `installed` · `failed`).
     * @returns {DisplayRefreshPlan} 쓸 값과 서버에 물을지.
     */
    static refreshPlan(current: DisplayWiring, userId: string | null, raw: string | null | undefined, installPhase: string): DisplayRefreshPlan {
        const id = userId || null;
        const wiring = current.owner === id ? { values: current.values, owner: id } : UtilDisplaySettings.initial(raw, id);
        return { wiring, load: installPhase !== 'pending' };
    }
}
