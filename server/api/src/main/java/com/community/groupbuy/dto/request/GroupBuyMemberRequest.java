package com.community.groupbuy.dto.request;

import java.util.List;

/**
 * 공구 회원 API 의 요청 본문(user-web {@code *.types.ts} 와 같은 모양). ID 는 글자로 받는다 — 숫자가 아니면 400 {@code fields.<칸>}.
 */
public final class GroupBuyMemberRequest {

    private GroupBuyMemberRequest() {
    }

    /** 단품 한 줄(견적·주문·본인 취소 — 본인 취소는 {@code lines} 로 받는다) */
    public record Line(String skuId, Integer qty) {
    }

    public record Quote(List<Line> items) {
    }

    public record Address(String recipient, String phone, String postcode, String address1, String address2, String memo) {
    }

    public record Agreements(Boolean purchaseTerms, Boolean refundPolicy, Boolean privacy) {
    }

    /** 주문 — 머리글 {@code Idempotency-Key}, {@code expectedAmount} 는 비교용(결정 72) */
    public record Order(List<Line> items, Address address, Agreements agreements, Long expectedAmount) {
    }

    public record Watch(String kind, String skuId) {
    }

    public record Cancel(List<Line> lines) {
    }

    /** 반품 줄(줄마다 사유 — 결정 44). 취소 요청이면 사유 없음 */
    public record RequestLine(String skuId, Integer qty, String reason) {
    }

    public record ReturnPreview(List<RequestLine> items) {
    }

    /** 취소·반품 요청 — 머리글 {@code Idempotency-Key} */
    public record CreateRequest(String type, List<RequestLine> items, String collectionMethod, String resolution, String reasonText, List<String> photoIds,
                                Long expectedAmount) {
    }

    public record SupplyChoice(String choice) {
    }
}
