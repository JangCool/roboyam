package com.community.sanction.dto.request;

import java.util.List;

/**
 * 반복 검토 닫기(admin-web {@code BoardSanctionReviewCloseRequest}) — {@code result} KEPT · SITE_SANCTIONED(+{@code siteSanctionId}) · VOIDED(+{@code voidSanctionIds}),
 * {@code memo} 0~200자(운영자끼리만). 검사는 서비스가 한다.
 */
public record BoardSanctionReviewCloseRequest(String result, String memo, String siteSanctionId, List<String> voidSanctionIds) {
}
