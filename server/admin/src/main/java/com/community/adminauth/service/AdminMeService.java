package com.community.adminauth.service;

import com.community.adminauth.dto.response.AdminMeResponse;
import com.community.adminauth.dto.response.AdminUserView;
import com.community.mfa.dto.response.AdminLoginMfa;
import com.community.permission.dto.AdminActor;
import com.community.permission.entity.Permission;
import com.community.site.dto.SiteBranding;
import com.community.site.service.SiteAssets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * 로그인 상태({@code me})·로그인 응답을 만든다. 사이트 이름과 로고 주소는 비로그인에도 준다(로그인 화면의 로고·탭 제목이 쓴다).
 * 권한 코드는 카탈로그({@link Permission}) 차례로 내려 같은 입력에 같은 순서가 나가게 한다. 관리자 2차 인증 단계({@code mfa})는 부르는 쪽이 정해 준다.
 */
@Service
public class AdminMeService {

    private final SiteAssets siteAssets;

    public AdminMeService(SiteAssets siteAssets) {
        this.siteAssets = siteAssets;
    }

    /** 이 운영자(없으면 비로그인)의 화면용 정보 — 2차 인증 안내 없음 */
    public AdminMeResponse of(Optional<AdminActor> actor) {
        return of(actor, null, null);
    }

    /**
     * @param mfa               2차 인증 단계(없으면 null) — 비로그인이면 2차 대기 세션의 단계
     * @param recoveryCodesLeft 복구 코드로 들어왔을 때 남은 개수(없으면 null)
     */
    public AdminMeResponse of(Optional<AdminActor> actor, AdminLoginMfa mfa, Integer recoveryCodesLeft) {
        Optional<SiteBranding> branding = siteAssets.branding();
        String siteName = branding.map(SiteBranding::siteName).orElse(null);
        String siteLogoUrl = branding.map(SiteBranding::logoUrl).orElse(null);
        if (actor.isEmpty()) {
            return new AdminMeResponse(false, null, siteName, siteLogoUrl, mfa, null);
        }
        AdminActor a = actor.get();
        List<String> permissions = new ArrayList<>();
        for (Permission permission : Permission.values()) {
            if (a.can(permission)) {
                permissions.add(permission.code());
            }
        }
        AdminUserView user = new AdminUserView(Long.toString(a.operatorId()), a.operatorName(), a.root() ? "ADMIN" : "STAFF", a.email(), a.root(),
                List.copyOf(permissions));
        return new AdminMeResponse(true, user, siteName, siteLogoUrl, mfa, recoveryCodesLeft);
    }
}
