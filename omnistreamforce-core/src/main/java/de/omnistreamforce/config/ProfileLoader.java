package de.omnistreamforce.config;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Perfiles de configuracion predefinidos y propios.
 * <p>
 * Se buscan en este orden, de modo que un perfil propio con el mismo nombre gana al de fabrica:
 * <ol>
 *   <li>{@code ~/.omnistreamforce/profiles/}</li>
 *   <li>{@code ./config/profiles/} del directorio actual</li>
 *   <li>los empaquetados en el propio jar</li>
 * </ol>
 */
public class ProfileLoader {

    private static final String CLASSPATH_DIR = "/profiles/";
    /** Los recursos de un jar no se pueden listar: el indice dice cuales hay. */
    private static final String CLASSPATH_INDEX = CLASSPATH_DIR + "index.txt";
    private static final String EXTENSION = ".yaml";

    private final ConfigManager configManager;
    private final Path userProfilesDir;
    private final Path workingProfilesDir;

    public ProfileLoader() {
        this(new ConfigManager(),
                Path.of(System.getProperty("user.home", "."), ".omnistreamforce", "profiles"),
                Path.of("config", "profiles"));
    }

    public ProfileLoader(ConfigManager configManager, Path userProfilesDir, Path workingProfilesDir) {
        this.configManager = configManager;
        this.userProfilesDir = userProfilesDir;
        this.workingProfilesDir = workingProfilesDir;
    }

    /** Nombres de perfil disponibles, sin repetir y en orden alfabetico. */
    public List<String> listProfiles() {
        Set<String> names = new LinkedHashSet<>();
        names.addAll(listFrom(userProfilesDir));
        names.addAll(listFrom(workingProfilesDir));
        names.addAll(listFromClasspath());
        return names.stream().sorted().toList();
    }

    public OmniStreamForceConfig loadProfile(String name) {
        Path fromUser = userProfilesDir.resolve(name + EXTENSION);
        if (Files.isRegularFile(fromUser)) {
            return configManager.load(fromUser);
        }
        Path fromWorking = workingProfilesDir.resolve(name + EXTENSION);
        if (Files.isRegularFile(fromWorking)) {
            return configManager.load(fromWorking);
        }
        try (InputStream input = ProfileLoader.class.getResourceAsStream(CLASSPATH_DIR + name + EXTENSION)) {
            if (input == null) {
                throw new IllegalArgumentException("No existe el perfil '" + name
                        + "'. Disponibles: " + listProfiles());
            }
            return configManager.load(input);
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo leer el perfil " + name, e);
        }
    }

    /** Guarda un perfil propio en el directorio del usuario y devuelve su ruta. */
    public Path saveProfile(String name, OmniStreamForceConfig config) {
        Path target = userProfilesDir.resolve(name + EXTENSION);
        configManager.save(config, target);
        return target;
    }

    public boolean exists(String name) {
        return listProfiles().contains(name);
    }

    private List<String> listFrom(Path directory) {
        if (directory == null || !Files.isDirectory(directory)) {
            return List.of();
        }
        try (Stream<Path> files = Files.list(directory)) {
            return files.filter(Files::isRegularFile)
                    .map(path -> path.getFileName().toString())
                    .filter(name -> name.endsWith(EXTENSION))
                    .map(name -> name.substring(0, name.length() - EXTENSION.length()))
                    .toList();
        } catch (IOException e) {
            return List.of();
        }
    }

    private List<String> listFromClasspath() {
        try (InputStream input = ProfileLoader.class.getResourceAsStream(CLASSPATH_INDEX)) {
            if (input == null) {
                return List.of();
            }
            List<String> names = new ArrayList<>();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(input, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String name = line.trim();
                    if (!name.isEmpty() && !name.startsWith("#")) {
                        names.add(name);
                    }
                }
            }
            return names;
        } catch (IOException e) {
            return List.of();
        }
    }
}
