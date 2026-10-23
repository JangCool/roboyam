// MOCK: 닉네임 그림 API(`GET·POST·DELETE /api/me/nickname-image` · `DELETE /api/me/nickname-image/request`)의 메모리 목업 — `useNicknameImageApi.ts` 가 부른다.
// 서버가 붙으면 이 파일을 지운다. 새로고침하면 처음 상태로 돌아간다. 올린 그림은 이 브라우저 안의 주소(`blob:`)로만 보인다(서버에 올라가지 않는다).
//
// ── 개발 전용 스위치 — 주소의 `?mock=이름` (쉼표로 여러 개) ─
//   nickimg-eligible   등급과 상관없이 신청할 수 있다(기본은 로그인 정보의 등급 — 단골·베테랑만)
//   nickimg-pending    처음부터 승인 대기 신청이 있다          nickimg-rejected  처음부터 반려된 신청이 있다(이틀 뒤 재신청)
//   nickimg-current    처음부터 적용 중인 그림이 있다           nickimg-fail      상태 읽기 실패(500)
//   nickimg-apply-fail 올리기가 규격 오류(400 fields.file)      nickimg-approve   신청 8초 뒤 운영자가 승인한다(상태를 다시 읽으면 적용됨)
//
// ── 목업 규칙(가정 — 계약 「목업 가정」) ─
//   · 자격: 단골(LOYAL) 이상 + 정지 아님. 이메일 인증 전이면 올리기·취소·해제가 `403 EMAIL_NOT_VERIFIED`.
//   · 한도: PNG·JPG·GIF·WebP · 200KB · 4:1(오차 1%) · 240×60 저장 · GIF 30프레임 · 반려 뒤 72시간. GIF 프레임 수·깨진 파일은 목업이 보지 않는다.
//   · 대기 중에 다시 올리면 앞 신청을 대체한다. 승인·반려는 관리자 화면 몫이라 목업 스위치로만 흉내 낸다.

import { ApiError } from '~/composables/common/useApiClient';
import { MOCK_ERRORS, mockClone, mockDelay, mockFlags } from '~/composables/common/mock/mock-util';
import type { AuthUser } from '~/composables/auth/api/useAuthApi.types';
import type { MyNicknameImage, NicknameImageRequest, NicknameImageUploadOptions, NicknameImageView } from '~/composables/profile/api/useNicknameImageApi.types';

const DAY = 24 * 60 * 60 * 1000;
const LIMITS = { maxBytes: 200 * 1024, width: 240, height: 60, ratio: '4:1', maxGifFrames: 30, formats: ['PNG', 'JPG', 'GIF', 'WebP'], rejectedReapplyHours: 72 };
const ELIGIBLE = new Set(['LOYAL', 'VETERAN']);
const GRADE_LABEL: Record<string, string> = { SEEDLING: '새싹', REGULAR: '일반', LOYAL: '단골', VETERAN: '베테랑' };

/** 예시 그림(240×60) — 처음부터 적용·대기·반려 상태를 보일 때 쓴다 */
function sample(color: string, text: string): string {
    const svg = `<svg xmlns='http://www.w3.org/2000/svg' width='240' height='60'><rect width='240' height='60' rx='10' fill='${color}'/><text x='120' y='39' font-size='24' font-weight='700' text-anchor='middle' fill='white' font-family='sans-serif'>${text}</text></svg>`;
    return `data:image/svg+xml;charset=utf-8,${encodeURIComponent(svg)}`;
}

interface MockState {
    current: NicknameImageView | null;
    request: NicknameImageRequest | null;
}

let state: MockState | null = null;
let seq = 10;

function seed(): MockState {
    if (state) return state;
    const flags = mockFlags();
    state = { current: null, request: null };
    if (flags.has('nickimg-current')) state.current = { id: 'n1', url: sample('#2563eb', 'ROBO ★'), still: null };
    if (flags.has('nickimg-pending')) state.request = { id: 'n2', status: 'PENDING', url: sample('#16a34a', 'NEW ROBO'), createdAt: new Date(Date.now() - 2 * 60 * 60 * 1000).toISOString(), rejectReason: null, canReapplyAt: null };
    if (flags.has('nickimg-rejected')) {
        state.request = {
            id: 'n3',
            status: 'REJECTED',
            url: sample('#dc2626', 'FLASH!!'),
            createdAt: new Date(Date.now() - DAY).toISOString(),
            rejectReason: '이미지가 빠르게 깜빡여요. 깜빡임이 없는 이미지로 다시 신청해 주세요.',
            canReapplyAt: new Date(Date.now() + 2 * DAY).toISOString(),
        };
    }
    return state;
}

