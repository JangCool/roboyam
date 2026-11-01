package com.community.hotdeal.dto.request;

/**
 * 글쓰기·고치기 본문의 {@code deal}(user-web {@code DealInput}) — 할인율·호스트·상태·제휴 강제는 서버가 정한다. 검사 순서·칸 경로({@code fields."deal.*"})가 계약이라
 * Bean Validation 없이 서비스({@code DealWriter})가 본다.
 *
 * @param endsAt 마감(UTC ISO) — 비우면 {@code autoExpireDays} 뒤 기간 종료
 */
public record DealInput(String storeName, String productUrl, Long normalPrice, Long dealPrice, Long shippingFee, String couponNote, String priceBasisKind,
                        Boolean dutyIncluded, String priceBasis, String endsAt, String stockState, Boolean affiliate, DealImageInput image) {
}