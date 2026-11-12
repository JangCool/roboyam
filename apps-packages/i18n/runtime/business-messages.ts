/**
 * 업무별 화면 문구 저장소(실행 쪽) — 빌드 모듈 `buildtime/business-messages.ts` 가 넣는 가상 모듈이 여기에 문구를 등록하고,
 * 플러그인 `plugins/business-messages.client.ts` 가 vue-i18n 에 잇는다.
 *
 * - 기본 언어(`ko`) 문구는 쓰는 화면 조각과 같이 와서 모듈이 뜰 때 `registerMessages` 로 들어온다(그 화면을 그리기 전).
 *   vue-i18n 이 만들어지기 전(엔트리 조각이 뜰 때)에 온 것은 모아 두었다가 이어 줄 때(`attachMessages`) 한꺼번에 넘긴다.
 * - 다른 언어는 바꿀 때(`switchMessagesLocale`) 지금까지 등록된 업무만 받는다. 그 뒤에 새로 뜬 화면의 업무도 지금 언어로 따라 받는다
 *   — 받는 동안은 대체 언어(한국어)로 보이고, 오면 다시 그린다(vue-i18n 의 문구 저장소는 반응형이다).
 * - 코드에서 못 찾은 키(서버가 내려준 키 등)는 vue-i18n 의 `missing` 처리기가 `requestMissingMessages` 로 그 업무를 받는다.
 *
 * ⚠ 이 파일은 Nuxt 문맥(`useNuxtApp`)을 쓰지 않는다 — 화면 조각이 뜰 때(앱이 만들어지기 전에도) 불린다.
 */
import loaders from 'virtual:roboyam-messages-loaders';

type Messages = Record<string, unknown>;
/** 받은 문구를 vue-i18n 에 넘기는 곳 */
type Sink = (locale: string, name: string, messages: Messages) => void;

/** 화면 조각과 같이 싣는 언어 — 대체 언어 */
const DEFAULT_LOCALE = 'ko';

/** 언어 → 업무 → 문구 */
const loaded = new Map<string, Map<string, Messages>>();
/** 받는 중인 것 — `언어:업무` */
const pending = new Map<string, Promise<void>>();
let sink: Sink | null = null;
/** 지금 화면 언어 */
let current = DEFAULT_LOCALE;

function isLoaded(locale: string, name: string): boolean {
    return loaded.get(locale)?.has(name) ?? false;
}

function store(locale: string, name: string, messages: Messages): void {
    let byName = loaded.get(locale);
    if (!byName) loaded.set(locale, (byName = new Map()));
    byName.set(name, messages);
    sink?.(locale, name, messages);
}

/**
 * 한 업무의 문구를 그 언어로 받는다(이미 있으면 바로 끝). 못 받으면 다음에 다시 시도할 수 있게 남기지 않는다.
 * @param {string} name - 업무 이름(파일 이름).
 * @param {string} locale - 언어 코드.
 * @returns {Promise<void>} 받아 넘긴 뒤 끝난다.
 */
export function loadMessages(name: string, locale: string): Promise<void> {
    if (isLoaded(locale, name)) return Promise.resolve();
    const key = `${locale}:${name}`;
    const running = pending.get(key);
    if (running) return running;
    const loader = loaders[name]?.[locale];
    if (!loader) return Promise.resolve();
    const task = loader()
        .then((module) => store(locale, name, module.default))
        .catch((error: unknown) => console.error(`[i18n] '${name}' 문구(${locale})를 받지 못했다`, error))
        .finally(() => pending.delete(key));
    pending.set(key, task);
    return task;
}

/**
 * 화면 조각이 들고 온 문구를 등록한다(빌드 모듈이 넣은 가상 모듈이 부른다).
 * @param {string} name - 업무 이름.
 * @param {string} locale - 들고 온 문구의 언어(기본 언어).
 * @param {Messages} messages - 문구.
 */
export function registerMessages(name: string, locale: string, messages: Messages): void {
    if (!isLoaded(locale, name)) store(locale, name, messages);
    // 다른 언어로 보고 있으면 이 업무도 그 언어로 받는다
    if (current !== locale) void loadMessages(name, current);
}

/**
 * 화면 언어를 바꾸기 전에 부른다 — 지금까지 등록된 업무의 문구를 그 언어로 받는다.
 * @param {string} locale - 바꿀 언어.
 * @returns {Promise<void>} 다 받은 뒤 끝난다.
 */
export async function switchMessagesLocale(locale: string): Promise<void> {
    current = locale;
    const names = new Set<string>();
    for (const byName of loaded.values()) for (const name of byName.keys()) names.add(name);
    await Promise.all([...names].map((name) => loadMessages(name, locale)));
}

/**
 * 없는 키를 만났을 때(vue-i18n `missing`) — 그 업무를 아직 안 받았으면 받는다.
 * @param {string} locale - 키를 찾던 언어.
 * @param {string} key - 키(`업무.무엇`).
 * @returns {boolean} 받기 시작했거나 받는 중이면 `true`(곧 다시 그려진다), 업무를 모르거나 이미 받았으면 `false`(정말 없는 키).
 */
export function requestMissingMessages(locale: string, key: string): boolean {
    const name = key.slice(0, key.indexOf('.'));
    if (!name || !loaders[name]) return false;
    const wanted = locale === DEFAULT_LOCALE ? [locale] : [locale, DEFAULT_LOCALE];
    const missing = wanted.filter((each) => !isLoaded(each, name));
    for (const each of missing) void loadMessages(name, each);
    return missing.length > 0;
}

/**
 * vue-i18n 에 잇는다 — 그때까지 모아 둔 문구를 먼저 넘긴다.
 * @param {Sink} next - 받은 문구를 vue-i18n 에 합치는 함수.
 */
export function attachMessages(next: Sink): void {
    sink = next;
    for (const [locale, byName] of loaded) for (const [name, messages] of byName) next(locale, name, messages);
}
