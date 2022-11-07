package de.intranda.goobi.plugins;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Stream;

import org.apache.commons.lang.StringUtils;
import org.goobi.beans.Process;
import org.goobi.beans.Step;
import org.goobi.production.enums.LogType;
import org.goobi.production.enums.PluginType;
import org.goobi.production.plugin.interfaces.IExportPlugin;
import org.goobi.production.plugin.interfaces.IPlugin;

import de.sub.goobi.config.ConfigPlugins;
import de.sub.goobi.helper.Helper;
import de.sub.goobi.helper.StorageProvider;
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


        // TODO: What is the destination used for? I think it is not needed.
        if (destination == null) {
            return startExport(process);
        }

        // TODO: never never never write hard coded pathes here. target shall come from config file
        destination = destination.replace("{goobiFolder}", "/opt/digiverso/goobi/").replace("goobi/../", "");
        log.debug("destination = " + destination);
        
        // TODO: Better use 'fieldIdentifier' instead of 'idname'
        // read information from config file
        
        // TODO: just use the second parameter here if there is really a default to set
        String idName = ConfigPlugins.getPluginConfig(title).getString("idname", "");
        // TODO: Better use org.apache.commons.lang.StringUtils.isBlank ... here as it checks for null and empty string
        if (idName.equals("")) {
        	// TODO: the parameter is named differently. Just write that the plugin configuration file is incomplete (without filename)
            logBoth(process.getId(), LogType.ERROR, "The \"name\" part in plugin_intranda_export_vlm.xml cannot be left empty.");
            logBoth(process.getId(), LogType.ERROR, ABORTION_MESSAGE + process.getId());
            return false;
        }

        String masterPath = process.getImagesOrigDirectory(false);
        log.debug("masterPath is: " + masterPath);
        // assure that the source folder is not empty
        if (new File(masterPath).list().length == 0) {
        	// TODO: Better use single ticks like 'this' - then it is easear to read without double quotation
            logBoth(process.getId(), LogType.ERROR, "There is nothing to copy from \"" + masterPath + "\", it is empty!");
            logBoth(process.getId(), LogType.ERROR, ABORTION_MESSAGE + process.getId());
            return false;
        }

        // TODO: use a better parameter name here like 'fieldVolume'
        // TODO: just use the second parameter here if there is really a default to set
        String volumeName = ConfigPlugins.getPluginConfig(title).getString("volumename", "");
        // TODO: just use the second parameter here if there is really a default to set
        // TODO: why do you trim here but never before? 
        String savingPath = ConfigPlugins.getPluginConfig(title).getString("path", "").trim();

        // TODO: why so complicated
        savingPath = savingPath.equals("") ? destination : savingPath;
        // TODO: don't do that. simply expect a complete path from config file
        if (!savingPath.startsWith("/")) { // using relative path
            savingPath = destination + savingPath;
            log.debug("savingPath = " + savingPath);
        }
        if (!savingPath.endsWith("/")) {
            savingPath += "/";
        }

        
        
        // if would do this a lot easier
