package com.community.social.dto.response;

import java.util.List;

/**
 * 소셜 로그인 공급자 설정 목록({@code GET /api/admin/social-providers}).
 *
 * @param cryptoKeyConfigured false 면 서버에 암호화 키({@code ROBOYAM_CRYPTO_KEY})가 없어 저장할 수 없다({@code PUT} 이 {@code 503 CRYPTO_KEY_MISSING})
 * @param publicUrlConfigured false 면 사이트 공개 주소가 없어 콜백 주소를 만들 수 없고 켜기를 거절한다({@code fields.enabled})
 * @param providers           늘 3개, 순서 고정 카카오 · 네이버 · 구글
 */
public record SocialProviderSettingsView(boolean cryptoKeyConfigured, boolean publicUrlConfigured, List<SocialProviderSettingView> providers) {
}
