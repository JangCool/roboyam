/** 설치 상태 컴포저블(`useInstallState`)이 공유하는 상태의 모양 */

/** 앱이 아는 설치 상태 — `unknown` 은 아직 안 물어봄, `failed` 는 물었지만 답을 못 받음 */
export type InstallPhase = 'unknown' | 'pending' | 'installed' | 'failed';

/** 화면이 공유하는 설치 상태 한 덩어리(`useState`) */
export interface InstallState {
    phase: InstallPhase;
    authorized: boolean;
}
