// MOCK: 내 프로필 API(`GET`·`PUT /api/me/profile`)의 메모리 목업 — `useProfileApi.ts` 가 이 파일의 함수를 부른다. 서버가 붙으면 `profile/api/mock/` 와 함께 지운다.
// 닉네임은 로그인 정보(`/api/auth/me`)의 것을 처음 값으로 쓴다(목업이 서버 닉네임을 실제로 바꾸지는 않는다 — 새로고침하면 처음 값으로 돌아간다).
//
// ── 개발 전용 스위치 — 주소의 `?mock=이름` (쉼표로 여러 개) ─
//   profile-fail       읽기 실패(500) → 화면 오류 상태
//   profile-save-fail  저장 실패(500) → 저장 줄 안 오류 한 줄
//   profile-used-up    가입 뒤 1번의 닉네임 변경을 이미 썼다 → 닉네임 칸 잠금 + "더 바꿀 수 없어요"
//   profile-stale      화면은 바꿀 수 있다고 보는데 저장 때 서버가 `409 NICKNAME_CHANGE_LIMIT` — 다른 탭에서 먼저 바꾼 경우
//   profile-restricted 로그인 정보는 정상인데 저장 때 서버가 `403 ACCOUNT_RESTRICTED`(방금 정지됨) → 화면이 읽기 전용 + 제재 안내로 바뀐다
//   (로그인 정보의 `restriction` 이 정지·영구 정지면 스위치 없이도 처음부터 읽기 전용이고, 저장하면 같은 403 이다)
//
// ── 목업 규칙(가정 — 계약 「목업 가정」) ─
//   · 닉네임 규칙은 화면 검사(`UtilValidation.nickname`)와 같고, 어기면 `400 VALIDATION fields.nickname`.
//   · `admin`·`운영자`·`관리자`·`roboyam` 이 들어가면 예약어 `400 VALIDATION fields.nickname`, `taken`·`사용중` 이면 `409 NICKNAME_TAKEN`.
//   · 닉네임 변경은 가입 뒤 1번(`FREE_LIMIT` — 서버 상수). 그 1번은 언제든(날짜 대기 없음), 이미 썼으면 `409 NICKNAME_CHANGE_LIMIT`. 변경권은 없다.
//   · 닉네임이 그대로인 저장(소개만)은 횟수를 쓰지 않고 변경 규칙·중복 검사도 하지 않는다.
//   · 정지·영구 정지 중(로그인 정보 `restriction`)이면 저장은 `403 ACCOUNT_RESTRICTED`(읽기는 된다 — 글쓰기와 같다).
//   · 한 줄 소개는 앞뒤 공백을 지우고 100자까지, 넘으면 `400 VALIDATION fields.bio`.

import { ApiError } from '~/composables/common/useApiClient';
import { MOCK_ERRORS, mockClone, mockDelay, mockFlags } from '~/composables/common/mock/mock-util';
import type { MyProfile, MyProfileRequest, NicknameChange } from '~/composables/profile/api/useProfileApi.types';
import { PROFILE_BIO_MAX } from '~/composables/profile/api/useProfileApi.types';
import UtilValidation from '~/utils/util-validation';

const DAY = 24 * 60 * 60 * 1000;
const FREE_LIMIT = 1;
const RESERVED = ['admin', '운영자', '관리자', 'roboyam'];
const TAKEN = ['taken', '사용중'];

/** 메모리 상태 — 응답의 `nicknameChange` 는 이것으로 그때그때 만든다 */
interface Store {
    nickname: string;
    bio: string;
    joinedAt: string;
    /** 가입 뒤 닉네임을 바꾼 횟수 */
    changeCount: number;
}

let store: Store | null = null;

function nicknameChangeOf(s: Store): NicknameChange {
    return { allowed: s.changeCount < FREE_LIMIT, changeCount: s.changeCount, freeLimit: FREE_LIMIT };
}

function view(s: Store): MyProfile {
    return mockClone({ nickname: s.nickname, bio: s.bio, joinedAt: s.joinedAt, nicknameChange: nicknameChangeOf(s) });
}

function initial(nickname: string): Store {
    const flags = mockFlags();
    const ago = (days: number) => new Date(Date.now() - days * DAY).toISOString();
    const changeCount = flags.has('profile-used-up') ? FREE_LIMIT : 0;
    return { nickname, bio: '로보얌에서 로봇·AI 이야기를 구경하는 중이에요.', joinedAt: ago(128), changeCount };
}

function ensure(nickname: string): Store {
    if (!store) store = initial(nickname);
    return store;
}

export async function mockMyProfile(nickname: string): Promise<MyProfile> {
    await mockDelay();
    if (mockFlags().has('profile-fail')) throw MOCK_ERRORS.internal();
    return view(ensure(nickname));
}

/** @param restricted - 로그인 정보로 본 정지·영구 정지 여부(`UtilSanction.isRestricted`) */
export async function mockSaveMyProfile(currentNickname: string, restricted: boolean, body: MyProfileRequest): Promise<MyProfile> {
    await mockDelay();
    if (mockFlags().has('profile-save-fail')) throw MOCK_ERRORS.internal();
    if (restricted || mockFlags().has('profile-restricted')) throw new ApiError({ status: 403, code: 'ACCOUNT_RESTRICTED', message: '이용이 정지된 동안에는 프로필을 바꿀 수 없어요.' });
    const profile = ensure(currentNickname);

    const nickname = body.nickname;
    const bio = body.bio.trim();
    const changing = nickname !== profile.nickname;
    const fields: Record<string, string> = {};
    const problem = changing ? UtilValidation.nickname(nickname) : null;
    if (problem === 'REQUIRED') fields.nickname = '닉네임을 입력해 주세요.';
    else if (problem === 'CHARS') fields.nickname = '한글·영문·숫자·밑줄과 글자 사이의 공백 한 칸만 쓸 수 있어요.';
    else if (problem === 'LENGTH') fields.nickname = `닉네임은 ${UtilValidation.NICKNAME_MIN}~${UtilValidation.NICKNAME_MAX}자로 입력해 주세요.`;
    else if (changing && RESERVED.some((word) => nickname.toLowerCase().includes(word))) fields.nickname = '쓸 수 없는 닉네임이에요.';
    if (bio.length > PROFILE_BIO_MAX) fields.bio = `한 줄 소개는 ${PROFILE_BIO_MAX}자까지 쓸 수 있어요.`;
    if (Object.keys(fields).length > 0) throw MOCK_ERRORS.validation(fields);

    if (changing) {
        // 다른 탭에서 방금 1번을 썼다고 친다
        if (mockFlags().has('profile-stale')) profile.changeCount = FREE_LIMIT;
        if (!nicknameChangeOf(profile).allowed) {
            throw new ApiError({ status: 409, code: 'NICKNAME_CHANGE_LIMIT', message: '닉네임을 이미 바꿔서 더 바꿀 수 없어요. 닉네임은 가입 뒤 1번만 바꿀 수 있어요.' });
        }
        if (TAKEN.includes(nickname)) throw new ApiError({ status: 409, code: 'NICKNAME_TAKEN', message: '이미 쓰고 있는 닉네임이에요. 다른 닉네임을 입력해 주세요.' });
        profile.nickname = nickname;
        profile.changeCount += 1;
    }
    profile.bio = bio;
    return view(profile);
}
