package com.ge.mp;


import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Paths;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class MavenMasterTest {

    static final MavenMaster mavenMaster = new MavenMaster();
    static final String rootDir = System.getProperty("user.dir");

    @BeforeAll
    public static void init() {
        mavenMaster.DRYRUN_ONLY = true;
    }

    @Test
    public void canListPoms() {
        List<File> pomFiles = mavenMaster.findPoms(rootDir);
        assertTrue(!pomFiles.isEmpty());

    }

    @Test
    public void canProcessPom() {
        File pomFile = Paths.get(rootDir, "src", "test", "resources", "pom.xml").toFile();
        boolean result = mavenMaster.processPom(pomFile);
        assertTrue(result);
    }

    @Test
    public void canGetMpParentProperties() {
        File pomFile = Paths.get(rootDir, "src", "test", "resources", "parent-pom.xml").toFile();
        List<String> parentProps = mavenMaster.getMpParentProperties(pomFile);
        assertTrue(!parentProps.isEmpty());
    }

    @Test
    public void canProcessAllPoms() {
        mavenMaster.DRYRUN_ONLY = true;
        mavenMaster.execute();
    }
}
