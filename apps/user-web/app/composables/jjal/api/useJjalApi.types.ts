/**
 * 내 짤 API 의 모양 — 계약 `docs/features/my-jjal-contract.md` 「API 후보」(목업 단계 — 서버가 붙으면 서버 DTO 이름에 맞춘다).
 * ID 는 짤 공개 ID(토큰 `:jjal-{ID}:` 의 ID)다. 보관함 줄은 (회원, 파일)이 유일하므로 짤 ID 로 가리킨다.
 */

/** 보관함 줄의 출처 — 내가 올림 · 글·댓글에서 담음 */
export type JjalSource = 'UPLOAD' | 'COLLECT';

/** 내 짤 하나 — `GET /api/me/jjals` 의 `items[]`. 배열 순서 = 회원이 정한 순서(서버가 그 차례로 준다 — 가정, 새로 넣은 짤은 맨 앞) */
export interface MyJjalItem {
    id: string;
    url: string;
    /** 저장본 가로·세로(px, 긴 변 300 이하) */
    width: number;
    height: number;
    /** 움짤(GIF 여러 장) */
    animated: boolean;
    source: JjalSource;
    /** 신고로 내려간 짤 — 고르기 창에서 빼고, 내 짤 칸에서는 빨간 칸 */
    blocked: boolean;
}

/** `GET /api/me/jjals` */
export interface MyJjalListResponse {
    items: MyJjalItem[];
    /** 보관함 개수(내려간 짤 포함 — 상한에 센다, 가정) */
    count: number;
    /** 상한(지금 50) */
    limit: number;
    /** 처음 안내를 확인했는가(계정에 기록 — PC·휴대폰에서 두 번 나오지 않게) */
    introAcknowledged: boolean;
}

/** 올리기·담기 결과 — 넣은 줄과 바뀐 개수(토스트 「내 짤에 담았어요 13 / 50」) */
export interface JjalSaveResult {
    item: MyJjalItem;
    count: number;
    limit: number;
}

/** `PUT /api/me/jjals/order` — 보관함 전체 ID 를 새 순서로 한 번에(내려간 짤 포함). 보관함과 맞지 않으면 `409` */
export interface JjalOrderRequest {
    ids: string[];
}

/** `POST /api/me/jjals/collect` */
export interface JjalCollectRequest {
    jjalId: string;
}

/** 올리기 — 브라우저가 줄여 만든 파일(GIF·PNG·JPEG)과 그 크기. 서버는 파일을 다시 검사한다(크기·형식·프레임) */
export interface JjalUploadRequest {
    file: Blob;
    /** 파일 이름(확장자 — 서버는 이름을 믿지 않고 첫 바이트로 본다) */
    filename: string;
}

/**
 * 글·댓글 속 짤 하나의 표시 정보 — 계약 「공개: 글·댓글 응답에 짤 토큰 → {id, url, width, height, animated, blocked, collectable}」.
 * 목업은 따로 묻는다(`GET /api/jjals?ids=` — 가정). 응답에 실을지 따로 받을지는 구현 때 정한다.
 */
export interface JjalView {
    id: string;
    url: string;
    width: number;
    height: number;
    animated: boolean;
    /** 신고로 내려간 짤 — 「내려간 짤」 자리 */
    blocked: boolean;
    /** 「내 짤에 담기」를 보일까 — 19금 게시판 글·댓글·내려간 짤은 false(서버 판정. 화면은 19금 게시판이면 한 번 더 끈다) */
    collectable: boolean;
}

/** `GET /api/jjals?ids=` — 목업 가정 */
export interface JjalLookupResponse {
    items: JjalView[];
}
