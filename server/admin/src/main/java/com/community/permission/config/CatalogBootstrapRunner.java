package com.community.permission.config;

import com.community.permission.service.PermissionCatalogSynchronizer;
import com.community.role.service.BuiltinRoleSeeder;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * 서버가 뜰 때 권한 카탈로그(코드)를 DB 와 맞추고({@link PermissionCatalogSynchronizer}) 내장 회원 역할을 넣는다({@link BuiltinRoleSeeder}).
 * 순서가 중요하다 — 역할의 권한이 권한 표를 참조한다. 실패하면 기동을 멈춘다(권한 표가 코드와 어긋난 채로 요청을 받지 않게).
 */
@Component
public class CatalogBootstrapRunner implements ApplicationRunner {

    private final PermissionCatalogSynchronizer synchronizer;
    private final BuiltinRoleSeeder seeder;

    public CatalogBootstrapRunner(PermissionCatalogSynchronizer synchronizer, BuiltinRoleSeeder seeder) {
        this.synchronizer = synchronizer;
        this.seeder = seeder;
    }

    @Override
    public void run(ApplicationArguments args) {
        synchronizer.sync();
        seeder.seed();
    }
}
