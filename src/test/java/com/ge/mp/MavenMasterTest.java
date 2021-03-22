package com.ge.mp;


import lombok.extern.slf4j.Slf4j;
import org.apache.maven.model.Model;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Paths;
import java.util.List;

import static com.ge.mp.MavenMaster.NV_PRODUCT;
import static com.ge.mp.MavenMaster.ROOT_DIR;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

@Slf4j
public class MavenMasterTest {

    static final MavenMaster mavenMaster = new MavenMaster();
    static final String rootDir = System.getProperty("user.dir");
    static boolean fail = false;

    @BeforeAll
    public static void init() {
        mavenMaster.DRYRUN_ONLY = false;
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


    @Test
    public void nothingIsUpVersioned()
    {
        try {
            fail =false;
            final File NV_BOM = Paths.get(ROOT_DIR, NV_PRODUCT, "nv-bom", "pom.xml").toFile();
            final File NV_BOM_DEVELOP = Paths.get(ROOT_DIR, "develop", NV_PRODUCT, "nv-bom", "pom.xml").toFile();
            Model newPom = MavenMaster.parsePomXmlFileToMavenPomModelOrg(NV_BOM);
            Model oldPom = MavenMaster.parsePomXmlFileToMavenPomModelOrg(NV_BOM_DEVELOP);

            newPom.getProperties().keySet().forEach(key -> {
                try {
                    String newValue = newPom.getProperties().getProperty((String) key);
                    String oldValue = oldPom.getProperties().getProperty((String) key);

                    if (newValue.startsWith("${") || oldValue.startsWith("${"))
                    {
                        // this is ok because it's a variable
                    } else {
                        Version newVersion = Version.parse(newValue);
                        Version oldVersion = Version.parse(oldValue);

                        int oldMajor = Integer.parseInt(oldVersion.getMajor());
                        int newMajor = Integer.parseInt(newVersion.getMajor());
                        if (oldMajor > newMajor) {
                            log.info("old major value is up versioned for {} {}:{}", key, oldValue, newValue);
                            fail = true;
                        } else if (oldMajor == newMajor)
                        {
                            int oldMinor = Integer.parseInt(oldVersion.getMinor());
                            int newMinor = Integer.parseInt(newVersion.getMinor());

                            if (oldMinor > newMinor)
                            {
                                log.info("old minor value is up versioned for {} {}:{}", key, oldValue, newValue);
                                fail = true;
                            } else if (oldMinor == newMinor)
                            {
                                int oldBuild = Integer.parseInt(oldVersion.getBuild());
                                int newBuild = Integer.parseInt(newVersion.getBuild());

                                if (oldBuild > newBuild)
                                {
                                    log.info("old build value is up versioned for {} {}:{}", key, oldValue, newValue);
                                    fail =true;
                                }
                            }
                        }
                        if ("-SNAPSHOT".equals(newVersion.getSuffix()))
                        {
                            log.info("project is snapshot version {} {}:{}", key, oldValue, newValue);
                            fail =true;
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    fail = true;
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
            fail = true;
        }
        if (fail)
        {
            fail();
        }

    }
}
