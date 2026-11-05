package com.community.emoticon.controller;

import com.community.emoticon.dto.response.EmoticonPublicView;
import com.community.emoticon.service.EmoticonAcquireService;
import com.community.zconfiguration.response.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 내 로보티콘(user-web {@code useMyEmoticonsApi} 의 S1 부분) — 로그인 필요(401), {@code no-store}. 고르기 창이 읽는다.
 * {@code GET} 가진 팩 전부(로보티콘 포함, 최근 가진 것 먼저 — 판매 중단 팩도) · {@code DELETE /{packId}} 목록에서 빼기(무료 팩 — 가지지 않았으면 할 일 없음, 204).
 * 구매 내역·환불({@code /api/me/emoticon-purchases})은 S3.
 */
@RestController
@RequestMapping("/api/me/emoticons")
public class MyEmoticonController {

    private final EmoticonAcquireService acquire;

    public MyEmoticonController(EmoticonAcquireService acquire) {
        this.acquire = acquire;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<EmoticonPublicView.MyPacks>> mine(Authentication authentication) {
        return EmoticonController.noStore(acquire.mine(EmoticonController.userId(authentication)));
    }

    @DeleteMapping("/{packId}")
    public ResponseEntity<Void> remove(@PathVariable String packId, Authentication authentication) {
        acquire.remove(EmoticonController.userId(authentication), packId);
        return ResponseEntity.noContent().build();
    }
}
