/** 투표 칸의 값 — `PollSettingsFields` 의 `v-model`. 글쓰기·고치기가 같이 쓴다(판정·변환은 `UtilPoll`) */
export interface PollFormValues {
    /** 「투표 붙이기」 */
    enabled: boolean;
    question: string;
    /** 선택지 칸(빈 칸 포함) — 보낼 때 빈 칸을 뺀다 */
    options: string[];
    /** 「복수 선택」 */
    multiple: boolean;
    /** 마감 순간(ISO) — 비면 마감 없음(`AppDateTimePicker` 의 값) */
    closesAt: string;
    /** 「다시 투표 허용」(기본 끔) */
    changeAllowed: boolean;
}

/** 투표 칸별 오류 — 서버 `fields."poll.*"`·`fields.poll` 또는 화면 검사 문구. 빈 글자 = 오류 없음 */
export interface PollFieldErrors {
    /** 칸에 붙지 않는 투표 오류(`fields.poll` — 게시판 옵션 꺼짐·비회원 등) */
    poll: string;
    question: string;
    options: string;
    closesAt: string;
}

/** 오류 없음. 새 투표의 처음 값은 `UtilPoll.emptyForm()`(선택지 배열을 나눠 쓰지 않게 늘 새로 만든다) */
export const EMPTY_POLL_FIELD_ERRORS: Readonly<PollFieldErrors> = { poll: '', question: '', options: '', closesAt: '' };
