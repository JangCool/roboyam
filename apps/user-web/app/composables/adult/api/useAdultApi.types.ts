/**
 * 성인 확인 API 가 주고받는 모양 — **서버에 아직 없다**(목업). 모양은 community 4차 계약 B절(`GET·PUT /api/me/adult`) 그대로다.
 * 지금은 출생연도 **자기 입력**(`SELF`)이고, 본인인증(`IDENTITY` — 커머스 세션 0-4)이 생기면 같은 칸을 채운다. 계약 후보: `docs/features/member-social-contract.md`.
 * ⚠ 출생연도는 개인정보다 — 로그·오류 문구·주소·저장소에 남기지 않는다.
 */

/** 성인 확인 방식 — 지금은 `SELF`, 나중에 `IDENTITY` */
export type AdultMethod = 'SELF' | 'IDENTITY';

/** `GET·PUT /api/me/adult` 응답 — `minYear`~`maxYear` 가 고를 수 있는 출생연도(올해−100 ~ 올해−14) */
export interface AdultStatus {
    birthYear: number | null;
    adult: boolean;
    method: AdultMethod | null;
    confirmedAt: string | null;
    minYear: number;
    maxYear: number;
}

/** `PUT /api/me/adult` 요청 — `confirm` 은 「입력한 출생연도가 사실과 같음」 확인(`true` 여야 한다) */
export interface AdultUpdateRequest {
    birthYear: number;
    confirm: boolean;
}