//        String fieldIdentifier = ConfigPlugins.getPluginConfig(title).getString("fieldIdentifier");
//        String fieldVolume = ConfigPlugins.getPluginConfig(title).getString("fieldVolume");
//        String path = ConfigPlugins.getPluginConfig(title).getString("path");
//        
//        if (StringUtils.isBlank(fieldIdentifier)  || StringUtils.isBlank(fieldVolume)  || StringUtils.isBlank(path)) {
//        	  logBoth(process.getId(), LogType.ERROR, "The configuration file for the VLM export is incomplete.");
//            logBoth(process.getId(), LogType.ERROR, ABORTION_MESSAGE + process.getId());
//            return false;
//        }
        
        
        String id = ""; // aimed to be the system number, e.g. ALMA MMS-ID
        String volumeTitle = "";
        
        boolean isMonograph = true;

        // read mets file to get its logical structure
        try {
            Fileformat ff = process.readMetadataFile();
            DigitalDocument dd = ff.getDigitalDocument();
            DocStruct logical = dd.getLogicalDocStruct();

            // get the ID
            id = findID(logical, idName);
            // assure that id is valid
            if (id.equals("")) {
                logBoth(process.getId(), LogType.ERROR, "No valid id found. It seems that " + idName + " is invalid. Recheck it please.");
                logBoth(process.getId(), LogType.ERROR, ABORTION_MESSAGE + process.getId());
                return false;
            }

            // get the volumeTitle if the work is composed of several volumes 
            if (logical.getType().isAnchor()) {
                isMonograph = false;
                logical = logical.getAllChildren().get(0);
                // since this work is not a monograph, we have to assure that it has a valid volumeTitle
                // TODO: Better use StringUtils.isBlank here again
                if (volumeName.equals("")) {
                    logBoth(process.getId(), LogType.ERROR,
                            "The \"volumeName\" part in plugin_intranda_export_vlm.xml cannot be left empty, since this book is not a monograph. ");
                    logBoth(process.getId(), LogType.ERROR, ABORTION_MESSAGE + process.getId());
                    return false;
                }
                volumeTitle = findID(logical, volumeName).replace(" ", "_");
                // TODO: Better use StringUtils.isBlank here again
                if (volumeTitle.equals("")) {
                    logBoth(process.getId(), LogType.ERROR,
                            "No valid volumeTitle found. It seems that " + volumeName + " is invalid. Recheck it please.");
                    logBoth(process.getId(), LogType.ERROR, ABORTION_MESSAGE + process.getId());
                    return false;
                }
            }

            log.debug("isMonograph = " + isMonograph);
            log.debug("id = " + id);
            if (!isMonograph) {
                log.debug("volumeTitle = " + volumeTitle);
            }

        } catch (ReadException | PreferencesException | IOException | SwapException e) {
            logBoth(process.getId(), LogType.ERROR, "Error happened: " + e);
            logBoth(process.getId(), LogType.ERROR, ABORTION_MESSAGE + process.getId());
            return false;
        }

        // id is already assured valid, let's create a folder named after it
        savingPath += id;
        if (!createFolder(savingPath)) {
            logBoth(process.getId(), LogType.ERROR, "Something went wrong trying to create the directory: " + savingPath);
            logBoth(process.getId(), LogType.ERROR, ABORTION_MESSAGE + process.getId());
            return false;
        }
        // now we have the root folder, great, let's find out if we need to create subfolders
        // subfolders are only needed if the book is not a monograph
        if (!isMonograph) {
            // volumeTitle is already assured, let's try to create a subfolder
            String subfolderName = "T_34_L_" + volumeTitle;
            // TODO: Don't use / manually. Better use this: https://www.baeldung.com/java-file-vs-file-path-separator
            savingPath += "/" + subfolderName;
            if (!createFolder(savingPath)) {
                logBoth(process.getId(), LogType.ERROR, "Something went wrong trying to create the directory: " + savingPath);
                logBoth(process.getId(), LogType.ERROR, ABORTION_MESSAGE + process.getId());
                return false;
            }
        }
        // if everything went well so far, then we only need to do the copy
        return tryCopy(process, masterPath, savingPath);
    }

    /**
     * 
     * @param path the absolute path as a String
     * @return true if the folder already exists or is successfully created, false if failure happens.
     */
    private boolean createFolder(String path) {
    	// TODO: Better use the StorageProvide for file operations to let it work on all file systems
//    	StorageProvider.getInstance().createDirectories(null);
    	
        File directory = new File(path);
        if (directory.exists()) {
            log.debug("Directory already exisits: " + path);
        } else if (directory.mkdirs()) {
            log.debug("Directory created: " + path);
        } else {
            log.error("Failed to create directory: " + path);
            return false;
        }
        return true;
    }
    
    /**
     * 
     * @param logical logical structure of a book as an object of DocStruct
     * @param idName value of which we want inside "logical"
     * @return the value of "idName" inside "logical" as String
     */
    // TODO: it is not really to find an ID, is it? Shouldn't it be better findMetadata?
    private String findID(DocStruct logical, String idName) {
        String id = "";
        for (Metadata md : logical.getAllMetadata()) {
            if (md.getType().getName().equals(idName)) {
                // id found
                id = md.getValue().trim();
                break;
            }
        }
        return id;
    }

    /**
     * 
     * @param fromPath absolute path to the source folder as String
     * @param toPath absolute path to the targeted folder as String
     * @throws IOException
     */
    private void copyImages(String fromPath, String toPath) throws IOException {
        log.debug("Copy images from \"" + fromPath + "\" to \"" + toPath + "\".");
        String[] files = new File(fromPath).list();
        for (String filename : files) {
            Path originalPath = Paths.get(fromPath + "/" + filename);
            Path destPath = Paths.get(toPath + "/" + filename);
            Files.copy(originalPath, destPath);
            // TODO: Better use the StorageProvide for file operations to let it work on all file systems
//                   StorageProvider.getInstance().copyFile(originalPath, destPath);
        
        }
    }

    /**
     * 
     * @param process
     * @param fromPath absolute path to the souce folder as String
     * @param toPath absolute path to the targeted folder as String
     * @return true if the copy is successfully performed, false otherwise
     * @throws IOException
     */
    private boolean tryCopy(Process process, String fromPath, String toPath) throws IOException {
        try (Stream<Path> stream = Files.list(Path.of(toPath))) {
            if (stream.findFirst().isPresent()) { // the folder is not empty
                logBoth(process.getId(), LogType.ERROR, "The directory: \"" + toPath + "\" is not empty!");
                logBoth(process.getId(), LogType.ERROR, ABORTION_MESSAGE + process.getId());
                return false;
            }
            // if the folder is empty, great!
            copyImages(fromPath, toPath);
            logBoth(process.getId(), LogType.INFO, "Images from \"" + fromPath + " are successfully copied to \"" + toPath + "\".");
            logBoth(process.getId(), LogType.INFO, COMPLETION_MESSAGE + process.getId());
            log.debug("=============================== Stopping VLM Export ===============================");
            return true;
        }
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