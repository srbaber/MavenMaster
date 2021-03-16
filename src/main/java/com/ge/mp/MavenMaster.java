package com.ge.mp;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.apache.maven.model.io.xpp3.MavenXpp3Writer;

@Slf4j
public class MavenMaster {
    static final String MP_PARENT = "mp-parent";

    static final String NV_PRODUCT = "nv-product";

    static final String ROOT_DIR = "/Users/steve.baber/git/";

    static final String PROJECTS_ROOT = ROOT_DIR + "all";

    static final File NV_BOM = Paths.get(ROOT_DIR, NV_PRODUCT, "nv-bom", "pom.xml").toFile();

    static final File MP_PARENT_POM = Paths.get(ROOT_DIR, "all", MP_PARENT, "pom.xml").toFile();

    static final int MAX_DEPTH = 3;

    static final List PROJECTS_TO_EXCLUDE = Arrays.asList("MavenMaster", "nv-product", "mp-parent", "mp-docker-oracle-xe", "mp-docker-base-image", "mp-docker-elk", "mp-docker-phoenix-base", "klondike", "anaximander");

    static final List TEST_ONLY_DEPENDECIES = Arrays.asList("slf4j-simple");

    static final List DEPENDENCIES_TO_ALTER = Arrays.asList("slf4j-api", "slf4j-simple", "liquibase-slf4j",
            "commons-codec", "commons-collections", "commons-io", "commons-lang", "commons-lang3",
            "owasp-java-html-sanitizer", "jackson-datatype-joda", "metrics-core", "animal-sniffer-annotations", "checker-qual",
            "error_prone_annotations", "failureaccess", "guava", "j2objc-annotations", "jsr305", "listenablefuture", "joda-time");

    static final List COMPILE_ONLY_DEPENDENCIES = Arrays.asList("joda-time");

    static final String PROVIDED = "provided";

    static final String TEST = "test";

    static final String COMPILE = "compile";

    static final List CORRECT_SCOPES = Arrays.asList(TEST, PROVIDED);

    static final String BNSF_PRODUCT_DEPENDENCIES = "mp-bnsf-product-dependencies";

    static final String PARENT_VERSION = "6.0.2";

    static boolean TEST_ONLY = false;

    static List<String> MP_PARENT_PROPERTIES = null;


    private static final Predicate<Path> IsExcludedProject = new Predicate<Path>() {
        @Override
        public boolean test(Path file) {
            return PROJECTS_TO_EXCLUDE.contains(file.getFileName().toString());
        }
    };

    private static final Predicate<Path> IsDirectory = new Predicate<Path>() {
        @Override
        public boolean test(Path file) {
            return Files.isDirectory(file);
        }
    };

    private static final Predicate<Path> IsPom = new Predicate<Path>() {
        @Override
        public boolean test(Path path) {
            return path.endsWith("pom.xml");
        }
    };

    private static final Function<Path, File> PathToFileMapper = new Function<Path, File>() {
        @Override
        public File apply(Path path) {
            return path.toFile();
        }
    };

    private static final Predicate<Dependency> IsMatchingDependency = new Predicate<Dependency>() {
        @Override
        public boolean test(Dependency dependency) {
            return DEPENDENCIES_TO_ALTER.contains(dependency.getArtifactId());
        }
    };

    private static final Predicate<Dependency> HasIncorrectScope = new Predicate<Dependency>() {
        @Override
        public boolean test(Dependency dependency) {
            if (TEST_ONLY_DEPENDECIES.contains(dependency.getArtifactId())) {
                return !TEST.equals(dependency.getScope());
            }
            if (COMPILE_ONLY_DEPENDENCIES.contains(dependency.getArtifactId())) {
                return !(dependency.getScope() == null || COMPILE.equals(dependency.getScope()));
            }
            return !CORRECT_SCOPES.contains(dependency.getScope());
        }
    };


    List<File> findPoms(final String rootFolder) {
        Path rootPath = Paths.get(rootFolder);
        return findPoms(rootPath, 0);
    }

    List<File> findPoms(final Path rootPath, final int depth) {
        try {
            List<File> pomFiles = new ArrayList<>();

            if (IsExcludedProject.test(rootPath)) {
                return pomFiles;
            }

            List<Path> pomFile = Files.list(rootPath).filter(IsPom).collect(Collectors.toList());
            List<Path> subDirectories = Files.list(rootPath).filter(IsDirectory).collect(Collectors.toList());

            if (depth > 0) {
                pomFiles.addAll(pomFile.stream()
                        .map(PathToFileMapper)
                        .collect(Collectors.toList()));
            }

            if (depth < MAX_DEPTH) {
                subDirectories.forEach(subDir -> {
                    pomFiles.addAll(findPoms(subDir, depth + 1));
                });
            }

            return pomFiles;
        } catch (IOException exc) {
            log.error("Unable to find pom files under {}", rootPath, exc);
        }
        return new ArrayList<File>();
    }

    public boolean processPom(File file) {
        boolean altered = false;
        try {
            Model model = parsePomXmlFileToMavenPomModel(file);
            altered = alterPom(model);
            parseMavenPomModelToXmlString(file, model);
        } catch (Exception exc) {
            log.error("Unable to alter pom file {}", file, exc);
        }
        return altered;
    }

    public void alterDependency(Dependency dependency) {
        String scope = PROVIDED;

        if (TEST_ONLY_DEPENDECIES.contains(dependency.getArtifactId())) {
            scope = TEST;
        } else if (COMPILE_ONLY_DEPENDENCIES.contains(dependency.getArtifactId())) {
            scope = COMPILE;
        }

        log.info("    Altering dependency {}:{} scope:{}", dependency.getGroupId(), dependency.getArtifactId(), scope);
        dependency.setScope(scope);
        dependency.setVersion(null);
    }

