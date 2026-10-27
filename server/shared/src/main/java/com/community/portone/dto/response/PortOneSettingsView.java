package com.community.portone.dto.response;

import com.community.identity.dto.IdentityProviderKind;
import com.community.payment.gateway.PaymentGatewayKind;
import java.util.List;

/**
 * 포트원 설정 화면 — {@code GET /api/admin/portone-settings}(계약 「0-4」 + 2a 결정 14). 값은 싣지 않는다.
 *
 * @param identityProvider    지금 본인인증 구현(배포 설정 {@code roboyam.identity.provider} — 화면에서 바꾸지 않는다)
 * @param paymentGateway      지금 결제 게이트웨이(배포 설정 {@code roboyam.payment.gateway} — 화면에서 바꾸지 않는다)
 * @param cryptoKeyConfigured 암호화 키({@code ROBOYAM_CRYPTO_KEY})가 있는가 — false 면 저장할 수 없다(지우기는 된다)
 * @param identityReady       본인인증 칸 셋(상점 ID·API 비밀키·본인인증 채널 키)이 모두 설정됐는가
 * @param paymentReady        결제 4칸(상점 ID·API 비밀키·결제 채널 키·웹훅 비밀키)과 암호화 키가 모두 있는가
 * @param fields              다섯 칸의 상태 — 공통 → 본인인증 → 결제 차례({@code PortOneSettingKey} 선언 차례)
 */
public record PortOneSettingsView(IdentityProviderKind identityProvider, PaymentGatewayKind paymentGateway, boolean cryptoKeyConfigured, boolean identityReady,
                                  boolean paymentReady, List<PortOneFieldStateView> fields) {
}
