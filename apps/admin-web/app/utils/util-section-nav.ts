/** 구역 내비에 오른 구획 하나 — `AppGadget` 이 `anchor` 로 스스로 등록한 것 */
export type SectionNavEntry = {
    /** 등록한 구획 인스턴스의 키(같은 구획이 다시 등록·해제할 때 짝을 맞춘다) */
    key: string;
    /** 구획의 주소(`id`) — 소문자·숫자·하이픈 */
    anchor: string;
    /** 내비에 보이는 이름(구획의 `navLabel`, 없으면 제목) */
    label: string;
    /** 이 구획에서 저장하지 않은 변경의 칸 수 — 0 이거나 없으면 표시 없음 */
    changes?: number;
    /** 이 구획의 입력 오류 칸 수 — 0 이거나 없으면 표시 없음 */
    invalid?: number;
};

/** 구획 하나가 내비에 알리는 상태 */
export type SectionNavStatus = { changes: number; invalid: number };

/** 현재 구획을 가를 때 쓰는 구획 하나의 위치 — `top` 은 스크롤 영역 위쪽 끝에서 구획 위쪽 끝까지의 거리(px) */
export type SectionNavPosition = { anchor: string; top: number };

export type ActiveAnchorInput = {
    /** 화면 위에서 아래 순서의 구획들 */
    sections: readonly SectionNavPosition[];
    /** 감시 선(px) — 이 선을 지난 마지막 구획이 현재 구획이다 */
    line: number;
    /** 스크롤 영역 맨 위에 있다 */
    atTop: boolean;
    /** 스크롤 영역 맨 끝에 닿았다 */
    atBottom: boolean;
    /** 항목을 누른 직후 이동이 끝날 때까지 잠근 구획(없으면 null) */
    locked: string | null;
};

/**
 * 우측 구역 내비(앵커 메뉴)의 순수 규칙 — 노출 조건·항목 정리·현재 구획 판정·이동 상수. community 관리자 앱의 `UtilAdminSectionNav` 와 같은 규칙이다.
 * 상태·DOM 은 `useSectionNav` 가 맡고, 여기는 "무엇이 항목이 되는가 · 어느 것이 현재인가" 만 정한다 — 그래서 브라우저 없이 시험한다.
 * community 와 다른 점: 스크롤하는 것이 문서가 아니라 본문 영역(`.app-content`)이라 위치는 그 영역 위쪽 끝을 기준으로 잰다.
 */
export default class UtilSectionNav {
    /** 이 수 이상의 구획이 등록돼야 내비를 보인다 — 구획 1개 화면은 내비를 숨긴다 */
    static readonly MIN_SECTIONS = 2;

    /** 감시 선이 구획 위쪽 여백보다 내려가는 거리(px) — 이동한 구획의 위쪽 끝이 이 선 위에 오도록 */
    static readonly SPY_LINE_GAP_PX = 8;

    /** 항목을 누른 뒤 구획 표시를 잠그는 최대 시간(ms) — 스크롤이 일어나지 않아도 이때 풀린다. 스크롤 중이면 멈춘 뒤 `SETTLE_MS` 에 풀린다 */
    static readonly LOCK_MS = 700;

    /** 스크롤이 멈췄다고 보는 시간(ms) */
    static readonly SETTLE_MS = 140;

    /** 이동 뒤 구획 테두리 강조가 켜져 있는 시간(ms) */
    static readonly FLASH_MS = 1200;

    /** 주소로 쓸 수 있는 글자 — 소문자·숫자·하이픈(처음·끝·연속 하이픈 없음) */
    static readonly ANCHOR_PATTERN = /^[a-z0-9]+(?:-[a-z0-9]+)*$/;

    /**
     * 구획 주소로 쓸 수 있는 글자인가.
     * @param {unknown} value - 검사할 값.
     * @returns {boolean} 소문자·숫자·하이픈 조합이면 true.
     */
    static isValidAnchor(value: unknown): value is string {
        return typeof value === 'string' && UtilSectionNav.ANCHOR_PATTERN.test(value);
    }

    /**
     * 등록된 항목을 정리한다 — 주소 형식이 아니거나 이름이 빈 것을 빼고, 같은 주소는 먼저 등록된 것만 남긴다(화면 안에서 주소는 유일해야 하므로).
     * @param {readonly SectionNavEntry[]} entries - 등록된 항목.
     * @returns {SectionNavEntry[]} 정리한 항목.
     */
    static normalize(entries: readonly SectionNavEntry[]): SectionNavEntry[] {
        const seen = new Set<string>();
        const out: SectionNavEntry[] = [];
        for (const entry of entries) {
            if (!UtilSectionNav.isValidAnchor(entry.anchor) || entry.label.trim() === '' || seen.has(entry.anchor)) continue;
            seen.add(entry.anchor);
            out.push(entry);
        }
        return out;
    }

    /**
     * 칸 수로 쓸 수 있는 값으로 고친다 — 음수·소수·숫자가 아닌 것은 0 이다.
     * @param {unknown} value - 바뀐·오류 칸 수 후보.
     * @returns {number} 0 이상의 정수.
     */
    static count(value: unknown): number {
        return typeof value === 'number' && Number.isFinite(value) && value > 0 ? Math.floor(value) : 0;
    }

    /**
     * 항목을 화면 위에서 아래 순서로 놓는다 — 등록은 구획이 그려진 순서(마운트 순서)라서 실제 위치로 다시 줄 세운다. 위치를 모르는 항목은 알려진 것들 뒤에 등록 순서대로 둔다.
     * @param {readonly SectionNavEntry[]} entries - 등록된 항목.
     * @param {Readonly<Record<string, number>>} tops - 주소 → 위치(px).
     * @returns {SectionNavEntry[]} 줄 세운 항목(입력은 바꾸지 않는다).
     */
    static order(entries: readonly SectionNavEntry[], tops: Readonly<Record<string, number>>): SectionNavEntry[] {
        const known = entries.filter((entry) => typeof tops[entry.anchor] === 'number');
        const unknown = entries.filter((entry) => typeof tops[entry.anchor] !== 'number');
        known.sort((a, b) => (tops[a.anchor] as number) - (tops[b.anchor] as number));
        return [...known, ...unknown];
    }

    /**
     * 지금 화면에서 현재 구획이 어느 것인가(스크롤 스파이).
     * 규칙: ① 잠금이 걸렸으면 그 구획 ② 맨 위면 첫 구획 ③ 맨 끝에 닿았으면 마지막 구획(짧은 끝 구획이 맨 위까지 못 올라오므로) ④ 아니면 감시 선을 지난 마지막 구획 ⑤ 하나도 안 지났으면 첫 구획.
     * @param {ActiveAnchorInput} input - 구획 위치·감시 선·위/아래 끝·잠금.
     * @returns {string | null} 현재 구획의 주소(구획이 없으면 null).
     */
    static activeAnchor(input: ActiveAnchorInput): string | null {
        const { sections, line, atTop, atBottom, locked } = input;
        if (sections.length === 0) return null;
        if (locked !== null && sections.some((section) => section.anchor === locked)) return locked;
        const first = sections[0] as SectionNavPosition;
        const last = sections[sections.length - 1] as SectionNavPosition;
        if (atTop) return first.anchor;
        if (atBottom) return last.anchor;
        let current = first.anchor;
        for (const section of sections) {
            if (section.top <= line) current = section.anchor;
            else break;
        }
        return current;
    }
}
