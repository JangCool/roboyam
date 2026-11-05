package com.community.emoticon.dto.response;

import java.time.Instant;
import java.util.List;

/**
 * 로보티콘 스튜디오(작가) API 의 응답 모양 — user-web {@code useEmoticonStudioApi.types.ts} 와 같은 이름·칸. 판매·정산 칸(S3·S4)은 0·null 이다.
 */
public final class EmoticonStudioView {

    private EmoticonStudioView() {
    }

    /** 올린 그림 하나 — {@code POST /api/me/emoticon-studio/images} 의 응답 */
    public record Image(String imageId, String url, String format, int width, int height, int bytes, int frames) {
    }

    /** 팩 안 로보티콘 한 개(편집 모양) */
    public record Emoticon(String imageId, String url, String format, int width, int height, int bytes, int frames, String label) {
    }

    /** 상점에 나온 팩의 새 판 심사 — 대기 · 반려 */
    public record Revision(String status, Instant submittedAt, String rejectReason) {
    }

    /** 운영자의 마지막 가격 변경(S2 — 유료↔무료 포함) — 새 판 승인으로 바뀐 가격은 {@code reason} null */
    public record PriceChange(int from, int to, String reason, Instant changedAt) {
    }

    /** 상품화 계약 동의 한 건(추가만) */
    public record Agreement(int version, Instant agreedAt) {
    }

    /**
     * {@code GET /api/me/emoticon-studio/contract} — 지금 판(약관 {@code ROBOTIKON_CONTRACT} 의 시행 중 게시본) · 제목(본문 첫 제목, 없으면 기본 문구) · 요약(본문 첫 목록의 항목) ·
     * 전문(평문 — 서버가 HTML 을 글자로 바꾼다) · 내 동의(오래된 것 먼저)
     */
    public record Contract(int version, String effectiveDate, String title, List<String> summary, String body, List<Agreement> agreements) {
    }

    /** {@code GET·PUT /api/me/emoticon-studio/payout-account} — 가린 값만. 등록 전이면 {@code registered:false} 이고 나머지는 null(인증 이름만 있을 수 있다) */
    public record PayoutAccount(boolean registered, String type, String businessNumberMasked, String bankCode, String accountMasked, String holderMasked,
                                String identityNameMasked, Instant updatedAt) {
    }

    /** 내 팩 한 개(목록 줄) */
    public record PackRow(String id, String name, String coverUrl, String status, String rejectReason, String suspendReason, Revision revision, String saleWish,
                          int price, int count, int acquireCount, long rewardTotal, long salesTotal, PriceChange lastPriceChange, Instant updatedAt) {
    }

    /** {@code GET /api/me/emoticon-studio/packs/{id}} — 편집 화면. 임시 판이 있으면 그 판, 없으면 마지막으로 낸 판의 값 */
    public record Pack(String id, String name, String coverUrl, String status, String rejectReason, String suspendReason, Revision revision, String saleWish, int price,
                       int count, int acquireCount, long rewardTotal, long salesTotal, PriceChange lastPriceChange, Instant updatedAt, String description,
                       List<String> tags, Image cover, List<Emoticon> emoticons, String priceRequestMemo) {
    }

    /** 최근 보상 한 줄(원장 사유 {@code EMOTICON_CREATOR_REWARD}) */
    public record Reward(String id, String packId, String packName, int points, Instant createdAt) {
    }

    /** 이번 달 판매 요약(유료 팩 — S4). S1 은 모두 0 */
    public record Sales(String month, int salesCount, long salesAmount, long refundAmount, long vatAmount, long baseAmount, long feeAmount, long expectedPayout) {
    }

    /** {@code GET /api/me/emoticon-studio} */
    public record Overview(List<PackRow> packs, int acquireTotal, long rewardTotal, long rewardToday, List<Reward> recentRewards, int pendingCount, Sales sales) {
    }
}