function view(me: AuthUser | null, restricted: boolean): MyNicknameImage {
    const s = seed();
    const grade = me?.grade ?? 'SEEDLING';
    const forced = mockFlags().has('nickimg-eligible');
    const lowGrade = !forced && !ELIGIBLE.has(grade);
    const ineligibleReason = lowGrade ? 'GRADE_TOO_LOW' : restricted ? 'SUSPENDED' : null;
    const reapplyAt = s.request?.status === 'REJECTED' ? s.request.canReapplyAt : null;
    return mockClone({
        eligible: ineligibleReason === null,
        ineligibleReason,
        grade: { code: grade, label: me?.gradeLabel || GRADE_LABEL[grade] || grade },
        requiredGrade: { code: 'LOYAL', label: GRADE_LABEL.LOYAL! },
        current: s.current,
        request: s.request,
        reapplyAt: reapplyAt && Date.parse(reapplyAt) > Date.now() ? reapplyAt : null,
        limits: LIMITS,
    });
}

function guard(me: AuthUser | null, restricted: boolean): void {
    if (me && !me.emailVerified) throw new ApiError({ status: 403, code: 'EMAIL_NOT_VERIFIED', message: '이메일 인증을 마친 뒤에 할 수 있어요.' });
    if (restricted) throw new ApiError({ status: 403, code: 'ACCOUNT_RESTRICTED', message: '이용이 정지된 동안에는 할 수 없어요.' });
}

export async function mockNicknameImageStatus(me: AuthUser | null, restricted: boolean): Promise<MyNicknameImage> {
    await mockDelay();
    if (mockFlags().has('nickimg-fail')) throw MOCK_ERRORS.internal();
    return view(me, restricted);
}

export async function mockNicknameImageApply(me: AuthUser | null, restricted: boolean, file: File, options: NicknameImageUploadOptions): Promise<MyNicknameImage> {
    guard(me, restricted);
    const current = view(me, restricted);
    if (current.ineligibleReason === 'GRADE_TOO_LOW') throw new ApiError({ status: 403, code: 'NICKNAME_IMAGE_GRADE_TOO_LOW', message: '단골 등급부터 신청할 수 있어요.' });
    if (current.reapplyAt) {
        const wait = Math.ceil((Date.parse(current.reapplyAt) - Date.now()) / 1000);
        throw new ApiError({ status: 409, code: 'NICKNAME_IMAGE_REAPPLY_TOO_SOON', message: '아직 다시 신청할 수 없어요.', retryAfterSeconds: wait, extras: { canReapplyAt: current.reapplyAt } });
    }
    if (file.size > LIMITS.maxBytes) throw MOCK_ERRORS.validation({ file: `닉네임 이미지는 200KB 이하만 올릴 수 있어요. (지금 ${Math.ceil(file.size / 1024)}KB)` });
    // 올리는 척 — 진행률을 네 번 알린다
    for (const step of [0.25, 0.5, 0.75, 1]) {
        if (options.signal?.aborted) throw new ApiError({ status: 0, code: 'ABORTED', message: '요청을 취소했어요.' });
        await new Promise((resolve) => setTimeout(resolve, 150));
        options.onProgress?.(step);
    }
    await mockDelay();
    if (mockFlags().has('nickimg-apply-fail')) throw MOCK_ERRORS.validation({ file: '가로:세로 비율이 4:1 이어야 해요. (지금 200×100)' });
    const s = seed();
    const request: NicknameImageRequest = { id: `n${++seq}`, status: 'PENDING', url: URL.createObjectURL(file), createdAt: new Date().toISOString(), rejectReason: null, canReapplyAt: null };
    s.request = request;
    if (mockFlags().has('nickimg-approve')) {
        setTimeout(() => {
            if (s.request?.id !== request.id) return;
            s.current = { id: request.id, url: request.url, still: null };
            s.request = null;
        }, 8000);
    }
    return view(me, restricted);
}

export async function mockNicknameImageCancel(me: AuthUser | null, restricted: boolean): Promise<void> {
    await mockDelay();
    guard(me, restricted);
    const s = seed();
    if (s.request?.status !== 'PENDING') throw new ApiError({ status: 404, code: 'NICKNAME_IMAGE_REQUEST_NOT_FOUND', message: '취소할 신청이 없어요.' });
    s.request = null;
}

export async function mockNicknameImageRemove(me: AuthUser | null, restricted: boolean): Promise<void> {
    await mockDelay();
    guard(me, restricted);
    const s = seed();
    if (!s.current) throw new ApiError({ status: 404, code: 'NICKNAME_IMAGE_NOT_FOUND', message: '적용 중인 닉네임 이미지가 없어요.' });
    s.current = null;
}
