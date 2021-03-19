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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import io.fabric8.maven.Maven;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.apache.maven.model.io.xpp3.MavenXpp3Writer;
import org.codehaus.plexus.util.FileUtils;

@Slf4j
public class MavenMaster {
    static final String MP_PARENT = "mp-parent";

    static final String NV_PRODUCT = "nv-product";

    static final String ROOT_DIR = "/Users/steve.baber/git/";

    static final String PROJECTS_ROOT = ROOT_DIR + "all";

    static final File NV_BOM = Paths.get(ROOT_DIR, NV_PRODUCT, "nv-bom", "pom.xml").toFile();

    static final File MP_PARENT_POM = Paths.get(ROOT_DIR, MP_PARENT, "pom.xml").toFile();

    static final File MP_PARENT_BUILD_POM = Paths.get(ROOT_DIR, MP_PARENT, "build", "pom.xml").toFile();

    static final int MAX_DEPTH = 3;

    static final List PROJECTS_TO_EXCLUDE = Arrays.asList("MavenMaster", "nv-product", "mp-parent", "mp-docker-oracle-xe", "mp-docker-base-image", "mp-docker-elk", "mp-docker-phoenix-base", "klondike", "anaximander");

    static final List TEST_ONLY_DEPENDECIES = Arrays.asList("slf4j-simple");

    static final List DEPENDENCIES_TO_ALTER = Arrays.asList("slf4j-api", "slf4j-simple", "liquibase-slf4j",
            "commons-codec", "commons-collections", "commons-io", "commons-lang", "commons-lang3",
            "jackson-datatype-joda", "metrics-core", "animal-sniffer-annotations", "checker-qual",
            "error_prone_annotations", "failureaccess", "guava", "j2objc-annotations", "jsr305", "listenablefuture", "joda-time");

    static final List COMPILE_ONLY_DEPENDENCIES = Arrays.asList("joda-time");

    static final List OVERRIDABLE_PROPERTIES = Arrays.asList("jacoco.coverage", "pmd.maxViolations", "findbugs.failOnError", "checkstyle.maxViolations");

    static final Map<String, String> UPDATED_PROPERTIES = new HashMap() {{
        put("mp-jackson.version", "2.0.1");
        put("mp-liquibase.version", "2.0.1");
    }};

    static final List<String> PROJECTS_NEED_MP_LIQUIBASE = Arrays.asList("ar-services",
            "mp-activity-configuration-service",
            "mp-alexandria-service",
            "mp-bnsf-translation-service",
            "mp-cdms",
            "mp-device-state-service",
            "mp-flux-capacitor-service",
            "mp-historian-service",
            "mp-liquibase",
            "mp-note-service",
            "mp-notification-service",
            "mp-plan-manager-service",
            "mp-planned-activity-location-selector",
            "mp-planning-configuration",
            "mp-reporting",
            "mp-search",
            "mp-signal-authority-service",
            "mp-system-status",
            "mp-test-liquibase",
            "mp-topology-activity-location-service",
            "mp-topology-cav",
            "mp-topology-constraint-service",
            "mp-topology-crossing",
            "mp-topology-manager",
            "mp-topology-tdc-service",
            "mp-topology-track-service",
            "mp-topology-traversal",
            "mp-track-authority-service",
            "mp-train-activity-state-calculator-service",
            "mp-train-activity-state-service",
            "mp-train-bof-service",
            "mp-train-consist-composite-service",
            "mp-train-consist-service",
            "mp-train-crew-service",
            "mp-train-delay-service",
            "mp-train-graph-captor",
            "mp-train-helper-engine-service",
            "mp-train-location-service",
            "mp-train-order-service",
            "mp-train-schedule-composite-service",
            "mp-train-schedule-service",
            "mp-train-status-service",
            "mp-train-unyielding-service",
            "mp-view",
            "mp-zuul",
            "rtmp-planning-service");

    static final List<String> REQUIRES_VERSION = Arrays.asList("mp-liquibase");

    static final String PROVIDED = "provided";

    static final String TEST = "test";

    static final String COMPILE = "compile";

    static final List CORRECT_SCOPES = Arrays.asList(TEST, PROVIDED);

    static final String BNSF_PRODUCT_DEPENDENCIES = "mp-bnsf-product-dependencies";

    static final String PARENT_VERSION = "6.0.5";

    static boolean DRYRUN_ONLY = false;

    static List<String> MP_PARENT_PROPERTIES = null;

    static List<String> MP_PARENT_DEPENDENCIES = null;

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

        // updateParent(model);
        updateVersion(model);
        // cleanupVersionProperties(model);
        // cleanupVersionDependencies(model);

