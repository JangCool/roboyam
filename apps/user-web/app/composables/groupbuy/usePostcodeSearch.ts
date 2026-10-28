/**
 * 다음(카카오) 우편번호 서비스 — 배송지 칸(`GroupBuyAddressFields`)의 「우편번호 찾기」. 안내 문서(postcode.map.kakao.com/guide)의 스크립트를 **한 번만** 불러온다(키가 필요 없다).
 * 팝업(`open()`)은 브라우저가 막을 수 있어서 쓰지 않고, 부르는 쪽 시트(`AppSheet`) 안 상자에 **바로 심는다**(`embed()` — pionworld2 `useDaumPostcode` 방식).
 * 주소를 직접 입력할 수 있어야 하므로 불러오지 못해도 던지지 않는다 — `embed` 가 false 면 부르는 쪽이 직접 입력으로 연다.
 * 이 컴포저블은 상태가 아니라 도구다 — 고른 주소는 부르는 쪽 폼에만 들어간다(어디에도 남기지 않는다).
 */

/** `oncomplete` 로 받는 값 중 쓰는 칸(안내 문서 기준) */
export interface PostcodeResult {
    /** 새 우편번호 5자리 */
    zonecode: string;
    /** 사용자가 고른 기본 주소(도로명 또는 지번) */
    address: string;
    roadAddress: string;
    jibunAddress: string;
    buildingName: string;
}

interface PostcodeConstructor {
    new (options: { oncomplete: (data: PostcodeResult) => void; width?: string; height?: string }): {
        embed: (container: HTMLElement, options?: { autoClose?: boolean }) => void;
    };
}

const SCRIPT_SRC = 'https://t1.daumcdn.net/mapjsapi/bundle/postcode/prod/postcode.v2.js';

/** 불러오는 중인 약속 — 직렬화하지 않는 값이라 `useState` 밖(모듈)에 둔다. 앱 전체에서 스크립트는 한 번만 */
let loading: Promise<boolean> | null = null;

/** 전역에 올라온 생성자 — 안내 문서의 `kakao.Postcode`, 예전 이름 `daum.Postcode` 도 받는다. 아직 없으면 null */
function constructorOf(): PostcodeConstructor | null {
    const w = window as unknown as { kakao?: { Postcode?: PostcodeConstructor }; daum?: { Postcode?: PostcodeConstructor } };
    return w.kakao?.Postcode ?? w.daum?.Postcode ?? null;
}

export function usePostcodeSearch() {
    /**
     * 스크립트를 불러온다. 이미 있으면 바로, 불러오는 중이면 그 약속을 기다린다. 못 불러오면 false(붙인 태그를 치워 다음에 다시 시도할 수 있다).
     * @returns {Promise<boolean>} 쓸 수 있으면 true.
     */
    function load(): Promise<boolean> {
        if (constructorOf()) return Promise.resolve(true);
        if (loading) return loading;
        loading = new Promise<boolean>((resolve) => {
            const script = document.createElement('script');
            script.src = SCRIPT_SRC;
            script.async = true;
            script.onload = () => resolve(constructorOf() !== null);
            script.onerror = () => {
                script.remove();
                resolve(false);
            };
            document.head.appendChild(script);
        }).then((ok) => {
            // 실패한 결과를 붙들고 있으면 다시 눌러도 계속 실패한다 — 비워 둔다
            if (!ok) loading = null;
            return ok;
        });
        return loading;
    }

    /**
     * 상자 안에 검색 화면을 심는다 — 주소를 고르면 `onPick`. 다시 열 때는 앞의 화면을 지우고 새로 심는다.
     * @param {HTMLElement} container - 심을 상자(높이가 정해져 있어야 한다).
     * @param {(result: PostcodeResult) => void} onPick - 고른 주소를 받는 함수.
     * @returns {Promise<boolean>} 심었으면 true, 스크립트를 못 불러왔으면 false.
     */
    async function embed(container: HTMLElement, onPick: (result: PostcodeResult) => void): Promise<boolean> {
        if (!(await load())) return false;
        const Postcode = constructorOf();
        if (!Postcode) return false;
        container.innerHTML = '';
        new Postcode({ oncomplete: onPick, width: '100%', height: '100%' }).embed(container, { autoClose: false });
        return true;
    }

    return { load, embed };
}
