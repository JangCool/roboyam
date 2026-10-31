/**
 * 사업자 화면 갈래 — 인증 심사(`/business-reviews`: 심사 중·재심사, 국세청 다시 조회·승인·반려) · 사업자 목록(`/businesses`: 승인·정지·탈퇴·반려·전체, 정지·정지 해제).
 * 두 화면이 같은 서랍·대화상자를 쓰고 탭 집합·기본 정렬·서랍 아래 단추만 이 값으로 가른다(04-ia-review 5절).
 */
export type BusinessScreenMode = 'review' | 'directory';
