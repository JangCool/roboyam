/**
 * 포인트 게임 관리자 설정 모양 — 서버 `PointGameSettingsView`(shared)·`PointGameSettingsSaveRequest`(admin) 와 같다.
 * 지금은 로또 번호 뽑기의 「당첨 번호 확인」 링크(복권 사업자 이름·주소)만 — 사업자가 바뀔 수 있어 운영자가 바꾼다. 룰렛 값·확률·횟수는 아직 고정(설정 칸 없음).
 */

/** `GET /api/admin/point-games/settings` */
export interface PointGameSettingsView {
    /** 복권 사업자 이름(회원 화면 「당첨 번호 확인 · {이름}」) */
    lotteryProviderName: string;
    /** 당첨 확인 링크 — `https://` 절대 주소 */
    lotteryCheckUrl: string;
    /** 처음 값(되돌리기 안내) */
    defaultProviderName: string;
    defaultCheckUrl: string;
    /** 마지막 저장 — 바꾼 적 없으면 null */
    updatedAt: string | null;
    updatedBy: string | null;
}

/** `PUT /api/admin/point-games/settings` — 두 칸을 한 번에 */
export interface PointGameSettingsSaveRequest {
    lotteryProviderName: string;
    lotteryCheckUrl: string;
}
