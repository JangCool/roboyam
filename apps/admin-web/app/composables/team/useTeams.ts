import type { ApiError } from '~/composables/common/useApiClient';
import type { TeamCandidateView, TeamMemberView, TeamSaveRequest, TeamView } from '~/composables/team/api/useTeamApi.types';

/**
 * 조직 구성 화면(`/settings/teams`)의 상태 — 팀 목록과 만들기·고치기·지우기·옮기기·팀원 변경. 이 화면에서만 쓰므로 화면이 setup 에서 부르고 `useState` 로 공유하지 않는다.
 * 서버와 말하는 것은 `useTeamApi` 다.
 *
 * 쓰기 함수는 **성공하면 바뀐 값으로 목록을 갈아 끼우고, 실패하면 `ApiError` 를 그대로 던진다**(서버 `message`·`fields` 를 가공하지 않는다 — `forms.md`). 어디에 보일지(폼 안 문구 · 토스트)는 부르는 화면이 정한다.
 * 쓰기는 한 번에 하나만 돌고(`saving`), 그동안 다른 쓰기 단추는 잠근다 — 같은 팀에 두 요청이 엇갈려 서로를 덮지 않게.
 */
export function useTeams() {
    const api = useTeamApi();
    const errorOf = useApiErrorOf();

    /** `undefined` = 아직 안 받았다 · 배열 = 받았다(비었으면 팀 없음) */
    const teams = ref<TeamView[] | undefined>(undefined);
    const loading = ref(false);
    /** 목록을 받지 못했을 때의 오류 — 화면 전체 `ErrorState`(다시 시도) */
    const error = ref<ApiError | null>(null);
    /** 쓰기 요청 중 */
    const saving = ref(false);

    async function load(): Promise<void> {
        if (loading.value) return;
        loading.value = true;
        error.value = null;
        try {
            teams.value = await api.list();
        } catch (e) {
            error.value = errorOf(e);
            teams.value = undefined;
        } finally {
            loading.value = false;
        }
    }

    /** 스켈레톤·오류 화면을 거치지 않고 뒤에서 목록만 다시 받는다 — 실패하면 지금 화면을 그대로 둔다 */
    async function refresh(): Promise<void> {
        try {
            teams.value = await api.list();
        } catch {
            // 다음 조회 때 맞춰진다
        }
    }

    /** 서버가 돌려준 팀으로 목록의 그 줄만 갈아 끼운다. 없으면 맨 뒤에 붙인다 */
    function upsert(team: TeamView): void {
        const list = teams.value ?? [];
        teams.value = list.some((item) => item.teamId === team.teamId) ? list.map((item) => (item.teamId === team.teamId ? team : item)) : [...list, team];
    }

    /** 목록이 낡았다는 신호의 오류 — 팀이 사라졌거나(다른 화면에서 지움) 팀원이 생겨 지울 수 없다. 던진 뒤 뒤에서 조용히 다시 받는다 */
    const isStale = (failure: ApiError): boolean => ['TEAM_NOT_FOUND', 'TEAM_HAS_MEMBERS'].includes(failure.code);

    /** 쓰기 한 건을 돌린다 — 실패는 `ApiError` 로 바꿔 던진다 */
    async function write<T>(job: () => Promise<T>): Promise<T> {
        saving.value = true;
        try {
            return await job();
        } catch (e) {
            const failure = errorOf(e);
            if (isStale(failure)) void refresh();
            throw failure;
        } finally {
            saving.value = false;
        }
    }

    return {
        teams,
        loading,
        error,
        saving,
        load,

        /** 팀을 만든다. 만든 팀을 돌려받는다 */
        create: (body: TeamSaveRequest): Promise<TeamView> =>
            write(async () => {
                const team = await api.create(body);
                upsert(team);
                return team;
            }),

        /** 이름·설명·사용 여부를 고친다 */
        update: (teamId: string, body: TeamSaveRequest): Promise<TeamView> =>
            write(async () => {
                const team = await api.update(teamId, body);
                upsert(team);
                return team;
            }),

        /** 팀을 지운다 — 팀원이 있으면 서버가 409 로 거절한다 */
        remove: (teamId: string): Promise<void> =>
            write(async () => {
                await api.remove(teamId);
                teams.value = teams.value?.filter((item) => item.teamId !== teamId);
            }),

        /** 한 칸 옮긴다 — 이웃의 순서도 바뀌므로 목록 전부를 받아 바꾼다 */
        move: (teamId: string, direction: 'UP' | 'DOWN'): Promise<void> =>
            write(async () => {
                teams.value = await api.move(teamId, { direction });
            }),

        /** 팀원 전체 목록으로 바꾼다 — 추가·제거 모두 이것이다 */
        setMembers: (teamId: string, members: readonly Pick<TeamMemberView, 'operatorId'>[]): Promise<TeamView> =>
            write(async () => {
                const team = await api.setMembers(teamId, { operatorIds: members.map((member) => member.operatorId) });
                upsert(team);
                return team;
            }),

        /** 팀에 넣을 수 있는 운영자 — 열 때마다 새로 받는다(낡은 목록으로 넣으면 서버가 거절한다) */
        candidates: async (teamId: string): Promise<TeamCandidateView[]> => {
            try {
                return await api.candidates(teamId);
            } catch (e) {
                throw errorOf(e);
            }
        },
    };
}
