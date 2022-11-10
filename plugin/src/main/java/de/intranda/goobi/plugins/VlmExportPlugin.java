package de.intranda.goobi.plugins;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import org.apache.commons.configuration.SubnodeConfiguration;
import org.apache.commons.configuration.XMLConfiguration;
import org.apache.commons.configuration.reloading.FileChangedReloadingStrategy;
import org.apache.commons.configuration.tree.xpath.XPathExpressionEngine;
import org.apache.commons.lang3.StringUtils;
import org.goobi.beans.Process;
import org.goobi.beans.Step;
import org.goobi.production.enums.LogType;
import org.goobi.production.enums.PluginType;
import org.goobi.production.plugin.interfaces.IExportPlugin;
import org.goobi.production.plugin.interfaces.IPlugin;

import de.sub.goobi.config.ConfigPlugins;
import de.sub.goobi.helper.Helper;
import de.sub.goobi.helper.StorageProvider;
import de.sub.goobi.helper.StorageProviderInterface;
import de.sub.goobi.helper.VariableReplacer;
import de.sub.goobi.helper.exceptions.DAOException;
import de.sub.goobi.helper.exceptions.ExportFileException;
import de.sub.goobi.helper.exceptions.SwapException;
import de.sub.goobi.helper.exceptions.UghHelperException;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;
import net.xeoh.plugins.base.annotations.PluginImplementation;
import ugh.dl.DigitalDocument;
import ugh.dl.DocStruct;
import ugh.dl.Fileformat;
import ugh.dl.Metadata;
import ugh.dl.Prefs;
import ugh.exceptions.DocStructHasNoTypeException;
import ugh.exceptions.MetadataTypeNotAllowedException;
import ugh.exceptions.PreferencesException;
import ugh.exceptions.ReadException;
import ugh.exceptions.TypeNotAllowedForParentException;
import ugh.exceptions.WriteException;

@PluginImplementation
@Log4j2
public class VlmExportPlugin implements IExportPlugin, IPlugin {

    private static final long serialVersionUID = 4183263742109935015L;
    private static final String ABORTION_MESSAGE = "Export aborted for process with ID ";
    private static final String COMPLETION_MESSAGE = "Export executed for process with ID ";
    @Getter
    private String title = "intranda_export_vlm";
    @Getter
    private PluginType type = PluginType.Export;
    @Getter
    @Setter
    private Step step;

    @Getter
    private List<String> problems;

    @Override
    public void setExportFulltext(boolean arg0) {
    }

    @Override
    public void setExportImages(boolean arg0) {
    }

    @Override
    public boolean startExport(Process process) throws IOException, InterruptedException, DocStructHasNoTypeException, PreferencesException,
    WriteException, MetadataTypeNotAllowedException, ExportFileException, UghHelperException, ReadException, SwapException, DAOException,
    TypeNotAllowedForParentException {
        String userHome = process.getProjekt().getDmsImportImagesPath();
        return startExport(process, userHome);
    }

