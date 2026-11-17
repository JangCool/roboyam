import type { ReportSanctionContext } from '~/components/report/ReportActionDialog.types';
import type { ReviewQueueActions } from '~/components/review/ReviewQueueList.types';
import type { ReviewQueueItem, ReviewQueueStatus, ReviewQueueType } from '~/composables/review/api/useReviewQueueApi.types';
import UtilTrial from '~/utils/util-trial';

/**
 * 확인할 일 목록 한 벌 — 칭호 관리 「확인할 일」 탭(`/members/titles/review`)과 「광고 › 체험단」 확인할 일(`/ads/trials/review`)이 같이 쓴다(trial-group-contract 「구현 결정」 7).
 * 받기(종류·상태가 바뀌면 다시) + 처리(칭호는 `useTitleApi`, 체험단 확인 일은 `useTrialApi`, 협찬 숨김은 `useReviewQueueApi` — 반납 확인은 사진이 필수라 모집 상세에서) — 그리는 것은 `ReviewQueueList`. 화면마다 따로 쓰는 상태라 `useState` 가 아니다.
 * 협찬 숨김 확인에 곁들이는 글쓴이 제재(점검 D2)는 신고 처리와 같은 작성자 현황(`useSanctionPreview`)을 `sponsorSanction` 으로 내려 준다 — 익명 글·탈퇴 회원(`member` null)은 제재 불가.
 * ⚠ setup 에서 부른다.
 */
export function useReviewQueue(type: MaybeRef<ReviewQueueType | null>, status: MaybeRef<ReviewQueueStatus>) {
    const api = useReviewQueueApi();
    const titleApi = useTitleApi();
    const trialApi = useTrialApi();
    const errorOf = useApiErrorOf();
    const sanctionPerm = useSanctionPermission();
    const sponsorAuthor = useSanctionPreview();
    const sponsorAuthorName = ref('');
    const sponsorAuthorOpen = ref(false);

    const rows = ref<ReviewQueueItem[]>([]);
    const pendingCounts = ref<Partial<Record<ReviewQueueType, number>>>({});
    const loading = ref(true);
    const loadError = ref('');

    let seq = 0;
    async function load(): Promise<void> {
        const mine = ++seq;
        loading.value = true;
        loadError.value = '';
        try {
            const res = await api.list(toValue(type), toValue(status));
            if (mine !== seq) return;
            rows.value = res.items;
            pendingCounts.value = res.pending;
        } catch (e) {
            if (mine !== seq) return;
            loadError.value = errorOf(e).message;
            rows.value = [];
        } finally {
            if (mine === seq) loading.value = false;
        }
    }

    watch(
        () => [toValue(type), toValue(status)] as const,
        () => void load(),
        { immediate: true },
    );

    const actions: ReviewQueueActions = {
        approveTitle: (id) => titleApi.approve(id, { reason: '' }),
        rejectTitle: (id, reason) => titleApi.reject(id, { reason }),
        approveProof: (id) => trialApi.approveProof(id),
        rejectProof: (id, reason) => trialApi.rejectProof(id, reason),
        proofPhotoUrl: (id) => trialApi.proofPhotoUrl(id),
        /** 판정 대화상자 — 모집 상세에서 신고와 비교 사진(입고·발송 전 ↔ 반납)을 찾는다 */
        loadDamage: async (trialId, reportId) => {
            const detail = await trialApi.detail(trialId);
            const report = detail.damageReports.find((r) => r.id === reportId);
            if (!report) throw new ApiError({ status: 404, code: 'TRIAL_DAMAGE_NOT_FOUND', message: '' });
            const p = detail.participants.find((x) => x.id === report.participantId);
            return {
                report,
                beforePhotos: [...(detail.stock?.photos ?? []), ...(p ? UtilTrial.photosOf(p, 'PRE_SHIP') : [])],
                afterPhotos: p ? UtilTrial.photosOf(p, 'RETURN') : [],
            };
        },
        decideDamage: async (reportId, verdict, note) => {
            await trialApi.decide(reportId, { verdict, note });
        },
        confirmSponsor: (postId, body) => api.confirmSponsor(postId, body),
        dismissSponsor: (postId, note) => api.dismissSponsor(postId, note),
        /** 협찬 숨김 확인을 열 때 — 글쓴이 제재 현황을 받는다(`sanction.read` 가 있고 회원일 때만) */
        openSponsorAuthor: (member) => {
            sponsorAuthorOpen.value = true;
            sponsorAuthorName.value = member?.nickname ?? '';
            if (!sanctionPerm.canRead.value || !member) {
                sponsorAuthor.clear();
                return;
            }
            void sponsorAuthor.open(member.id);
        },
        retrySponsorAuthor: () => sponsorAuthor.retry(),
        reload: load,
    };

    /** 협찬 숨김 확인 대화상자의 작성자 현황 — `sanction.read` 가 없으면 null(제재 구획을 그리지 않는다) */
    const sponsorSanction = computed<ReportSanctionContext | null>(() => {
        if (!sponsorAuthorOpen.value || !sanctionPerm.canRead.value) return null;
        return {
            eligible: sponsorAuthor.userId.value !== null,
            authorName: sponsorAuthorName.value,
            preview: sponsorAuthor.preview.value,
            loading: sponsorAuthor.loading.value,
            error: sponsorAuthor.error.value,
            perm: { canManage: sanctionPerm.canManage.value, canBan: sanctionPerm.canBan.value },
        };
    });

    return { rows, pendingCounts, loading, loadError, load, actions, sponsorSanction };
}
