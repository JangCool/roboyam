import type { PostAuthor } from '~/composables/board/api/usePostApi.types';
import { mockFlags } from '~/composables/common/mock/mock-util';

/** MOCK: 스위치 `?mock=author-bio` 의 예시 소개(데모 회원은 소개가 비어 있어 카드를 볼 수 없다) */
const MOCK_SAMPLE_BIO = '로봇 조립과 3D 프린팅을 좋아해요. 주말엔 휴머노이드 영상 몰아보기 🤖';

/**
 * 글 보기의 작성자 한 줄 소개 — 회원이고 프로필이 **공개**이고 소개가 있을 때만 값(`profile-privacy-contract.md`).
 * 서버 후보는 글 상세 `author.bio`(공개 프로필의 소개만 싣는다)다. 그 칸이 오면 그것을 그대로 쓴다.
 * MOCK: 서버가 아직 주지 않아 작성자의 공개 프로필(`useUserProfileApi().profile` — 목업 공개 범위가 겹쳐 있다)에서 소개를 받아 온다.
 * 서버가 붙으면 `// MOCK:` 줄을 지우고 `author.bio` 만 쓴다(이 컴포저블도 지워도 된다).
 */
export function usePostAuthorBio(author: () => PostAuthor | null | undefined) {
    const bio = ref<string | null>(null);
    const profiles = useUserProfileApi(); // MOCK:
    let seq = 0;

    watch(
        () => {
            const a = author();
            return a ? `${a.kind}:${a.memberId ?? ''}:${a.name ?? ''}:${a.bio ?? ''}` : '';
        },
        async () => {
            const mine = ++seq;
            const a = author();
            bio.value = null;
            if (!a || a.kind !== 'MEMBER' || !a.memberId || !a.name) return;
            // REAL: bio.value = a.bio?.trim() || null;
            if (a.bio !== undefined) {
                bio.value = a.bio?.trim() || null;
                return;
            }
            // MOCK: 서버 칸이 없으면 공개 프로필에서 — 비공개면 `bio` 가 null 로 온다. 실패하면 소개 없이
            try {
                const profile = await profiles.profile(a.memberId);
                if (mine !== seq) return;
                const sample = mockFlags().has('author-bio') ? MOCK_SAMPLE_BIO : null; // MOCK: 스위치 `?mock=author-bio` — 소개가 없는 공개 회원에 예시 소개
                bio.value = profile.visibility === 'PRIVATE' ? null : profile.bio?.trim() || sample;
            } catch {
                if (mine === seq) bio.value = null;
            }
        },
        { immediate: true },
    );

    return { bio };
}
