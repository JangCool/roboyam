import { ADULT_BOARD_PIN_DEMAND_STATE_KEY } from '~/composables/common/useApiClient';
import type { AdultBoardPinDemand } from '~/composables/adultboard/useAdultBoard.types';

/**
 * 「핀 필요」 알림을 듣는다 — 성인 전용 게시판을 여는 API 가 **어느 호출이든** `403 ADULT_BOARD_PIN_REQUIRED` 로 거절되면(`useApiClient` 가 번호를 올린다) `onDemand` 를 부른다.
 * 게시판 목록·글 보기·글쓰기 화면이 쓴다: 처음 읽다가 받은 것은 그 화면의 불러오기 오류 처리(`UtilAdultBoard.pinRequired`)가 핀 입력 화면으로 바꾸고,
 * 여기는 **이미 보던 중**(핀 확인이 만료된 뒤의 쪽 넘기기·댓글·추천·신고·등록 등)에 받은 것을 핀 입력으로 바꾸는 길이다 — 지금 그 화면이 어떤 상태인지는 `onDemand` 가 가른다.
 * 계약: `docs/features/adult-board-pin-contract.md`.
 * ⚠ 컴포넌트·페이지 setup 에서 부른다(화면이 사라지면 듣기도 끝난다).
 * @param {(lockedUntil: string | null) => void} onDemand - 알림을 받았을 때. 인자는 틀림 잠금의 끝 시각(ISO, 없으면 null).
 */
export function useAdultBoardPinDemand(onDemand: (lockedUntil: string | null) => void): void {
    const demand = useState<AdultBoardPinDemand>(ADULT_BOARD_PIN_DEMAND_STATE_KEY, () => ({ seq: 0, lockedUntil: null }));
    watch(
        () => demand.value.seq,
        () => onDemand(demand.value.lockedUntil),
    );
}
