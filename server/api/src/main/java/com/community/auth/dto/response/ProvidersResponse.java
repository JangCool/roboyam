package com.community.auth.dto.response;

import java.util.List;

/** 쓸 수 있는 소셜 로그인 목록. 켜 둔 공급자가 없으면 빈 배열이다(화면이 소셜 버튼을 숨긴다) */
public record ProvidersResponse(List<ProviderView> providers) {
}