    @Override
    public boolean startExport(Process process, String destination) throws IOException, InterruptedException, DocStructHasNoTypeException,
    PreferencesException, WriteException, MetadataTypeNotAllowedException, ExportFileException, UghHelperException, ReadException,
    SwapException, DAOException, TypeNotAllowedForParentException {

        log.debug("=============================== Starting VLM Export ===============================");

        String masterPath = process.getImagesOrigDirectory(false);
        log.debug("masterPath is: " + masterPath);
        // assure that the source folder is not empty
        if (new File(masterPath).list().length == 0) {
            logBoth(process.getId(), LogType.ERROR, "There is nothing to copy from '" + masterPath + "', it is empty!");
            logBoth(process.getId(), LogType.ERROR, ABORTION_MESSAGE + process.getId());
            return false;
        }

        // read information from config file
        SubnodeConfiguration config = getConfig(process);
        String path = config.getString("path");
        // destination will be only used as default value if <path> is not configured
        // hence we only have to assure that it is not null in that scenario
        if (StringUtils.isBlank(path)) {
            log.debug("Target 'path' is not configured, using default settings instead.");
            if (StringUtils.isBlank(destination)) {
                log.debug("The parameter 'destination' is invalid, restarting export with default settings.");
                return startExport(process);
            }
            path = destination;
        }

        String fieldIdentifier = config.getString("identifier");
        String fieldVolume = config.getString("volume");
        String subfolderPrefix = config.getString("subfolderPrefix", "");

        if (StringUtils.isBlank(fieldIdentifier) || StringUtils.isBlank(fieldVolume)) {
            logBoth(process.getId(), LogType.ERROR, "The configuration file for the VLM export is incomplete.");
            logBoth(process.getId(), LogType.ERROR, ABORTION_MESSAGE + process.getId());
            return false;
        }

        Path savingPath;
        
        String id = ""; // aimed to be the system number, e.g. ALMA MMS-ID
        String volumeTitle = ""; // used to distinguish volumes from one another
        
        boolean isOneVolumeWork = true;

        // read mets file to get its logical structure
        try {
            Fileformat ff = process.readMetadataFile();
            DigitalDocument dd = ff.getDigitalDocument();
            DocStruct logical = dd.getLogicalDocStruct();
            Prefs prefs = process.getRegelsatz().getPreferences();
            VariableReplacer vp = new VariableReplacer(dd, prefs, process, null);

            // replace Goobi Variables in the path string and get the Path object of it
            path = vp.replace(path);
            savingPath = Paths.get(path);
            log.debug("target path = " + path);

            // get the ID
            id = findMetadata(logical, fieldIdentifier);
            // assure that id is valid
            if (StringUtils.isBlank(id)) {
                logBoth(process.getId(), LogType.ERROR, "No valid id found. It seems that " + fieldIdentifier + " is invalid. Recheck it please.");
                logBoth(process.getId(), LogType.ERROR, ABORTION_MESSAGE + process.getId());
                return false;
            }

            // get the volumeTitle if the work is composed of several volumes 
            if (logical.getType().isAnchor()) {
                isOneVolumeWork = false;
                logical = logical.getAllChildren().get(0);
                volumeTitle = findMetadata(logical, fieldVolume).replace(" ", "_");
                if (StringUtils.isBlank(volumeTitle)) {
                    logBoth(process.getId(), LogType.ERROR,
                            "No valid volumeTitle found. It seems that " + fieldVolume + " is invalid. Recheck it please.");
                    logBoth(process.getId(), LogType.ERROR, ABORTION_MESSAGE + process.getId());
                    return false;
                }
            }

            log.debug("isOneVolumeWork = " + isOneVolumeWork);
            log.debug("id = " + id);
            if (!isOneVolumeWork) {
                log.debug("volumeTitle = " + volumeTitle);
            }

        } catch (ReadException | PreferencesException | IOException | SwapException e) {
            logBoth(process.getId(), LogType.ERROR, "Error happened: " + e);
            logBoth(process.getId(), LogType.ERROR, ABORTION_MESSAGE + process.getId());
            return false;
        }

        // id is already assured valid, let's create a folder named after it
        savingPath = savingPath.resolve(id);
        if (!createFolder(savingPath)) {
            logBoth(process.getId(), LogType.ERROR, "Something went wrong trying to create the directory: " + savingPath.toString());
            logBoth(process.getId(), LogType.ERROR, ABORTION_MESSAGE + process.getId());
            return false;
        }
        // now we have the root folder, great, let's find out if we need to create subfolders
        // subfolders are only needed if the book is not a one-volume work
        if (!isOneVolumeWork) {
            // volumeTitle is already assured, let's try to create a subfolder
            String subfolderName = subfolderPrefix + volumeTitle;
            savingPath = savingPath.resolve(subfolderName);
            if (!createFolder(savingPath)) {
                logBoth(process.getId(), LogType.ERROR, "Something went wrong trying to create the directory: " + savingPath.toString());
                logBoth(process.getId(), LogType.ERROR, ABORTION_MESSAGE + process.getId());
                return false;
            }
        }
        // if everything went well so far, then we only need to do the copy
        return tryCopy(process, Paths.get(masterPath), savingPath);
    }

