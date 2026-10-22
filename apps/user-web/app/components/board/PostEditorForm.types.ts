/** 글쓰기·고치기 폼의 값 — `PostEditorForm` 의 `v-model`. 비밀번호는 이 값을 든 화면의 메모리에만 있다(저장소·주소에 남기지 않는다) */
export interface PostFormValues {
    prefixId: string | null;
    title: string;
    content: string;
    adult: boolean;
    /** 비회원 글쓰기에서만 쓴다 */
    guestName: string;
    guestPassword: string;
    /** 자동입력 방지(목업 체크) */
    captcha: boolean;
}

/** 게시판 고르기 목록의 한 항목 */
export interface PostBoardOption {
    slug: string;
    label: string;
}

/** 빈 폼 — 쓰는 곳은 `{ ...EMPTY_POST_FORM }` 로 복사해 쓴다 */
export const EMPTY_POST_FORM: Readonly<PostFormValues> = { prefixId: null, title: '', content: '', adult: false, guestName: '', guestPassword: '', captcha: false };
