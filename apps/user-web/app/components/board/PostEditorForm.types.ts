import type { ContentFormat } from '~/composables/board/api/usePostApi.types';

/** 글쓰기·고치기 폼의 값 — `PostEditorForm` 의 `v-model`. 비밀번호는 이 값을 든 화면의 메모리에만 있다(저장소·주소에 남기지 않는다) */
export interface PostFormValues {
    prefixId: string | null;
    title: string;
    /** 본문 — `contentFormat` 이 `TEXT` 면 평문, `HTML` 이면 에디터 HTML */
    content: string;
    /** 본문 형식 — 게시판 글 에디터 설정을 따른다(`UtilBoard.postFormat`). 게시판이 바뀌면 화면이 본문을 바꿔 맞춘다(`UtilPost.convertContent`) */
    contentFormat: ContentFormat;
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
export const EMPTY_POST_FORM: Readonly<PostFormValues> = { prefixId: null, title: '', content: '', contentFormat: 'TEXT', adult: false, guestName: '', guestPassword: '', captcha: false };
