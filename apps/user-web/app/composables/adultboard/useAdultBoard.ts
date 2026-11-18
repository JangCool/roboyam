import type { AdultBoardRelockMode, AdultBoardStatus } from '~/composables/adultboard/api/useAdultBoardApi.types';
import type { AdultBoardLoadStatus } from '~/composables/adultboard/useAdultBoard.types';
import UtilBoardMessages from '~/utils/util-board-messages';

/**
 * 내 19금 게시판 접근 설정·핀 확인 상태(공유 `useState`)와 그 동작 — 설정 화면(`/settings/adult-board`)과 핀 입력 화면(`AdultBoardPinGate`)이 같이 쓴다.
 * 계약: `docs/features/adult-board-pin-contract.md`. 서버 호출은 `useAdultBoardApi`.
 *
 * - `load()` 는 던지지 않는다(상태로 알린다). 바꾸는 동작(`enable`·`disable`·`changePin`·`setRelockMode`·`unlock`·`lock`)은 실패하면 **던진다** — 부르는 화면이 코드로 갈라 자리에 붙인다.
 * - **로그인한 사람마다 따로다** — 받아 둔 상태의 주인이 지금 로그인한 사람과 다르면 버린다.
 * - 핀번호·계정 비밀번호는 인자로만 지나간다(상태에 두지 않는다). 계정 비밀번호가 없는 회원(`passwordAvailable: false`)은 `password` 에 null 을 준다(요청에 싣지 않는다).
 * - **접근이 켜지고 꺼지면 메뉴와 게시판 목록을 다시 받는다**(`useMenu().reload()`·`useBoards().reload()` — 서버는 「성인 + 접근 켜짐」이면 성인 전용 게시판을 메뉴·목록에 싣는다, 핀 확인은 보지 않는다).
 *   켜기·끄기 뒤에는 늘, 상태를 다시 받았는데 켜짐이 달라져 있으면(다른 기기에서 바꿈) 그때도. 19금 전용 가젯은 화면(홈·가젯 페이지·게시판 레이아웃)이 들어올 때마다 새로 받는다.
 * ⚠ 컴포넌트·페이지 setup 에서 부른다(Nuxt 문맥). 돌려받은 함수는 `await` 뒤에 써도 된다.
 */

/** 요청 번호 — 늦게 온 옛 응답을 버린다 */
let request = 0;

export function useAdultBoard() {
    const api = useAdultBoardApi();
    const { user } = useAuth();
    const t = useTranslate();
    const menu = useMenu();
    const boards = useBoards();

    const status = useState<AdultBoardStatus | null>('adult-board-status', () => null);
    const ownerId = useState<string | null>('adult-board-status-owner', () => null);
    const loadStatus = useState<AdultBoardLoadStatus>('adult-board-load-status', () => 'idle');
    const loadError = useState<string>('adult-board-load-error', () => '');

    function reset(): void {
        request++;
        status.value = null;
        ownerId.value = null;
        loadStatus.value = 'idle';
        loadError.value = '';
    }

    /** 메뉴·게시판 목록을 다시 받는다(둘 다 던지지 않는다 — 못 받으면 가진 것을 그대로 둔다) */
    function refreshListings(): void {
        void menu.reload();
        void boards.reload();
    }

    /** 서버가 돌려준 상태로 바꾼다 — 그사이 받던 옛 응답은 버린다. 켜짐이 달라졌으면 메뉴·게시판 목록도 다시 받는다 */
    function apply(found: AdultBoardStatus, refresh = false): AdultBoardStatus {
        const me = user.value?.id ?? null;
        const toggled = ownerId.value === me && status.value !== null && status.value.enabled !== found.enabled;
        request++;
        status.value = found;
        ownerId.value = me;
        loadStatus.value = 'ready';
        loadError.value = '';
        if (refresh || toggled) refreshListings();
        return found;
    }

    /** 서버에서 받는다. 받아 둔 값이 있으면 그대로 보이면서 바꾼다 */
    async function load(): Promise<AdultBoardStatus | null> {
        const me = user.value?.id ?? null;
        if (!me) {
            reset();
            return null;
        }
        if (ownerId.value !== me) reset();
        const mine = ++request;
        if (!status.value) loadStatus.value = 'loading';
        loadError.value = '';
        try {
            const found = await api.status();
            if (mine !== request) return status.value;
            return apply(found);
        } catch (e) {
            if (mine === request && !status.value) {
                loadStatus.value = 'error';
                loadError.value = UtilBoardMessages.of(t, e);
            }
            return status.value;
        }
    }

    // 로그아웃·계정 전환 — 다른 사람의 설정이 남아 보이지 않게 비운다
    watch(
        () => user.value?.id ?? null,
        (id) => {
            if (id !== ownerId.value && ownerId.value !== null) reset();
        },
    );

    return {
        status,
        loadStatus,
        loadError,
        load,
        /** 메뉴·게시판 목록 다시 받기 — 접근이 꺼졌다는 것을 오류(`409 ADULT_BOARD_DISABLED`)로 처음 알았을 때 */
        refreshListings,
        /** 켜기 — 계정 비밀번호(없는 회원은 null) + 핀번호 6자리. 성공하면 이 기기는 열림 */
        enable: async (password: string | null, pin: string) => apply(await api.setEnabled({ enabled: true, pin, ...(password === null ? {} : { password }) }), true),
        /** 끄기 — 확인 없이 */
        disable: async () => apply(await api.setEnabled({ enabled: false }), true),
        /** 핀번호 바꾸기·잊었을 때 다시 정하기 — 계정 비밀번호(없는 회원은 null). 성공하면 이 기기는 열림 */
        changePin: async (password: string | null, pin: string) => apply(await api.changePin({ pin, ...(password === null ? {} : { password }) })),
        setRelockMode: async (relockMode: AdultBoardRelockMode) => apply(await api.setRelockMode(relockMode)),
        /** 핀번호 확인 — 틀리면 `ApiError`(`ADULT_BOARD_PIN_WRONG`·`ADULT_BOARD_PIN_LOCKED`)를 던진다 */
        unlock: async (pin: string) => apply(await api.unlock(pin)),
        /** 이 기기 지금 잠그기 */
        lock: async () => apply(await api.lock()),
    };
}
