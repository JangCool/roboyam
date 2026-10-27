package com.community.portone.controller;

import com.community.permission.annotation.RequirePermission;
import com.community.permission.dto.AdminActor;
import com.community.permission.entity.Permission;
import com.community.portone.dto.PortOneSettingKey;
import com.community.portone.dto.request.PortOneCommonSaveRequest;
import com.community.portone.dto.request.PortOneIdentitySaveRequest;
import com.community.portone.dto.request.PortOnePaymentSaveRequest;
import com.community.portone.dto.response.PortOneSettingsView;
import com.community.portone.exception.PortOneFieldNotFoundException;
import com.community.portone.service.PortOneSettingsService;
import com.community.zconfiguration.response.ApiResponse;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 포트원 설정 API(설정 › 시스템 › 포트원 — 계약 「0-4」, 세션 정리 결정 9, 2a 결정 14) — 조회 {@code site.read}, 저장·지우기 {@code site.secret}. 값(상점 ID·API 비밀키·채널 키·
 * 웹훅 비밀키)은 어떤 응답에도 없다 — 설정 여부·바꾼 시각·바꾼 사람만. 지금 본인인증 구현({@code identityProvider})·결제 게이트웨이({@code paymentGateway})는 배포 설정이다
 * (화면에서 바꾸지 않는다). 저장·지우기는 카드 단위 — 공통(상점 ID·API 비밀키) · 본인인증(본인인증 채널 키) · 결제(결제 채널 키·웹훅 비밀키).
 * 소셜 로그인 설정처럼 응답은 {@code Cache-Control: no-store}. 입력 검사는 서비스가 한다.
 */
@RestController
@RequestMapping("/api/admin/portone-settings")
public class PortOneSettingsController {

    private final PortOneSettingsService service;

    public PortOneSettingsController(PortOneSettingsService service) {
        this.service = service;
    }

    @RequirePermission(Permission.SITE_READ)
    @GetMapping
    public ResponseEntity<ApiResponse<PortOneSettingsView>> view() {
        return noStore(service.view());
    }

    /** 공통 칸(보낸 칸만) — 400 {@code VALIDATION} · 503 {@code CRYPTO_KEY_MISSING} */
    @RequirePermission(Permission.SITE_SECRET)
    @PutMapping("/common")
    public ResponseEntity<ApiResponse<PortOneSettingsView>> saveCommon(@RequestBody(required = false) PortOneCommonSaveRequest body, AdminActor actor) {
        PortOneCommonSaveRequest b = body == null ? new PortOneCommonSaveRequest(null, null) : body;
        return noStore(service.save(PortOneSettingKey.Group.COMMON, sent(PortOneSettingKey.STORE_ID, b.storeId(), PortOneSettingKey.API_SECRET, b.apiSecret()),
                actor.operatorId(), actor.ip()));
    }

    /** 본인인증 칸 — 400 {@code VALIDATION} · 503 {@code CRYPTO_KEY_MISSING} */
    @RequirePermission(Permission.SITE_SECRET)
    @PutMapping("/identity")
    public ResponseEntity<ApiResponse<PortOneSettingsView>> saveIdentity(@RequestBody(required = false) PortOneIdentitySaveRequest body, AdminActor actor) {
        String channelKey = body == null ? null : body.identityChannelKey();
        return noStore(service.save(PortOneSettingKey.Group.IDENTITY, sent(PortOneSettingKey.IDENTITY_CHANNEL_KEY, channelKey, null, null), actor.operatorId(), actor.ip()));
    }

    /** 결제 칸(보낸 칸만) — 400 {@code VALIDATION}(웹훅 비밀키는 {@code whsec_} 형식) · 503 {@code CRYPTO_KEY_MISSING} */
    @RequirePermission(Permission.SITE_SECRET)
    @PutMapping("/payment")
    public ResponseEntity<ApiResponse<PortOneSettingsView>> savePayment(@RequestBody(required = false) PortOnePaymentSaveRequest body, AdminActor actor) {
        PortOnePaymentSaveRequest b = body == null ? new PortOnePaymentSaveRequest(null, null) : body;
        return noStore(service.save(PortOneSettingKey.Group.PAYMENT, sent(PortOneSettingKey.PAYMENT_CHANNEL_KEY, b.paymentChannelKey(), PortOneSettingKey.WEBHOOK_SECRET,
                b.webhookSecret()), actor.operatorId(), actor.ip()));
    }

    /** 칸 하나 지우기 — 204 · 404 {@code PORTONE_FIELD_NOT_FOUND}(모르는 카드·그 카드의 칸이 아님·설정돼 있지 않은 칸) */
    @RequirePermission(Permission.SITE_SECRET)
    @DeleteMapping("/{group}/{key}")
    public ResponseEntity<Void> deleteField(@PathVariable String group, @PathVariable String key, AdminActor actor) {
        PortOneSettingKey.Group card = Arrays.stream(PortOneSettingKey.Group.values()).filter(g -> PortOneSettingsService.groupName(g).equals(group)).findFirst()
                .orElseThrow(PortOneFieldNotFoundException::new);
        service.deleteField(card, key, actor.operatorId(), actor.ip());
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    /** 보낸 칸만(null 은 뺀다) */
    private static Map<PortOneSettingKey, String> sent(PortOneSettingKey first, String firstValue, PortOneSettingKey second, String secondValue) {
        Map<PortOneSettingKey, String> sent = new HashMap<>();
        if (firstValue != null) {
            sent.put(first, firstValue);
        }
        if (second != null && secondValue != null) {
            sent.put(second, secondValue);
        }
        return sent;
    }

    private static <T> ResponseEntity<ApiResponse<T>> noStore(T body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(body));
    }
}
