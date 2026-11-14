package com.community.ad.service;

import com.community.ad.repository.master.AdPlacementStore;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * 구글 애드센스의 사이트 쪽 표시(docs/features/adsense-contract.md 「단계별 동작」) — 공개 {@code index.html} 머리 스크립트·메타({@code SitePageHandler})와
 * {@code /ads.txt}({@code AdsTxtController}). VERIFY·SHOW 이고 게시자 ID 가 있을 때만 낸다.
 *
 * <p>설정은 사본 없이 요청마다 한 행(id = 1)의 애드센스 칸만 읽는다 — 관리자 서버가 저장하면 다음 요청부터 따라온다(공개 자리 설정 {@code GET /api/ads/placements} 와 같다,
 * 캐시 관리 계약의 「캐시하지 않는 것 — 광고」).
 */
@Service
public class AdSenseSiteService {

    private final AdPlacementStore store;

    public AdSenseSiteService(AdPlacementStore store) {
        this.store = store;
    }

    /** 머리에 넣을 게시자 ID({@code ca-pub-…}) — 끔이거나 게시자 ID 가 없으면 비어 있다 */
    public Optional<String> activeClient() {
        return store.adsense().activeClient();
    }

    /** {@code /ads.txt} 본문(한 줄 + 줄바꿈) — 끔이거나 게시자 ID 가 없으면 비어 있다 */
    public Optional<String> adsTxt() {
        return store.adsense().adsTxt();
    }
}
