/**
 * 에디터 이미지 API 가 주고받는 모양 — `POST /api/images`(multipart `file`)의 `201` 응답. 계약: `docs/features/board-public-contract.md` 「에디터 화면」.
 * 서버가 같은 이름의 DTO 를 두면 그 이름을 따른다(서버는 동시에 만드는 중 — 2026-10-23).
 * 모양은 에디터 레이어의 `RichImageUploadResult`(`#layers/rich-editor/components/RichEditor.types`)와 같아 그대로 넘길 수 있다.
 */
export interface ImageUploadResult {
    /** 이미지 ID — 본문에 `<img data-image-id="ID">` 로 넣는다(서버가 주소를 다시 채운다). Snowflake 문자열일 수 있다 */
    id: string;
    /** 에디터 미리보기용 주소 */
    url: string;
    width: number;
    height: number;
}