    /**
     * 
     * @param process
     * @return SubnodeConfiguration object according to the project's name
     */
    private SubnodeConfiguration getConfig(Process process) {
        String projectName = process.getProjekt().getTitel();
        log.debug("projectName = " + projectName);
        XMLConfiguration xmlConfig = ConfigPlugins.getPluginConfig(title);
        xmlConfig.setExpressionEngine(new XPathExpressionEngine());
        xmlConfig.setReloadingStrategy(new FileChangedReloadingStrategy());
        SubnodeConfiguration conf = null;

        // order of configuration is:
        // 1.) project name matches
        // 2.) project is *
        try {
            conf = xmlConfig.configurationAt("//config[./project = '" + projectName + "']");
        } catch (IllegalArgumentException e) {
            conf = xmlConfig.configurationAt("//config[./project = '*']");
        }

        return conf;
    }

    /**
     * 
     * @param path the absolute path of the targeted folder
     * @return true if the folder already exists or is successfully created, false if failure happens.
     */
    private boolean createFolder(Path path) {
        if (path == null) {
            log.error("The path provided is null!");
            return false;
        }
        if (StringUtils.isBlank(path.toString())) {
            log.error("The path provided is empty!");
            return false;
        }
        StorageProviderInterface provider = StorageProvider.getInstance();
        if (provider.isFileExists(path)) {
            log.debug("Directory already exisits: "  + path.toString());
            return true;
        }
        try {
            provider.createDirectories(path);
            log.debug("Directory created: " + path.toString());
            return true;
        } catch (IOException e) {
            log.error("Failed to create directory: " + path.toString());
            return false;
        }
    }

    /**
     * 
     * @param logical logical structure of a book as an object of DocStruct
     * @param fieldName value of which we want inside "logical"
     * @return the value of "fieldName" inside "logical" as String
     */
    private String findMetadata(DocStruct logical, String fieldName) {
        String fieldValue = "";
        for (Metadata md : logical.getAllMetadata()) {
            if (md.getType().getName().equals(fieldName)) {
                // field found
                fieldValue = md.getValue().trim();
                break;
            }
        }
        return fieldValue;
    }

    /**
     * 
     * @param fromPath absolute path to the source folder
     * @param toPath absolute path to the targeted folder
     * @throws IOException
     */
    private void copyImages(Path fromPath, Path toPath) throws IOException {
        log.debug("Copy images from '" + fromPath.toString() + "' to '" + toPath.toString() + "'.");
        StorageProviderInterface provider = StorageProvider.getInstance();
        List<String> files = provider.list(fromPath.toString());
        for (String file : files) {
            Path srcPath = fromPath.resolve(file);
            Path destPath = toPath.resolve(file);
            provider.copyFile(srcPath, destPath);
        }
    }

    /**
     * 
     * @param process
     * @param fromPath absolute path to the souce folder
     * @param toPath absolute path to the target folder
     * @return true if the copy is successfully performed, false otherwise
     * @throws IOException
     */
    private boolean tryCopy(Process process, Path fromPath, Path toPath) {
        StorageProviderInterface provider = StorageProvider.getInstance();
        if (!provider.list(toPath.toString()).isEmpty()) {
            logBoth(process.getId(), LogType.ERROR, "The directory: '" + toPath.toString() + "' is not empty!");
            logBoth(process.getId(), LogType.ERROR, ABORTION_MESSAGE + process.getId());
            return false;
        }
        // if the folder is empty, great!
        try {
            copyImages(fromPath, toPath);

        } catch (IOException e) {
            logBoth(process.getId(), LogType.ERROR,
                    "Errors happened trying to copy from '" + fromPath.toString() + "' to '" + toPath.toString() + "'.");
            logBoth(process.getId(), LogType.ERROR, ABORTION_MESSAGE + process.getId());
            return false;
        }
        logBoth(process.getId(), LogType.INFO, "Images from '" + fromPath.toString() + "' are successfully copied to '" + toPath.toString() + "'.");
        logBoth(process.getId(), LogType.INFO, COMPLETION_MESSAGE + process.getId());
        log.debug("=============================== Stopping VLM Export ===============================");
        return true;
    }

    /**
     * 
     * @param processId
     * @param logType
     * @param message message to be shown to both terminal and journal
     */
    private void logBoth(int processId, LogType logType, String message) {
        String logMessage = "VLM Export Plugin: " + message;
        switch (logType) {
            case INFO:
                log.info(logMessage);
                break;
            case ERROR:
                log.error(logMessage);
                break;
        }
        if (processId > 0) {
            Helper.addMessageToProcessJournal(processId, logType, logMessage);
        }
    }

}