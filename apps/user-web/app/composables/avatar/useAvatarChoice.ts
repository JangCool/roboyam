/** 고르기 전에 확인할 아바타 — 카탈로그 줄(`AvatarItem`)이나 내 아이템 줄에서 */
export interface AvatarChoiceTarget {
    /** 움직이는 아바타 */
    animated?: boolean;
    /** 환불 기한 안 — 고르면 환불할 수 없다 */
    refundable: boolean;
}

/**
 * 아바타 고르기 직전의 확인 — 프로필 설정 고르기·상점 아바타 탭 「바로 프로필로 쓰기」·내 아이템 「프로필로 쓰기」가 같이 쓴다(묻기만 한다 — 저장은 부르는 쪽 `useAvatarApi().select`).
 *
 * **움직이는 꾸미기는 하나만**(사용자 지시 2026-11-06): 움직이는 아바타와 움직이는 닉네임 그림(움직이는 GIF — 서버가 정지 그림 `still` 을 따로 둔 것)은 함께 쓰지 못한다. 나중에 고른 쪽이 남는다.
 *  - 움직이는 아바타를 고를 때 움직이는 닉네임 그림이 적용 중이면 묻는다. 고르면 닉네임 그림은 **지우지 않고 「쉬는 중」**이 된다 — 닉네임 그림 화면에서
 *    다시 켤 수 있고 심사를 다시 받지 않는다(다시 켜면 아바타가 마지막으로 쓴 정지 아바타로 돌아간다). 쉬는 중은 따로 저장하지 않고 「움직이는 아바타를 쓰는 중」에서 나온다.
 *  - 정지 닉네임 그림(PNG·JPG)과 움직이는 아바타, 움직이는 닉네임 그림과 정지 아바타는 함께 쓸 수 있다.
 *  - 환불 기한 안의 유료 아바타는 고르면 환불할 수 없다 — 두 가지를 같은 대화상자 하나에 합쳐 묻는다(대화상자를 두 번 띄우지 않는다).
 * ⚠ setup 에서 부른다(안에서 API·대화상자 컴포저블을 잡는다).
 */
export function useAvatarChoice() {
    const nicknameApi = useNicknameImageApi();
    const { confirm } = useConfirmDialog();
    const t = useTranslate();
    const decorations = useMyDecoration();

    /** 움직이는 닉네임 그림이 지금 적용 중인가(서버 상태 — 적용 중 그림에 정지 그림 `still` 이 있고 쉬는 중 `paused` 가 아니다). 못 읽으면 false */
    async function nicknameMotionApplied(): Promise<boolean> {
        try {
            const state = await nicknameApi.status();
            return Boolean(state.current?.still) && !state.paused;
        } catch {
            return false;
        }
    }

    /**
     * 고를지 묻는다 — 물을 것이 없으면 바로 true
     * @param avatar 고를 아바타
     * @param motionInUse 지금 움직이는 아바타를 쓰는 중(그러면 닉네임 그림은 이미 쉬고 있어 묻지 않는다)
     */
    async function confirmChoose(avatar: AvatarChoiceTarget, motionInUse = false): Promise<boolean> {
        const conflict = Boolean(avatar.animated) && !motionInUse && (await nicknameMotionApplied());
        if (!conflict && !avatar.refundable) return true;
        const lines: string[] = [];
        if (conflict) lines.push(t('avatar.motionConflictText'));
        if (avatar.refundable) lines.push(t('avatar.useConfirmText'));
        return await confirm({
            title: conflict ? t('avatar.motionConflictTitle') : t('avatar.useConfirmTitle'),
            message: lines.join('\n\n'),
            confirmLabel: conflict ? t('avatar.motionConflictConfirm') : t('avatar.useNow'),
        });
    }

    /**
     * 쉬는 닉네임 그림을 다시 쓴다 — 움직이는 아바타는 마지막으로 쓴 정지 아바타(없으면 `basic-default`)로 돌아간다. 묻기는 부르는 쪽(닉네임 그림 화면).
     * 서버 연결됨: `POST /api/me/nickname-image/resume` → 내 프로필(`avatarUrl` = 돌아간 정지 아바타). 심사는 다시 받지 않는다.
     */
    async function resumeNicknameImage(): Promise<void> {
        const profile = await nicknameApi.resume();
        decorations.applyAvatar(profile.avatarUrl, profile.avatarStillUrl ?? null);
    }

    return { confirmChoose, nicknameMotionApplied, resumeNicknameImage };
}
