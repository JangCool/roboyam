package com.community.site.dto;

/**
 * 화면 주소를 글 상세로 보고 한 번 조회한 결과 — {@code PostLinkPreviewService#forPath} 가 만든다(미리보기와 「없는 글」 판정이 같은 조회를 쓴다).
 *
 * @param gone    글 주소인데 글이 없거나 지워졌다(지움·예약 삭제 시각 지남·임시저장) — 화면은 그대로, 상태만 404 로 내린다(docs/features/sitemap-contract.md)
 * @param preview 비회원이 읽을 수 있는 글의 미리보기 칸 — 글 주소가 아니거나 비회원이 읽을 수 없거나 없는 글이면 null
 */
public record PostLinkResult(boolean gone, PostLinkPreview preview) {

    /** 해당 없음 — 글 주소가 아니거나 비회원이 읽을 수 없는 글(있는지 없는지를 상태로 알리지 않는다) */
    public static final PostLinkResult NONE = new PostLinkResult(false, null);
    /** 없는 글 */
    public static final PostLinkResult GONE = new PostLinkResult(true, null);

    public static PostLinkResult of(PostLinkPreview preview) {
        return new PostLinkResult(false, preview);
    }
}
