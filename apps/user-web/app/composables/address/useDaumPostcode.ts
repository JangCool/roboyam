/**
 * 다음(카카오) 우편번호 서비스 — 공용 주소 입력(`AddressInput`)의 「우편번호 찾기」. 안내 문서(postcode.map.kakao.com/guide)의 스크립트를 **한 번만** 불러온다(키가 필요 없다).
 * 스크립트 주소는 관리자 설정(주소 검색 › 다음 스크립트 주소)에서 온다 — 도메인을 옮긴 적이 있는 서비스라 코드에 박지 않는다.
 * 팝업(`open()`)은 브라우저가 막을 수 있어서 쓰지 않고, 부르는 쪽 시트(`AppSheet`) 안 상자에 **바로 심는다**(`embed()` — pionworld2 `useDaumPostcode` 방식).
 * 못 불러와도 던지지 않는다 — `embed` 가 false 면 부르는 쪽이 자체 주소 DB(설정이 허락할 때)나 직접 입력으로 넘어간다.
 * 이 컴포저블은 상태가 아니라 도구다 — 고른 주소는 부르는 쪽 폼에만 들어간다(어디에도 남기지 않는다).
 */

/** `oncomplete` 로 받는 값 중 쓰는 칸(안내 문서 기준) */
export interface DaumPostcodeResult {
    /** 새 우편번호 5자리 */
    zonecode: string;
    /** 사용자가 고른 기본 주소(도로명 또는 지번) */
    address: string;
    roadAddress: string;
    jibunAddress: string;
    buildingName: string;
}

interface DaumPostcodeConstructor {
    new (options: { oncomplete: (data: DaumPostcodeResult) => void; width?: string; height?: string }): {
        embed: (container: HTMLElement, options?: { autoClose?: boolean }) => void;
    };
}

/** 망이 막혀 응답도 오류도 오지 않을 때 기다리는 한도 — 넘으면 못 불러온 것으로 본다 */
const LOAD_TIMEOUT_MS = 8000;

/** 불러오는 중인 약속과 그 주소 — 직렬화하지 않는 값이라 `useState` 밖(모듈)에 둔다. 앱 전체에서 스크립트는 한 번만 */
let loading: Promise<boolean> | null = null;
let loadingSrc: string | null = null;

/** 전역에 올라온 생성자 — 안내 문서의 `kakao.Postcode`, 예전 이름 `daum.Postcode` 도 받는다. 아직 없으면 null */
function constructorOf(): DaumPostcodeConstructor | null {
    const w = window as unknown as { kakao?: { Postcode?: DaumPostcodeConstructor }; daum?: { Postcode?: DaumPostcodeConstructor } };
    return w.kakao?.Postcode ?? w.daum?.Postcode ?? null;
}

export function useDaumPostcode() {
    /**
     * 스크립트를 불러온다. 이미 있으면 바로, 같은 주소를 불러오는 중이면 그 약속을 기다린다. 못 불러오면 false(붙인 태그를 치워 다음에 다시 시도할 수 있다).
     * @param {string} src - 스크립트 주소(설정 값).
     * @returns {Promise<boolean>} 쓸 수 있으면 true.
     */
    function load(src: string): Promise<boolean> {
        if (constructorOf()) return Promise.resolve(true);
        if (loading && loadingSrc === src) return loading;
        loadingSrc = src;
        loading = new Promise<boolean>((resolve) => {
            const script = document.createElement('script');
            const timer = setTimeout(() => fail(), LOAD_TIMEOUT_MS);
            function fail(): void {
                clearTimeout(timer);
                script.remove();
                resolve(false);
            }
            script.src = src;
            script.async = true;
            script.onload = () => {
                clearTimeout(timer);
                resolve(constructorOf() !== null);
            };
            script.onerror = fail;
            document.head.appendChild(script);
        }).then((ok) => {
            // 실패한 결과를 붙들고 있으면 다시 눌러도 계속 실패한다 — 비워 둔다
            if (!ok) {
                loading = null;
                loadingSrc = null;
            }
            return ok;
        });
        return loading;
    }

    /**
     * 상자 안에 검색 화면을 심는다 — 주소를 고르면 `onPick`. 다시 열 때는 앞의 화면을 지우고 새로 심는다.
     * @param {HTMLElement} container - 심을 상자(높이가 정해져 있어야 한다).
     * @param {string} src - 스크립트 주소(설정 값).
     * @param {(result: DaumPostcodeResult) => void} onPick - 고른 주소를 받는 함수.
     * @returns {Promise<boolean>} 심었으면 true, 스크립트를 못 불러왔으면 false.
     */
    async function embed(container: HTMLElement, src: string, onPick: (result: DaumPostcodeResult) => void): Promise<boolean> {
        if (!(await load(src))) return false;
        const Postcode = constructorOf();
        if (!Postcode) return false;
        container.innerHTML = '';
        new Postcode({ oncomplete: onPick, width: '100%', height: '100%' }).embed(container, { autoClose: false });
        return true;
    }

    return { load, embed };
}