        if (!dependenciesToAlter.isEmpty()) {
            //log.info("Processing project {}", model.getArtifactId());
            //dependenciesToAlter.forEach(this::alterDependency);
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

        model.getDependencies().stream()
                .filter(dependency -> REQUIRES_VERSION.contains(dependency.getArtifactId()))
                .peek(dependency -> log.info("Version set for project {} artifact {}", model.getArtifactId(), dependency.getArtifactId()))
                .forEach(dependency -> {
                    String key = dependency.getArtifactId() + ".version";
                    dependency.setVersion("${" + key + "}");
                });

        PROJECTS_NEED_MP_LIQUIBASE.stream()
                .filter(projectName -> model.getPomFile().getAbsolutePath().endsWith(projectName + "/pom.xml"))
                .peek(projectName -> log.info("Property added for project {} mp-liquibase.version ", model.getArtifactId()))
                .forEach(projectName -> model.getProperties().putIfAbsent("mp-liquibase.version", ""));

        UPDATED_PROPERTIES.entrySet().stream()
                .filter(entry -> model.getProperties().containsKey(entry.getKey()))
                .filter(entry -> !entry.getValue().equals(model.getProperties().getProperty(entry.getKey())))
                .peek(entry -> log.info("Property set for project {} {} {}:{}", model.getArtifactId(), entry.getKey(), model.getProperties().getProperty(entry.getKey()), entry.getValue()))
                .forEach(entry -> model.getProperties().setProperty(entry.getKey(), entry.getValue()));
    }

    public String dependencyKey(Dependency dependency) {
        return dependency.getGroupId() + ":" + dependency.getArtifactId();
    }

    public void cleanupVersionDependencies(Model model) {
        List<String> mpParentDependencies = getMpParentDependencies(MP_PARENT_BUILD_POM);
        model.getDependencies().stream().filter(dependency -> {
            return dependency.getVersion() != null && mpParentDependencies.contains(dependencyKey(dependency));
        }).forEach(dependency -> {
            log.info("Parent versioning cleaned for project {} {}:{}", model.getArtifactId(), dependencyKey(dependency), dependency.getVersion());
            dependency.setVersion(null);
        });
    }


    public List<String> getMpParentDependencies(File parentPomFile) {
        if (MP_PARENT_DEPENDENCIES == null) {
            try {
                MP_PARENT_DEPENDENCIES = new ArrayList<>();
                Model parentModel = parsePomXmlFileToMavenPomModel(parentPomFile);
                parentModel.getDependencyManagement().getDependencies().forEach(dependency -> MP_PARENT_DEPENDENCIES.add(dependencyKey(dependency)));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return MP_PARENT_DEPENDENCIES;
    }

    public List<String> getMpParentProperties(File parentPomFile) {
        if (MP_PARENT_PROPERTIES == null) {
            try {
                MP_PARENT_PROPERTIES = new ArrayList<>();
                Model parentModel = parsePomXmlFileToMavenPomModel(parentPomFile);
                parentModel.getProperties().keySet().stream().filter(key -> !OVERRIDABLE_PROPERTIES.contains(key)).forEach(key -> MP_PARENT_PROPERTIES.add((String) key));
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
                log.info("NV-BOM version updated for project {} {} {}:{}", model.getArtifactId(), key, bomVersion, newVersion);
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
            //version.addMajor(1);
            //version.setMinor(0);
            //version.setBuild(1);
            version.setSuffix(null);

            String newVersion = version.toStringWithPrefixAndSuffix(3);

            if (!oldVersion.equals(newVersion)) {
                if (useParentPom) {
                    log.info("Altering parent version {} {}:{}", model.getArtifactId(), oldVersion, newVersion);
                    model.getParent().setVersion(newVersion);
                } else {
                    log.info("Altering version {} {}:{}", model.getArtifactId(), oldVersion, newVersion);
                    model.setVersion(newVersion);
                }
            }
            updateNVBom(model, oldVersion, newVersion);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void updateParent(Model model) {
        if (model.getParent() != null && model.getParent().getArtifactId().startsWith(MP_PARENT)) {
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
        FileReader reader = new FileReader(file);
        Model model = Maven.readModel(reader);
        model.setPomFile(file);
        reader.close();
        return model;
    }

    public static Model parsePomXmlFileToMavenPomModelOrg(File file) throws Exception {
        Model model = null;
        FileReader reader = null;
        MavenXpp3Reader mavenreader = new MavenXpp3Reader();
        reader = new FileReader(file);
        model = mavenreader.read(reader);
        model.setPomFile(file);
        reader.close();
        return model;
    }

    public static void parseMavenPomModelToXmlString(File file, Model model) throws Exception {
        String outputFile = backupSourceAndOutput(file);

        Maven.writeModel(model, Paths.get(outputFile));
    }

    public static void parseMavenPomModelToXmlStringOrg(File file, Model model) throws Exception {
        String outputFile = backupSourceAndOutput(file);

        MavenXpp3Writer mavenWriter = new MavenXpp3Writer();
        Writer writer = new FileWriter(outputFile);
        mavenWriter.write(writer, model);
        writer.flush();
        writer.close();
    }

    private static String backupSourceAndOutput(File file) throws IOException {
        File backupFile = new File(file.getAbsolutePath() + ".orig");
        FileUtils.copyFile(file, backupFile);

        String outputFile = file.getAbsolutePath();
        if (DRYRUN_ONLY) {
            outputFile = file.getAbsolutePath() + ".dryrun";
            FileUtils.copyFile(file, new File(outputFile));
        }

        return outputFile;
    }
}

