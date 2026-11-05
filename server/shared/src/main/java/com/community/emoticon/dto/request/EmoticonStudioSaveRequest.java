package com.community.emoticon.dto.request;

import java.util.List;

/**
 * 작가 팩 임시 저장·심사 요청 본문 — user-web {@code EmoticonStudioSaveRequest}·{@code EmoticonStudioSubmitRequest}(심사 요청은 동의 셋 + 계약 판이 더해진다 — 임시 저장은 그 칸을 보지 않는다).
 * 검사 순서·칸 경로({@code emoticons[0].label})가 계약이라 Bean Validation 없이 서비스가 모은다. 금액은 받지 않는다(운영자가 정한다).
 *
 * @param emoticons            로보티콘 차례 — 올린 그림 ID 와 이름표
 * @param saleWish             {@code FREE}·{@code PAID} — 판매 중·판매 중단 팩은 바꾸지 않는다(무시)
 * @param agreeContractVersion 유료 희망(또는 유료로 나온 팩의 새 판)일 때 동의한 상품화 계약 판 — 지금 판이어야 한다(이미 지금 판에 동의했으면 없어도 된다, S2)
 */
public record EmoticonStudioSaveRequest(String name, String description, List<String> tags, String coverImageId, List<Item> emoticons, String saleWish,
                                        String priceRequestMemo, Boolean agreeOwnership, Boolean agreeLicense, Boolean agreePolicy, Integer agreeContractVersion) {

    /** 로보티콘 한 개 */
    public record Item(String imageId, String label) {
    }
}
