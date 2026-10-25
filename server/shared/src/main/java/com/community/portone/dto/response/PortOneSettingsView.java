package com.community.portone.dto.response;

import com.community.identity.dto.IdentityProviderKind;
import java.util.List;

/**
 * 포트원 설정 화면 — {@code GET /api/admin/portone-settings}(계약 「0-4」). 값은 싣지 않는다.
 *
 * @param identityProvider    지금 본인인증 구현(배포 설정 {@code roboyam.identity.provider} — 화면에서 바꾸지 않는다)
 * @param cryptoKeyConfigured 암호화 키({@code ROBOYAM_CRYPTO_KEY})가 있는가 — false 면 저장할 수 없다(지우기는 된다)
 * @param identityReady       본인인증 칸 셋이 모두 설정됐는가
 */
public record PortOneSettingsView(IdentityProviderKind identityProvider, boolean cryptoKeyConfigured, boolean identityReady, List<PortOneFieldStateView> fields) {
}
