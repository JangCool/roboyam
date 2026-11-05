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

    /**
     * 주문 — 머리글 {@code Idempotency-Key}, {@code expectedAmount} 는 비교용(결정 72). 배송지는 둘 중 하나(docs/features/address-book-contract.md):
     * <ul>
     *   <li>{@code addressId}(주소록의 글자 ID) + {@code address.memo} — 서버가 주소록에서 풀어 주문 배송지로 복사하고 메모만 요청 값을 쓴다({@code address} 의 다른 칸은 무시).
     *       없거나 남의 ID 는 400 {@code fields.addressId}.</li>
     *   <li>{@code address} 전체 + {@code saveAddress}(기본 false) — true 면 주문 생성 트랜잭션 안에서 주소록에 저장(10개면 저장만 건너뜀).</li>
     * </ul>
     */
    public record Order(List<Line> items, Address address, Agreements agreements, Long expectedAmount, String addressId, Boolean saveAddress) {

        public Order(List<Line> items, Address address, Agreements agreements, Long expectedAmount) {
            this(items, address, agreements, expectedAmount, null, null);
        }
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
