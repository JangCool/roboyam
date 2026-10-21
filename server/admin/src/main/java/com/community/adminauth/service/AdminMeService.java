package com.community.adminauth.service;

import com.community.adminauth.dto.response.AdminMeResponse;
import com.community.adminauth.dto.response.AdminUserView;
import com.community.permission.dto.AdminActor;
import com.community.permission.entity.Permission;
import com.community.site.dto.SiteValues;
import com.community.site.service.SiteSettingsService;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * 로그인 상태({@code me})·로그인 응답을 만든다. 사이트 이름은 비로그인에도 준다(로그인 화면의 로고·탭 제목이 쓴다).
 * 권한 코드는 카탈로그({@link Permission}) 차례로 내려 같은 입력에 같은 순서가 나가게 한다.
 */
@Service
public class AdminMeService {

    private final SiteSettingsService siteSettings;

    public AdminMeService(SiteSettingsService siteSettings) {
        this.siteSettings = siteSettings;
    }

    /** 이 운영자(없으면 비로그인)의 화면용 정보 */
    public AdminMeResponse of(Optional<AdminActor> actor) {
        String siteName = siteSettings.current().map(SiteValues::name).orElse(null);
        if (actor.isEmpty()) {
            return new AdminMeResponse(false, null, siteName, null);
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
        return new AdminMeResponse(true, user, siteName, null);
    }
}
