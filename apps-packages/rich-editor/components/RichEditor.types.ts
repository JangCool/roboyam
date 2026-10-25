/**
 * `RichEditor` 의 계약 타입 — 쓰는 앱(글쓰기·댓글·나중에 관리자 메일 템플릿)이 명시적으로 import 한다(`#layers/rich-editor/components/RichEditor.types`).
 */

/**
 * 에디터 수준 — 게시판 설정의 `postEditor`·`commentEditor` 유효값 중 에디터를 쓰는 값(`NONE` 은 에디터 없이 Textarea 다).
 *  - `SIMPLE`: 굵게·기울임·밑줄·취소선·링크·글머리 목록·번호 목록·인용(태그 `p br strong em u s a ul ol li blockquote`)
 *  - `ADVANCED`(글만): SIMPLE + 제목(h2·h3)·구분선·인라인 코드·코드 블록·글자색/배경색(팔레트)·이미지·유튜브·블록 끌기 손잡이
 *  - `DOCUMENT`(관리자 문서 — 약관): SIMPLE + 제목(h2·h3)·구분선(태그 `+ h2 h3 hr`). 게시판 설정 값이 아니다
 */
export type RichEditorLevel = 'SIMPLE' | 'ADVANCED' | 'DOCUMENT';

/** 이미지 올리기 결과 — 서버 `POST /api/images` 의 `201` 응답(`{id, url, width, height}`) 모양 */
export interface RichImageUploadResult {
    id: string;
    url: string;
    width: number;
    height: number;
}

/** 올리기 진행·취소 */
export interface RichImageUploadOptions {
    signal: AbortSignal;
    /** 0~1. 본문을 다 보냈을 때 1 */
    onProgress: (fraction: number) => void;
}

/**
 * 이미지 한 장을 올리는 함수 — 레이어는 서버를 부르지 않으므로 쓰는 앱이 자기 API 컴포저블로 만들어 넘긴다.
 * 실패는 앱의 `ApiError`(`status`·`code`·`message`)를 그대로 던진다 — 문구는 서버 문구를 쓴다.
 */
export type RichImageUploader = (file: File, options: RichImageUploadOptions) => Promise<RichImageUploadResult>;

/** 에디터의 첨부 상태 — 부모가 「올리는 중·실패한 이미지가 있으면 저장 막기」에 쓴다 */
export interface RichEditorMediaState {
    /** 본문에 들어 있는 이미지 수 */
    images: number;
    /** 본문에 들어 있는 유튜브 수 */
    videos: number;
    /** 올리는 중(대기 포함)인 이미지 수 */
    uploading: number;
    /** 올리지 못하고 남아 있는 이미지 수 */
    failed: number;
}
