package com.community.address.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.community.address.dto.AddressProvider;
import com.community.address.dto.request.AddressSettingsSaveRequest;
import com.community.zconfiguration.exception.ValidationException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

/** 주소 DB 올리기 요청과 설정 저장의 칸 검사, 갱신 권고 판정(address-search-contract 「필드·검증」) — DB 없이 */
class AddressImportRulesTest {

    private static MultipartFile zip(String name, int bytes) {
        return new MockMultipartFile("files", name, "application/zip", new byte[bytes]);
    }

    @Test
    void 파일은_1개에서_2개() {
        assertThatThrownBy(() -> AddressImportService.check(List.of(), "MS949"))
                .isInstanceOfSatisfying(ValidationException.class, e -> assertThat(e.getFields()).containsOnlyKeys("files"));
        assertThatThrownBy(() -> AddressImportService.check(List.of(zip("a.zip", 1), zip("b.zip", 1), zip("c.zip", 1)), "MS949"))
                .isInstanceOfSatisfying(ValidationException.class, e -> assertThat(e.getFields().get("files").key()).isEqualTo("address.import.filesCount"));
        assertThatCode(() -> AddressImportService.check(List.of(zip("ko.zip", 1), zip("en.ZIP", 1)), "MS949")).doesNotThrowAnyException();
    }

    @Test
    void 파일은_zip_빈_파일_아님_300MB_이하_화면과_같은_순서() {
        assertThat(AddressImportService.fileProblem("rnaddrkor_seoul.txt", 10).key()).isEqualTo("address.import.fileNotZip");
        assertThat(AddressImportService.fileProblem(null, 10).key()).isEqualTo("address.import.fileNotZip");
        assertThat(AddressImportService.fileProblem("a.txt", 0).key()).isEqualTo("address.import.fileNotZip");
        assertThat(AddressImportService.fileProblem("a.zip", 0).key()).isEqualTo("address.import.fileEmpty");
        assertThat(AddressImportService.fileProblem("a.zip", AddressImportService.FILE_MAX_BYTES + 1).key()).isEqualTo("address.import.fileTooLarge");
        assertThat(AddressImportService.fileProblem("a.zip", AddressImportService.FILE_MAX_BYTES)).isNull();
        assertThat(AddressImportService.FILE_MAX_BYTES).isEqualTo(300L * 1024 * 1024);
    }

    @Test
    void 글자_집합은_MS949_와_UTF_8_만_파일_칸과_같이_모아_알린다() {
        assertThatThrownBy(() -> AddressImportService.check(List.of(zip("a.txt", 1)), "EUC-JP"))
                .isInstanceOfSatisfying(ValidationException.class, e -> {
                    assertThat(e.getFields()).containsOnlyKeys("files", "charset");
                    assertThat(e.getFields().get("charset").key()).isEqualTo("address.import.charsetUnknown");
                });
        assertThatThrownBy(() -> AddressImportService.check(List.of(zip("a.zip", 1)), null)).isInstanceOf(ValidationException.class);
        assertThatCode(() -> AddressImportService.check(List.of(zip("a.zip", 1)), "UTF-8")).doesNotThrowAnyException();
    }

    @Test
    void 설정_저장은_세_칸을_모두_보고_틀린_칸을_모은다() {
        assertThatThrownBy(() -> AddressSettingsService.check(new AddressSettingsSaveRequest("KAKAO", "http://x.example/p.js", 3)))
                .isInstanceOfSatisfying(ValidationException.class, e -> {
                    assertThat(e.getFields()).containsOnlyKeys("provider", "scriptUrl", "staleWarnDays");
                    assertThat(e.getFields().get("provider").key()).isEqualTo("address.settings.providerUnknown");
                });
        assertThatThrownBy(() -> AddressSettingsService.check(new AddressSettingsSaveRequest(null, null, null)))
                .isInstanceOfSatisfying(ValidationException.class, e -> assertThat(e.getFields()).containsOnlyKeys("provider", "staleWarnDays"));
        assertThat(AddressSettingsService.check(new AddressSettingsSaveRequest("DAUM_FALLBACK_INTERNAL", "", 60))).isEqualTo(AddressProvider.DAUM_FALLBACK_INTERNAL);
    }

    @Test
    void 갱신_권고는_마지막_적재가_권고_일수보다_오래됐거나_올린_적이_없을_때() {
        Instant now = Instant.parse("2026-10-30T00:00:00Z");
        assertThat(AddressSettingsService.stale(null, 60, now)).isTrue();
        assertThat(AddressSettingsService.stale(now.minus(Duration.ofDays(60)), 60, now)).isFalse();
        assertThat(AddressSettingsService.stale(now.minus(Duration.ofDays(60)).minusSeconds(1), 60, now)).isTrue();
        assertThat(AddressSettingsService.stale(now.minus(Duration.ofDays(10)), 7, now)).isTrue();
    }
}
