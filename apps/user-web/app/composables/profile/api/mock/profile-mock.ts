// MOCK: 내 프로필 API(`GET`·`PUT /api/me/profile`)의 메모리 목업 — `useProfileApi.ts` 가 이 파일의 함수를 부른다. 서버가 붙으면 `profile/api/mock/` 와 함께 지운다.
// 닉네임은 로그인 정보(`/api/auth/me`)의 것을 처음 값으로 쓴다(목업이 서버 닉네임을 실제로 바꾸지는 않는다 — 새로고침하면 처음 값으로 돌아간다).
//
// ── 개발 전용 스위치 — 주소의 `?mock=이름` (쉼표로 여러 개) ─
//   profile-fail       읽기 실패(500) → 화면 오류 상태
//   profile-save-fail  저장 실패(500) → 폼 위 오류 한 줄
//   profile-cooldown   닉네임을 바꾼 지 얼마 안 됨 → 닉네임 칸 잠금 + 다음 변경 가능 날짜 안내
//
// ── 목업 규칙(가정 — 계약 「목업 가정」) ─
//   · 닉네임 규칙은 화면 검사(`UtilValidation.nickname`)와 같고, 어기면 `400 VALIDATION fields.nickname`.
//   · `admin`·`운영자`·`관리자`·`roboyam` 이 들어가면 예약어 `400 VALIDATION fields.nickname`, `taken`·`사용중` 이면 `409 NICKNAME_TAKEN`.
//   · 닉네임을 바꾸면 30일 동안 다시 못 바꾼다 — 그 안에 바꾸려 하면 `409 NICKNAME_CHANGE_TOO_SOON`(`extras.availableAt`).
//   · 한 줄 소개는 앞뒤 공백을 지우고 100자까지, 넘으면 `400 VALIDATION fields.bio`.

import { ApiError } from '~/composables/common/useApiClient';
import { MOCK_ERRORS, mockClone, mockDelay, mockFlags } from '~/composables/common/mock/mock-util';
import type { MyProfile, MyProfileRequest } from '~/composables/profile/api/useProfileApi.types';
import { PROFILE_BIO_MAX } from '~/composables/profile/api/useProfileApi.types';
import UtilValidation from '~/utils/util-validation';

const DAY = 24 * 60 * 60 * 1000;
const INTERVAL_DAYS = 30;
const RESERVED = ['admin', '운영자', '관리자', 'roboyam'];
const TAKEN = ['taken', '사용중'];

let store: MyProfile | null = null;

function initial(nickname: string): MyProfile {
    const cooldown = mockFlags().has('profile-cooldown');
    const changedAt = cooldown ? new Date(Date.now() - 3 * DAY).toISOString() : null;
    return {
        nickname,
        bio: '로보얌에서 이것저것 구경하는 중이에요.',
        joinedAt: new Date(Date.now() - 128 * DAY).toISOString(),
        nicknameChangedAt: changedAt,
        nicknameChangeAvailableAt: changedAt ? new Date(Date.parse(changedAt) + INTERVAL_DAYS * DAY).toISOString() : null,
        nicknameChangeIntervalDays: INTERVAL_DAYS,
    };
}

function ensure(nickname: string): MyProfile {
    if (!store) store = initial(nickname);
    return store;
}

export async function mockMyProfile(nickname: string): Promise<MyProfile> {
    await mockDelay();
    if (mockFlags().has('profile-fail')) throw MOCK_ERRORS.internal();
    return mockClone(ensure(nickname));
}

export async function mockSaveMyProfile(currentNickname: string, body: MyProfileRequest): Promise<MyProfile> {
    await mockDelay();
    if (mockFlags().has('profile-save-fail')) throw MOCK_ERRORS.internal();
    const profile = ensure(currentNickname);

    const nickname = body.nickname;
    const bio = body.bio.trim();
    const fields: Record<string, string> = {};
    const problem = UtilValidation.nickname(nickname);
    if (problem === 'REQUIRED') fields.nickname = '닉네임을 입력해 주세요.';
    else if (problem === 'CHARS') fields.nickname = '한글·영문·숫자·밑줄과 글자 사이의 공백 한 칸만 쓸 수 있어요.';
    else if (problem === 'LENGTH') fields.nickname = `닉네임은 ${UtilValidation.NICKNAME_MIN}~${UtilValidation.NICKNAME_MAX}자로 입력해 주세요.`;
    else if (RESERVED.some((word) => nickname.toLowerCase().includes(word))) fields.nickname = '쓸 수 없는 닉네임이에요.';
    if (bio.length > PROFILE_BIO_MAX) fields.bio = `한 줄 소개는 ${PROFILE_BIO_MAX}자까지 쓸 수 있어요.`;
    if (Object.keys(fields).length > 0) throw MOCK_ERRORS.validation(fields);

    const changing = nickname !== profile.nickname;
    if (changing) {
        if (profile.nicknameChangeAvailableAt && Date.parse(profile.nicknameChangeAvailableAt) > Date.now()) {
            throw new ApiError({
                status: 409,
                code: 'NICKNAME_CHANGE_TOO_SOON',
                message: `닉네임은 바꾼 뒤 ${INTERVAL_DAYS}일이 지나야 다시 바꿀 수 있어요.`,
                extras: { availableAt: profile.nicknameChangeAvailableAt },
            });
        }
        if (TAKEN.includes(nickname)) throw new ApiError({ status: 409, code: 'NICKNAME_TAKEN', message: '이미 쓰고 있는 닉네임이에요. 다른 닉네임을 입력해 주세요.' });
        const now = new Date();
        profile.nickname = nickname;
        profile.nicknameChangedAt = now.toISOString();
        profile.nicknameChangeAvailableAt = new Date(now.getTime() + INTERVAL_DAYS * DAY).toISOString();
    }
    profile.bio = bio;
    return mockClone(profile);
}
