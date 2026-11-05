package com.community.emoticon.controller;

import com.community.emoticon.dto.request.EmoticonReviewDecisionRequest;
import com.community.emoticon.dto.response.EmoticonPurchaseView;
import com.community.emoticon.service.EmoticonSalesAdminService;
import com.community.permission.annotation.RequirePermission;
import com.community.permission.dto.AdminActor;
import com.community.permission.entity.Permission;
import com.community.zconfiguration.response.ApiResponse;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 관리자 로보티콘 판매 내역·예외 환불(emoticon-contract S3 — admin-web {@code useEmoticonAdminApi} 의 {@code sales}·{@code refundSale}). 권한 {@code emoticon.sales} 하나,
 * 구매 회원·작가가 실려 {@code no-store}.
 * <ul>
 *   <li>{@code GET /api/admin/emoticons/sales?from&to&packId&status&page&size} → {@code { items, total, paidAmount }}</li>
 *   <li>{@code POST /api/admin/emoticons/sales/{id}/refund { reason }} → {@code { sale }} — 결제 취소(전액)를 넣고 보유를 뺀다, 감사 {@code EMOTICON_SALE_REFUND}</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/admin/emoticons/sales")
public class EmoticonSalesAdminController {

    private final EmoticonSalesAdminService sales;

    public EmoticonSalesAdminController(EmoticonSalesAdminService sales) {
        this.sales = sales;
    }

    @RequirePermission(Permission.EMOTICON_SALES)
    @GetMapping
    public ResponseEntity<ApiResponse<EmoticonPurchaseView.SalePage>> list(@RequestParam(required = false) String from, @RequestParam(required = false) String to,
                                                                          @RequestParam(required = false) String packId, @RequestParam(required = false) String status,
                                                                          @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {
        return noStore(sales.list(from, to, packId, status, page, size));
    }

    @RequirePermission(Permission.EMOTICON_SALES)
    @PostMapping("/{id}/refund")
    public ResponseEntity<ApiResponse<EmoticonPurchaseView.SaleRefundResult>> refund(@PathVariable String id, @RequestBody(required = false) EmoticonReviewDecisionRequest body,
                                                                                    AdminActor actor) {
        return noStore(sales.refund(actor.operatorId(), actor.ip(), id, body == null ? null : body.reason()));
    }

    private static <T> ResponseEntity<ApiResponse<T>> noStore(T body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(body));
    }
}
