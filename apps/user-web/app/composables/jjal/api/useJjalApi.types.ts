/**
 * 내 짤 API 의 모양 — 계약 `docs/features/my-jjal-contract.md` 「화면 확정 결과」·「서버 구현 결과」. 서버 DTO(server/api `com.community.jjal.dto`)와 같은 모양이다.
 * ID 는 짤 공개 ID(토큰 `:jjal-{ID}:` 의 ID)다. 보관함 줄은 (회원, 파일)이 유일하므로 짤 ID 로 가리킨다.
 */

/** 보관함 줄의 출처 — 내가 올림 · 글·댓글에서 담음 */
export type JjalSource = 'UPLOAD' | 'COLLECT';

/** 내 짤 하나 — `GET /api/me/jjals` 의 `items[]`(서버 `MyJjalItemView`). 배열 순서 = 회원이 정한 순서(새로 넣은 짤은 맨 앞) */
export interface MyJjalItem {
    id: string;
    /** 그림 주소 — 내려간 짤(공개 위치로 옮기기 전 포함)은 null(「내려간 짤」 자리) */
    url: string | null;
    /** 저장본 가로·세로(px, 긴 변 300 이하) */
    width: number;
    height: number;
    /** 움짤(GIF 여러 장) */
    animated: boolean;
    source: JjalSource;
    /** 신고로 내려간 짤 — 고르기 창에서 빼고, 내 짤 칸에서는 빨간 칸 */
    blocked: boolean;
}

/** `GET /api/me/jjals`(서버 `MyJjalListView`) */
export interface MyJjalListResponse {
    items: MyJjalItem[];
    /** 보관함 개수(내려간 짤 포함 — 상한에 센다) */
    count: number;
    /** 상한(지금 50) */
    limit: number;
    /** 처음 안내를 확인했는가(계정에 기록 — PC·휴대폰에서 두 번 나오지 않게) */
    introAcknowledged: boolean;
}

/** 올리기·담기 결과(`201`, 서버 `JjalSaveResultView`) — 넣은 줄과 바뀐 개수(토스트 「내 짤에 담았어요 13 / 50」) */
export interface JjalSaveResult {
    item: MyJjalItem;
    count: number;
    limit: number;
}

/** `PUT /api/me/jjals/order` — 보관함 전체 ID 를 새 순서로 한 번에(내려간 짤 포함). 보관함과 맞지 않으면 `409 JJAL_ORDER_CONFLICT` */
export interface JjalOrderRequest {
    ids: string[];
}

/**
 * `POST /api/me/jjals/collect`(서버 `JjalCollectRequest`) — 서버가 「보이는 글·댓글에 쓰인 짤인가」·「19금 게시판·19금 글이 아닌가」를 이 글·댓글로 판정한다.
 * 안 보이는 글·그 글에 안 쓰인 짤 `404 JJAL_NOT_FOUND` · 19금 `403 JJAL_NOT_COLLECTABLE` · 칸 오류 `400 fields.jjalId|postId|commentId`.
 */
export interface JjalCollectRequest {
    jjalId: string;
    /** 그 짤이 보이는 글(댓글의 짤이면 그 댓글의 글) */
    postId: string;
    /** 댓글의 짤이면 그 댓글(글 본문의 짤이면 빠짐) */
    commentId?: string;
}

/** 올리기 — 브라우저가 줄여 만든 파일(GIF·PNG·JPEG)과 그 크기. 서버는 파일을 다시 검사한다(크기·형식·프레임) */
export interface JjalUploadRequest {
    file: Blob;
    /** 파일 이름(확장자 — 서버는 이름을 믿지 않고 첫 바이트로 본다) */
    filename: string;
}

/** 글·댓글 속 짤 하나의 표시 정보(서버 `JjalView`) — `GET /api/jjals?ids=&postId=` 로 따로 받는다 */
export interface JjalView {
    id: string;
    /** 그림 주소 — 내려간 짤은 null(「내려간 짤」 자리) */
    url: string | null;
    width: number;
    height: number;
    animated: boolean;
    /** 신고로 내려간 짤 — 「내려간 짤」 자리 */
    blocked: boolean;
    /**
     * 「내 짤에 담기」를 보일까(서버 판정) — 내려간 짤은 false. 맥락 글(`postId`)을 주면 그 글이 보이고 19금 게시판·19금 글이 아니고 그 글(본문·댓글)에 쓰인 짤일 때만 true,
     * 주지 않으면 내려가지 않은 짤은 true(맥락을 모른다). 화면은 맥락 글을 주고 받은 값만 담기에 쓴다(`useJjalCatalog.collectableAt`).
     */
    collectable: boolean;
}

/** `GET /api/jjals?ids=a,b&postId=`(서버 `JjalLookupView`) — 모르는 ID 는 빠진다. 한 번에 100개까지(넘으면 `400 fields.ids`) */
export interface JjalLookupResponse {
    items: JjalView[];
}
