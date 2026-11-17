import type { ReportSanctionContext } from '~/components/report/ReportActionDialog.types';
import type { ReviewQueueActions } from '~/components/review/ReviewQueueList.types';
import type { ReviewQueueItem, ReviewQueueStatus, ReviewQueueType } from '~/composables/review/api/useReviewQueueApi.types';
import type { SanctionPreview } from '~/composables/sanction/api/useSanctionApi.types';
import { ApiError } from '~/composables/common/useApiClient';
import UtilTrial from '~/utils/util-trial';

/**
 * 확인할 일 목록 한 벌 — 칭호 관리 「확인할 일」 탭(`/members/titles/review`)과 「광고 › 체험단」 확인할 일(`/ads/trials/review`)이 같이 쓴다(trial-group-contract 「구현 결정」 7).
 * 받기(종류·상태가 바뀌면 다시) + 처리(칭호는 `useTitleApi`, 체험단 확인 일은 `useTrialApi`, 협찬 숨김은 `useReviewQueueApi` — 반납 확인은 사진이 필수라 모집 상세에서) — 그리는 것은 `ReviewQueueList`. 화면마다 따로 쓰는 상태라 `useState` 가 아니다.
 * 협찬 숨김 확인에 곁들이는 글쓴이 제재(점검 D2)는 신고 처리와 같은 작성자 현황(`useSanctionPreview`)을 `sponsorSanction` 으로 내려 준다 — 익명 글은 봉인 미리보기(`refs.anonymousPostId`),
 * `refs.sanctionable` "false"(비회원·탈퇴·파기)면 제재 불가.
 * ⚠ setup 에서 부른다.
 */
export function useReviewQueue(type: MaybeRef<ReviewQueueType | null>, status: MaybeRef<ReviewQueueStatus>) {
    const api = useReviewQueueApi();
    const titleApi = useTitleApi();
    const trialApi = useTrialApi();
    const errorOf = useApiErrorOf();
    const sanctionPerm = useSanctionPermission();
    const sanctionApi = useSanctionApi();
    const t = useTranslate();
    const sponsorAuthor = useSanctionPreview();
    const sponsorAnon = anonymousPreview();
    const sponsorAuthorName = ref('');
    const sponsorAuthorOpen = ref(false);

    /**
     * 익명 글의 봉인된 작성자 현황(`GET /api/admin/sanctions/preview?anonymousPostId=` — 신고 사건 서랍 「이 글 작성자 제재」와 같은 API, 응답 `user.sealed` true).
     * 회원 기준인 `useSanctionPreview` 와 달리 글 ID 로 받는다. 늦게 온 앞 대상 응답은 버린다.
     */
    function anonymousPreview() {
        const postId = ref<string | null>(null);
        const preview = ref<SanctionPreview | null>(null);
        const loading = ref(false);
        const error = ref<ApiError | null>(null);
        let ticket = 0;
        async function open(id: string): Promise<void> {
            const mine = ++ticket;
            postId.value = id;
            preview.value = null;
            error.value = null;
            loading.value = true;
            try {
                const found = await sanctionApi.previewAnonymous({ postId: id, commentId: null });
                if (mine === ticket) preview.value = found;
            } catch (e) {
                if (mine === ticket) error.value = errorOf(e);
            } finally {
                if (mine === ticket) loading.value = false;
            }
        }
        function clear(): void {
            ticket++;
            postId.value = null;
            preview.value = null;
            error.value = null;
            loading.value = false;
        }
        return { postId, preview, loading, error, open, clear, retry: () => (postId.value ? void open(postId.value) : undefined) };
    }

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
        /**
         * 협찬 숨김 확인을 열 때 — 글쓴이 제재 현황을 받는다(`sanction.read` 가 있을 때만). 회원 글은 `?userId=`, 익명 글은 봉인 미리보기 `?anonymousPostId=`.
         * `refs.sanctionable` "false"(비회원·탈퇴·파기)면 받지 않는다 — 칸이 없는 옛 응답은 `member` 가 있는지로 본다.
         */
        openSponsorAuthor: (row) => {
            sponsorAuthorOpen.value = true;
            const anonymous = row.refs.anonymous === 'true';
            const sanctionable = row.refs.sanctionable === undefined ? row.member !== null : row.refs.sanctionable === 'true';
            const anonymousPostId = anonymous && sanctionable ? (row.refs.anonymousPostId ?? null) : null;
            sponsorAuthorName.value = anonymous ? t('anonymousAuthor.sanctionTarget') : (row.member?.nickname ?? '');
            sponsorAuthor.clear();
            sponsorAnon.clear();
            if (!sanctionPerm.canRead.value || !sanctionable) return;
            if (anonymousPostId) void sponsorAnon.open(anonymousPostId);
            else if (!anonymous && row.member) void sponsorAuthor.open(row.member.id);
        },
        retrySponsorAuthor: () => (sponsorAnon.postId.value ? sponsorAnon.retry() : sponsorAuthor.retry()),
        reload: load,
    };

    /** 협찬 숨김 확인 대화상자의 작성자 현황 — `sanction.read` 가 없으면 null(제재 구획을 그리지 않는다) */
    const sponsorSanction = computed<ReportSanctionContext | null>(() => {
        if (!sponsorAuthorOpen.value || !sanctionPerm.canRead.value) return null;
        const perm = { canManage: sanctionPerm.canManage.value, canBan: sanctionPerm.canBan.value };
        // 익명 글 — 봉인 미리보기(`preview.user.sealed` true)로 단계를 고른다. 영구 정지는 닉네임 대신 확인 표시(`SanctionStepPicker`), 보낼 때 닉네임 없음(`UtilSanction.stepRequest`)
        if (sponsorAnon.postId.value !== null) {
            return {
                eligible: true,
                authorName: sponsorAuthorName.value,
                preview: sponsorAnon.preview.value,
                loading: sponsorAnon.loading.value,
                error: sponsorAnon.error.value,
                perm,
            };
        }
        return {
            eligible: sponsorAuthor.userId.value !== null,
            authorName: sponsorAuthorName.value,
            preview: sponsorAuthor.preview.value,
            loading: sponsorAuthor.loading.value,
            error: sponsorAuthor.error.value,
            perm,
        };
    });

    return { rows, pendingCounts, loading, loadError, load, actions, sponsorSanction };
}