    public boolean alterPom(Model model) {

        List<Dependency> dependenciesToAlter = model.getDependencies().stream()
                .filter(IsMatchingDependency)
                .filter(HasIncorrectScope)
                .collect(Collectors.toList());

        updateParent(model);
        // updateVersion(model);
        // cleanupVersionProperties(model);

        if (!dependenciesToAlter.isEmpty()) {
            // log.info("Processing project {}", model.getArtifactId());
            // dependenciesToAlter.forEach(this::alterDependency);
            return true;
        }

        return false;
    }

    public void cleanupVersionProperties(Model model) {
        List<String> mpParentProperties = getMpParentProperties(MP_PARENT_POM);

        mpParentProperties.stream()
                .filter(model.getProperties()::containsKey)
                .peek(existingKey -> log.info("Parent versioning cleaned for project {} {}", model.getArtifactId(), existingKey))
                .forEach(model.getProperties()::remove);
    }

    public List<String> getMpParentProperties(File parentPomFile) {
        if (MP_PARENT_PROPERTIES == null) {
            try {
                MP_PARENT_PROPERTIES = new ArrayList<>();
                Model parentModel = parsePomXmlFileToMavenPomModel(parentPomFile);
                parentModel.getProperties().keySet().forEach(key -> MP_PARENT_PROPERTIES.add((String) key));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return MP_PARENT_PROPERTIES;
    }

    public void updateNVBom(Model model, String oldVersion, String newVersion) {
        if (model.getArtifactId().endsWith("-it") || model.getArtifactId().endsWith("-maestro-plugin")) {
            return;
        }

        try {
            Model nvBomModel = parsePomXmlFileToMavenPomModel(NV_BOM);

            String bomVersion = "";
            String key = model.getArtifactId() + ".version";
            if (nvBomModel.getProperties().containsKey(key)) {
                // exact match
                bomVersion = nvBomModel.getProperties().getProperty(key);
            }

            if (StringUtils.isEmpty(bomVersion)) {
                // parent match
                key = key.replaceAll("-service", "").replaceAll("-api", "").replaceAll("-parent", "");
                if (nvBomModel.getProperties().containsKey(key)) {
                    bomVersion = nvBomModel.getProperties().getProperty(key);
                }
            }

            if (bomVersion.startsWith("$")) {
                // don't change variables
                return;
            } else if (StringUtils.isEmpty(bomVersion)) {
                log.warn("Could not identify version key for project {}", model.getArtifactId());
            } else if (bomVersion.equals(newVersion)) {
                log.warn("NV-BOM version unchanged for project {}", model.getArtifactId());
                return;
            } else {
                log.info("NV-BOM version updated for project {} {} {}:{}", model.getArtifactId(), key, oldVersion, newVersion);
                nvBomModel.getProperties().setProperty(key, newVersion);
                parseMavenPomModelToXmlString(NV_BOM, nvBomModel);
            }
        } catch (Exception exc) {
            log.error("Unable to open NV-BOM pom.xml from {}", NV_BOM.toString(), exc);
        }

    }

    public void updateVersion(Model model) {
        try {
            boolean useParentPom = false;
            String oldVersion = model.getVersion();
            if (oldVersion == null && !BNSF_PRODUCT_DEPENDENCIES.equals(model.getParent().getArtifactId())) {
                oldVersion = model.getParent().getVersion();
                useParentPom = true;
            }
            if (oldVersion == null) {
                return;
            }

            Version version = Version.parse(oldVersion);
            version.addMajor(0);
            version.setMinor(0);
            version.setBuild(0);
            version.setSuffix(null);

            String newVersion = version.toStringWithPrefixAndSuffix(3);

            if (useParentPom) {
                log.info("Altering parent version {} {}:{}", model.getArtifactId(), oldVersion, newVersion);
                model.getParent().setVersion(newVersion);
            } else {
                log.info("Altering version {} {}:{}", model.getArtifactId(), oldVersion, newVersion);
                model.setVersion(newVersion);
            }

            updateNVBom(model, oldVersion, newVersion);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void updateParent(Model model) {
        if (MP_PARENT.equals(model.getParent().getArtifactId())) {
            if (!PARENT_VERSION.equals(model.getParent().getVersion())) {
                log.info("    Altering parent version {}:{}", model.getParent().getVersion(), PARENT_VERSION);
                model.getParent().setVersion(PARENT_VERSION);
            }
        }
    }

    public void execute() {
        List<File> pomFileList = findPoms(PROJECTS_ROOT);
        pomFileList.sort(File::compareTo);
        pomFileList.forEach(this::processPom);
    }

    public static void main(String[] args) throws Exception {
        MavenMaster mavenMaster = new MavenMaster();
        mavenMaster.execute();
    }

    public static Model parsePomXmlFileToMavenPomModel(File file) throws Exception {
        Model model = null;
        FileReader reader = null;
        MavenXpp3Reader mavenreader = new MavenXpp3Reader();
        reader = new FileReader(file);
        model = mavenreader.read(reader);
        reader.close();
        return model;
    }

    public static void parseMavenPomModelToXmlString(File file, Model model) throws Exception {
        if (TEST_ONLY) {
            return;
        }

        MavenXpp3Writer mavenWriter = new MavenXpp3Writer();
        Writer writer = new FileWriter(file);
        mavenWriter.write(writer, model);
        writer.flush();
        writer.close();
    }
}

