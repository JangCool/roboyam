import type { RichImageUploader, RichImageUploadResult } from '#layers/rich-editor/components/RichEditor.types';
import type { RichUploadFailure } from '#layers/rich-editor/utils/util-rich-messages';

/** 올리기 한 장의 상태 — 대기 → 올리는 중(진행률) → 서버가 처리하는 중(본문은 다 보냈다) → 완료(목록에서 사라짐), 또는 실패 */
export type RichUploadStatus = 'queued' | 'uploading' | 'processing' | 'failed';

export interface RichUploadItem {
    id: string;
    name: string;
    bytes: number;
    isGif: boolean;
    status: RichUploadStatus;
    /** 0~1 — `uploading` 일 때만 뜻이 있다 */
    progress: number;
    failure: RichUploadFailure | null;
    /** 한꺼번에 고른 파일의 묶음 번호 — 같은 묶음은 고른 순서대로 본문에 들어간다 */
    batch: number;
}

export interface RichImageUploadsOptions {
    /** 올리는 함수(앱이 넘긴 것) — 없으면 올리지 않는다 */
    uploader: () => RichImageUploader | null;
    /** 한 장이 올라왔다 — 에디터에 넣는다 */
    onUploaded: (id: string, result: RichImageUploadResult) => void;
    /** 목록에서 빠졌다(취소·지우기·완료) */
    onGone?: (id: string) => void;
}
