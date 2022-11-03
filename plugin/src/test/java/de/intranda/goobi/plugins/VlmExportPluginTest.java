package de.intranda.goobi.plugins;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.XMLConfiguration;
import org.apache.commons.configuration.reloading.FileChangedReloadingStrategy;
import org.easymock.EasyMock;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.powermock.api.easymock.PowerMock;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.powermock.reflect.internal.WhiteboxImpl;

import de.sub.goobi.config.ConfigPlugins;
//import de.sub.goobi.mock.MockProcess;

@RunWith(PowerMockRunner.class)
@PrepareForTest({ ConfigPlugins.class, VlmExportPlugin.class })
@PowerMockIgnore({ "javax.management.*", "javax.net.ssl.*", "jdk.internal.reflect.*" })
//@SuppressStaticInitialization("")
public class VlmExportPluginTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();
    private File tempFolder;
    private static String resourcesFolder;

    @BeforeClass
    public static void setUpClass() throws Exception {
        resourcesFolder = "src/test/resources/"; // for junit tests in eclipse

        if (!Files.exists(Paths.get(resourcesFolder))) {
            resourcesFolder = "target/test-classes/"; // to run mvn test from cli or in jenkins
        }

        String log4jFile = resourcesFolder + "log4j2.xml"; // for junit tests in eclipse

        System.setProperty("log4j.configurationFile", log4jFile);
    }

    @Before
    public void setUp() throws Exception {
        tempFolder = folder.newFolder("tmp");

        resourcesFolder = "src/test/resources/"; // for junit tests in eclipse

        if (!Files.exists(Paths.get(resourcesFolder))) {
            resourcesFolder = "target/test-classes/"; // to run mvn test from cli or in jenkins
        }

        PowerMock.mockStatic(ConfigPlugins.class);
        EasyMock.expect(ConfigPlugins.getPluginConfig(EasyMock.anyString())).andReturn(getConfig()).anyTimes();
        PowerMock.replay(ConfigPlugins.class);
    }

    @Test
    public void testConstructor() {
        VlmExportPlugin plugin = new VlmExportPlugin();
        assertNotNull(plugin);
    }

    private XMLConfiguration getConfig() {
        String file = "plugin_intranda_export_sample.xml";
        XMLConfiguration config = new XMLConfiguration();
        config.setDelimiterParsingDisabled(true);
        try {
            config.load(resourcesFolder + file);
        } catch (ConfigurationException e) {
        }
        config.setReloadingStrategy(new FileChangedReloadingStrategy());
        return config;
    }
    
    /* Tests for the method startExport(Process, String) */
    @Ignore
    @Test
    public void testStartExportGivenNullAsSecondArgument() throws Exception {
        //Process process = MockProcess.createProcess();
        //assertNotNull(process.getProjekt().getDmsImportImagesPath());
        //VlmExportPlugin plugin = new VlmExportPlugin();
        //assertEquals(plugin.startExport(process, null), plugin.startExport(process));
    }

    /*================= Tests for the private methods ================= */

    /* Tests for the method createFolder(String) */
    @Test
    public void testCreateFolderGivenEmptyString() throws Exception {
        VlmExportPlugin plugin = new VlmExportPlugin();
        assertFalse(WhiteboxImpl.invokeMethod(plugin, "createFolder", ""));
    }

    @Test
    public void testCreateFolderGivenExistingPath() throws Exception {
        VlmExportPlugin plugin = new VlmExportPlugin();
        assertTrue(Files.exists(Path.of("/tmp")));
        assertTrue(WhiteboxImpl.invokeMethod(plugin, "createFolder", "/tmp"));
    }

    @Test
    public void testCreateFolderGivenUnexistingPath() throws Exception {
        VlmExportPlugin plugin = new VlmExportPlugin();
        final String path = "/tmp/unexisting_path";
        assertFalse(Files.exists(Path.of(path)));
        assertTrue(WhiteboxImpl.invokeMethod(plugin, "createFolder", path));
        assertTrue(Files.exists(Path.of(path)));
        Files.delete(Path.of(path));
        assertFalse(Files.exists(Path.of(path)));
    }


}

