package com.community.address.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.community.address.dto.response.AddressServerFileView;
import com.community.zconfiguration.exception.ValidationException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** 서버 폴더에서 적재 — 폴더 바로 아래 .zip 만 보이고, 목록에 있는 이름만 고를 수 있다(폴더 밖 경로 불가), 파일은 임시 파일이 아니다 */
class AddressServerFilesTest {

    @TempDir
    Path root;

    private Path folder() throws IOException {
        Path dir = Files.createDirectories(root.resolve("import"));
        Files.writeString(dir.resolve("202610_한글_전체분.zip"), "zip");
        Files.writeString(dir.resolve("202610_영문_전체분.ZIP"), "zip");
        Files.writeString(dir.resolve("empty.zip"), "");
        Files.writeString(dir.resolve("readme.txt"), "x");
        Files.writeString(dir.resolve(".hidden.zip"), "x");
        Files.createDirectories(dir.resolve("sub.zip"));
        Files.writeString(root.resolve("outside.zip"), "x");
        return dir;
    }

    @Test
    void 꺼져_있으면_목록이_비고_고르면_400() {
        AddressServerFiles off = new AddressServerFiles("  ");

        assertThat(off.enabled()).isFalse();
        assertThat(off.list()).isEmpty();
        assertThatThrownBy(() -> off.resolve(List.of("a.zip")))
                .isInstanceOfSatisfying(ValidationException.class, e -> assertThat(e.getFields().get("fileNames").key()).isEqualTo("address.import.serverDirOff"));
    }

    @Test
    void 폴더_바로_아래의_숨기지_않은_zip_만_이름_순으로_보인다() throws IOException {
        AddressServerFiles files = new AddressServerFiles(folder().toString());

        assertThat(files.enabled()).isTrue();
        assertThat(files.list()).extracting(AddressServerFileView::name).containsExactly("202610_영문_전체분.ZIP", "202610_한글_전체분.zip", "empty.zip");
    }

    @Test
    void 목록에_있는_이름만_고르고_지우지_않는_원천으로_준다() throws IOException {
        Path dir = folder();
        AddressServerFiles files = new AddressServerFiles(dir.toString());

        List<AddressFileReader.Source> sources = files.resolve(List.of("202610_한글_전체분.zip", "202610_영문_전체분.ZIP"));

        assertThat(sources).extracting(AddressFileReader.Source::name).containsExactly("202610_한글_전체분.zip", "202610_영문_전체분.ZIP");
        assertThat(sources).allMatch(source -> !source.temporary());
        assertThat(sources.getFirst().path()).isEqualTo(dir.toAbsolutePath().normalize().resolve("202610_한글_전체분.zip"));
    }

    @Test
    void 폴더_밖_경로_숨김_하위_폴더_목록에_없는_이름은_400() throws IOException {
        AddressServerFiles files = new AddressServerFiles(folder().toString());

        for (String name : List.of("../outside.zip", "readme.txt", ".hidden.zip", "sub.zip", "nope.zip")) {
            assertThatThrownBy(() -> files.resolve(List.of(name)))
                    .as(name)
                    .isInstanceOfSatisfying(ValidationException.class, e -> assertThat(e.getFields().get("fileNames").key()).isEqualTo("address.import.serverFileMissing"));
        }
    }

    @Test
    void 개수는_1에서_2개_빈_파일은_안_된다() throws IOException {
        AddressServerFiles files = new AddressServerFiles(folder().toString());

        assertThatThrownBy(() -> files.resolve(List.of())).isInstanceOfSatisfying(ValidationException.class,
                e -> assertThat(e.getFields().get("fileNames").key()).isEqualTo("address.import.filesCount"));
        assertThatThrownBy(() -> files.resolve(null)).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> files.resolve(List.of("202610_한글_전체분.zip", "202610_영문_전체분.ZIP", "empty.zip"))).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> files.resolve(List.of("empty.zip"))).isInstanceOfSatisfying(ValidationException.class,
                e -> assertThat(e.getFields().get("fileNames").key()).isEqualTo("address.import.fileEmpty"));
        assertThat(files.resolve(List.of("202610_한글_전체분.zip", "202610_한글_전체분.zip"))).hasSize(1); // 같은 이름은 한 번
    }
}
