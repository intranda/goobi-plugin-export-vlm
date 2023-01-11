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
import org.goobi.beans.Process;
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
import de.sub.goobi.helper.NIOFileUtils;
import de.sub.goobi.helper.StorageProvider;

@RunWith(PowerMockRunner.class)
@PrepareForTest({ ConfigPlugins.class, VlmExportPlugin.class, Process.class, StorageProvider.class })
@PowerMockIgnore({ "javax.management.*", "javax.net.ssl.*", "jdk.internal.reflect.*" })
public class VlmExportPluginTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();
    private File tempFolder;
    private static String resourcesFolder;

    private static Path defaultGoobiConfig;

    @BeforeClass
    public static void setUpClass() throws Exception {
        resourcesFolder = "src/test/resources/"; // for junit tests in eclipse

        if (!Files.exists(Paths.get(resourcesFolder))) {
            resourcesFolder = "target/test-classes/"; // to run mvn test from cli or in jenkins
        }

        String log4jFile = resourcesFolder + "log4j2.xml"; // for junit tests in eclipse
        System.setProperty("log4j.configurationFile", log4jFile);

        Path template = Paths.get(VlmExportPluginTest.class.getClassLoader().getResource(".").getFile());

        defaultGoobiConfig = Paths.get(template.getParent().getParent().toString() + "/src/test/resources/config/goobi_config.properties");
        if (!Files.exists(defaultGoobiConfig)) {
            defaultGoobiConfig = Paths.get("target/test-classes/config/goobi_config.properties");
        }
    }

    @Before
    public void setUp() throws Exception {
        tempFolder = folder.newFolder("tmp");

        PowerMock.mockStatic(ConfigPlugins.class);
        EasyMock.expect(ConfigPlugins.getPluginConfig(EasyMock.anyString())).andReturn(getConfig()).anyTimes();
        PowerMock.replay(ConfigPlugins.class);

        PowerMock.mockStatic(StorageProvider.class);
        EasyMock.expect(StorageProvider.getInstance()).andReturn(new NIOFileUtils());
        PowerMock.replay(StorageProvider.class);
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

    }


    /*================= Tests for the private methods ================= */

    @Test
    public void testCreateFolderLocalGivenNull() throws Exception {
        VlmExportPlugin plugin = new VlmExportPlugin();
        assertFalse(WhiteboxImpl.invokeMethod(plugin, "createFolder", false, null));
    }

    @Test
    public void testCreateFolderLocalGivenEmptyPath() throws Exception {
        VlmExportPlugin plugin = new VlmExportPlugin();
        assertFalse(WhiteboxImpl.invokeMethod(plugin, "createFolder", false, Paths.get("")));
    }

    @Test
    public void testCreateFolderLocalGivenExistingPath() throws Exception {
        VlmExportPlugin plugin = new VlmExportPlugin();
        Path temp = Paths.get("/tmp");
        assertTrue(Files.exists(temp));
        assertTrue(WhiteboxImpl.invokeMethod(plugin, "createFolder", false, temp));
    }

    @Test
    public void testCreateFolderLocalGivenUnexistingPath() throws Exception {
        VlmExportPlugin plugin = new VlmExportPlugin();
        final Path path = Paths.get("/tmp/unexisting_path");
        assertFalse(Files.exists(path));
        assertTrue(WhiteboxImpl.invokeMethod(plugin, "createFolder", false, path));
        assertTrue(Files.exists(path));
        Files.delete(path);
        assertFalse(Files.exists(path));
    }


}

