package de.omnistreamforce.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProfileLoaderTest {

    private ProfileLoader loader(Path userDir, Path workingDir) {
        return new ProfileLoader(new ConfigManager(), userDir, workingDir);
    }

    @Test
    void packagedProfilesAreListedAndLoadable(@TempDir Path dir) {
        ProfileLoader loader = loader(dir.resolve("user"), dir.resolve("working"));

        assertThat(loader.listProfiles())
                .contains("development", "fastfood-burst", "healthcare-fast",
                        "ecommerce-spike", "multi-domain", "aws-msk-iam", "outbox-dual");

        OmniStreamForceConfig development = loader.loadProfile("development");
        assertThat(development.domains()).hasSize(1);
        assertThat(development.domains().get(0).domain()).isEqualTo("fastfood");
        assertThat(development.domains().get(0).eventsPerSecond()).isEqualTo(5);
        assertThat(development.domains().get(0).errorRate()).isEqualTo(50.0);
    }

    @Test
    void everyPackagedProfileIsValid(@TempDir Path dir) {
        ProfileLoader loader = loader(dir.resolve("user"), dir.resolve("working"));
        ConfigManager manager = new ConfigManager();

        for (String name : loader.listProfiles()) {
            OmniStreamForceConfig config = loader.loadProfile(name);
            assertThat(manager.validate(config))
                    .describedAs("perfil %s", name)
                    .isEmpty();
        }
    }

    @Test
    void theMultiDomainProfileCoversTheThreeDomains(@TempDir Path dir) {
        OmniStreamForceConfig config = loader(dir.resolve("user"), dir.resolve("working"))
                .loadProfile("multi-domain");

        assertThat(config.domains()).extracting(OmniStreamForceConfig.DomainMapping::domain)
                .containsExactly("fastfood", "ecommerce", "healthcare");
        assertThat(config.totalEventsPerSecond()).isEqualTo(190);
    }

    @Test
    void theDualProfileConfiguresBothDestinations(@TempDir Path dir) {
        OmniStreamForceConfig config = loader(dir.resolve("user"), dir.resolve("working"))
                .loadProfile("outbox-dual");

        assertThat(config.sink().type()).isEqualTo("DUAL");
        assertThat(config.sink().usesDatabase()).isTrue();
        assertThat(config.sink().usesKafka()).isTrue();
        assertThat(config.sink().jdbcUrl()).contains("postgresql");
    }

    @Test
    void aProfileOnDiskWinsOverThePackagedOne(@TempDir Path dir) throws IOException {
        Path userDir = dir.resolve("user");
        Files.createDirectories(userDir);
        Files.writeString(userDir.resolve("development.yaml"), """
                domains:
                  - domain: fastfood
                    eventsPerSecond: 999
                """, StandardCharsets.UTF_8);

        OmniStreamForceConfig config = loader(userDir, dir.resolve("working")).loadProfile("development");

        assertThat(config.domains().get(0).eventsPerSecond()).isEqualTo(999);
    }

    @Test
    void profilesInTheWorkingDirectoryAreAlsoFound(@TempDir Path dir) throws IOException {
        Path workingDir = dir.resolve("config/profiles");
        Files.createDirectories(workingDir);
        Files.writeString(workingDir.resolve("mio.yaml"), """
                domains:
                  - domain: ecommerce
                    eventsPerSecond: 7
                """, StandardCharsets.UTF_8);

        ProfileLoader loader = loader(dir.resolve("user"), workingDir);

        assertThat(loader.listProfiles()).contains("mio");
        assertThat(loader.exists("mio")).isTrue();
        assertThat(loader.loadProfile("mio").domains().get(0).eventsPerSecond()).isEqualTo(7);
    }

    @Test
    void savingAProfileMakesItAvailable(@TempDir Path dir) {
        Path userDir = dir.resolve("user");
        ProfileLoader loader = loader(userDir, dir.resolve("working"));
        OmniStreamForceConfig config = loader.loadProfile("healthcare-fast");

        Path saved = loader.saveProfile("mi-perfil", config);

        assertThat(Files.exists(saved)).isTrue();
        assertThat(loader.listProfiles()).contains("mi-perfil");
        assertThat(loader.loadProfile("mi-perfil").domains().get(0).domain()).isEqualTo("healthcare");
    }

    @Test
    void anUnknownProfileListsTheAvailableOnes(@TempDir Path dir) {
        ProfileLoader loader = loader(dir.resolve("user"), dir.resolve("working"));

        assertThatThrownBy(() -> loader.loadProfile("no-existe"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no-existe")
                .hasMessageContaining("development");
    }
}
