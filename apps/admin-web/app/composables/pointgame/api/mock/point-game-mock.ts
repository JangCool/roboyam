// MOCK: 포인트 게임 관리자 설정(당첨 확인 링크) — 서버에 칸이 없어(화면 확정 전, 2026-11-16) 이 탭의 메모리에만 둔다.
// 새로 고치면 처음 값으로 돌아가고, 공개 사이트(user-web)에는 닿지 않는다(user-web 은 자기 목업 `composables/pointgame/api/mock/point-game-mock.ts` 의 같은 기본값을 쓴다).
// 서버 API 가 생기면 이 파일과 `usePointGameAdminApi` 의 MOCK 줄을 지운다. 문구는 서버 몫이라 번역 키로 옮기지 않는다.

import type { PointGameSettingsSaveRequest, PointGameSettingsView } from '~/composables/pointgame/api/usePointGameAdminApi.types';
import { ApiError } from '~/composables/common/useApiClient';
import UtilPointGame from '~/utils/util-point-game';

const DEFAULT_PROVIDER = '동행복권';
const DEFAULT_URL = 'https://dhlottery.co.kr';

let state: PointGameSettingsView = {
    lotteryProviderName: DEFAULT_PROVIDER,
    lotteryCheckUrl: DEFAULT_URL,
    defaultProviderName: DEFAULT_PROVIDER,
    defaultCheckUrl: DEFAULT_URL,
    updatedAt: null,
    updatedBy: null,
};

function delay(): Promise<void> {
    return new Promise((resolve) => setTimeout(resolve, 300 + Math.random() * 300));
}

export default class PointGameAdminMock {
    static async settings(): Promise<PointGameSettingsView> {
        await delay();
        return { ...state };
    }

    /** 서버가 할 검사를 흉내 낸다(화면도 `UtilPointGame` 으로 미리 막는다) */
    static async save(body: PointGameSettingsSaveRequest, by: string | null): Promise<PointGameSettingsView> {
        await delay();
        const name = body.lotteryProviderName.trim();
        const url = body.lotteryCheckUrl.trim();
        const fields: Record<string, string> = {};
        if (UtilPointGame.providerNameProblem(name)) fields.lotteryProviderName = `복권 사업자 이름은 1~${UtilPointGame.PROVIDER_NAME_MAX}자로 적어 주세요.`;
        if (UtilPointGame.checkUrlProblem(url)) fields.lotteryCheckUrl = 'https:// 로 시작하는 주소를 적어 주세요.';
        if (Object.keys(fields).length) throw new ApiError({ status: 400, code: 'VALIDATION', message: '입력한 값을 확인해 주세요.', fields });
        state = { ...state, lotteryProviderName: name, lotteryCheckUrl: url, updatedAt: new Date().toISOString(), updatedBy: by };
        return { ...state };
    }
}
