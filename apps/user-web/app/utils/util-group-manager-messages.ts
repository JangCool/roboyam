import type { Translate } from '#layers/i18n/utils/util-i18n';
import type { DisplayTime } from '#layers/display-time/utils/util-time';
import type { GroupManageBlockedReason, GroupManagerInfo, GroupManagerPermission } from '~/composables/group/api/useGroupApi.types';
import UtilGroupManager from '~/utils/util-group-manager';

/**
 * 소모임 매니저 화면의 계산이 낀 문구 — 맡길 일 이름·요약·체크 시트 줄·인원 줄·잠금 안내·확인 대화상자. 상수 문구는 화면이 `t('groupManager.키')` 로 부른다.
 * 서버 `message` 가 있는 오류는 서버 문구를 그대로 쓴다(`UtilBoardMessages.of`). 문구 본문은 `i18n/locales/{ko,en}/groupManager.ts`.
 */

/** 체크 시트의 한 줄 */
export interface ManagerPermissionOption {
    key: GroupManagerPermission;
    name: string;
    hint: string;
    /** 켰을 때 줄 아래 보조 안내 — 없으면 null */
    note: string | null;
}

/** 제목 + 설명 */
export interface ManagerCopy {
    title: string;
    text: string;
}

/** 맡길 일 중 켰을 때 보조 안내가 붙는 것 */
const WITH_NOTE: readonly GroupManagerPermission[] = ['APPLICATIONS', 'KICK'];

export default class UtilGroupManagerMessages {
    /** 맡길 일의 고정 이름 */
    static permissionName(t: Translate, key: GroupManagerPermission): string {
        return t(`groupManager.perm.${key}`);
    }

    /** 체크 시트 다섯 줄 — 고정 순서·고정 이름 */
    static options(t: Translate): ManagerPermissionOption[] {
        return UtilGroupManager.ORDER.map((key) => ({
            key,
            name: t(`groupManager.perm.${key}`),
            hint: t(`groupManager.permHint.${key}`),
            note: WITH_NOTE.includes(key) ? t(`groupManager.permNote.${key}`) : null,
        }));
    }

    /** 맡은 일을 「가입 신청 처리 · 멤버 강퇴」 한 줄로 — 모르는 키는 버린다. 없으면 빈 글자 */
    static summary(t: Translate, keys: readonly string[] | null | undefined): string {
        return UtilGroupManager.normalize(keys ?? [])
            .map((key) => t(`groupManager.perm.${key}`))
            .join(t('groupManager.summarySeparator'));
    }

    /** 멤버 줄의 맡은 일 요약 — 내 도구가 멈춰 있으면 끝에 안내가 붙는다 */
    static summaryLine(t: Translate, keys: readonly string[] | null | undefined, paused: boolean): string {
        const base = UtilGroupManagerMessages.summary(t, keys);
        if (!paused) return base;
        return base ? `${base}${t('groupManager.summarySeparator')}${t('groupManager.toolsPaused')}` : t('groupManager.toolsPaused');
    }

    /** 관리 메인 머리의 「맡은 일: …」 */
    static headGranted(t: Translate, keys: readonly GroupManagerPermission[]): string {
        return t('groupManager.headGranted', { list: UtilGroupManagerMessages.summary(t, keys) || '—' });
    }

    /** 매니저 거르기의 보조 글 — 「2026.07 임명」. 시각이 없으면 빈 글자 */
    static appointedLabel(t: Translate, iso: string | null | undefined, time: DisplayTime): string {
        const p = time.parts(iso);
        return p ? t('groupManager.appointed', { y: p.y, mo: String(p.mo).padStart(2, '0') }) : '';
    }

    /** 체크 시트의 「매니저가 맡지 못하는 일」 줄 */
    static fixedLines(t: Translate): string[] {
        return [t('groupManager.fixedInfo'), t('groupManager.fixedManagers'), t('groupManager.fixedLeader')];
    }

    /** 임명 시트의 인원 줄 — 「매니저 2/5명 → 임명하면 3/5명」, 가득 찼으면 해임하거나 늘리라는 안내 */
    static slotsLine(t: Translate, info: GroupManagerInfo): string {
        if (UtilGroupManager.isFull(info)) return t('groupManager.slotsFull', { limit: info.limit });
        return t('groupManager.slots', { count: info.count, limit: info.limit, next: info.count + 1 });
    }

    /** 인원 숫자가 하한·상한에 닿았을 때의 이유 — 아니면 빈 글자 */
    static limitBoundHint(t: Translate, value: number, info: GroupManagerInfo): string {
        const bound = UtilGroupManager.limitBound(value, info);
        if (bound === 'FLOOR') return t('groupManager.limitFloor', { n: info.count });
        if (bound === 'CEILING') return t('groupManager.limitCeiling', { max: UtilGroupManager.limitRange(info).max });
        return '';
    }

    /** 잠금 줄을 눌렀을 때 — 맡겨지지 않은 일의 이름을 말한다 */
    static lockedSheet(t: Translate, key: GroupManagerPermission): ManagerCopy {
        return { title: t('groupManager.notDelegatedTitle'), text: t('groupManager.lockedText', { name: UtilGroupManagerMessages.permissionName(t, key) }) };
    }

    /** 매니저인데 이 화면의 일은 맡지 않았다(주소로 직접 들어옴) */
    static notDelegated(t: Translate, key: GroupManagerPermission): ManagerCopy {
        return { title: t('groupManager.notDelegatedTitle'), text: t('groupManager.notDelegatedText', { name: UtilGroupManagerMessages.permissionName(t, key) }) };
    }

    /** 해임 확인 */
    static removeConfirm(t: Translate, name: string): { title: string; message: string; confirmLabel: string } {
        return { title: t('groupManager.removeTitle', { name }), message: t('groupManager.removeMessage', { name }), confirmLabel: t('groupManager.removeConfirm') };
    }

    /** 멈춘 단추 위의 짧은 이유(`title`) */
    static blockedShort(t: Translate, reason: GroupManageBlockedReason): string {
        if (reason === 'EMAIL_NOT_VERIFIED') return t('groupManager.blockedEmail');
        return t(reason === 'BUSINESS_ACCOUNT_RESTRICTED' ? 'groupManager.blockedBusiness' : 'groupManager.blockedRestricted');
    }
}
