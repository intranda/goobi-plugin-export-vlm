package de.intranda.goobi.plugins;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Stream;

import org.goobi.beans.Process;
import org.goobi.beans.Step;
import org.goobi.production.enums.PluginType;
import org.goobi.production.plugin.interfaces.IExportPlugin;
import org.goobi.production.plugin.interfaces.IPlugin;

import de.sub.goobi.config.ConfigPlugins;
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

        if (destination == null) {
            return startExport(process);
        }

        log.debug("destination = " + destination);
        destination = destination.replace("{goobiFolder}", "/opt/digiverso/goobi/").replace("goobi/../", "");
        log.debug("destination = " + destination);
        // read information from config file
        String idName = ConfigPlugins.getPluginConfig(title).getString("idname", "");
        if (idName.equals("")) {
            log.error("The <name> part in plugin_intranda_export_vlm.xml cannot be left empty.");
            log.error(ABORTION_MESSAGE + process.getId());
            return false;
        }
        String volumeName = ConfigPlugins.getPluginConfig(title).getString("volumename", "");
        String savingPath = ConfigPlugins.getPluginConfig(title).getString("path", "").trim();
        String masterPath = process.getImagesOrigDirectory(false);
        log.debug("masterPath is: " + masterPath);
        savingPath = savingPath.equals("") ? destination : savingPath;
        if (!savingPath.startsWith("/")) { // using relative path
            savingPath = destination + savingPath;
            log.debug("savingPath = " + savingPath);
        }
        if (!savingPath.endsWith("/")) {
            savingPath += "/";
        }
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
                log.error("No valid id found. It seems that " + idName + " is invalid. Recheck it please.");
                log.error(ABORTION_MESSAGE + process.getId());
                return false;
            }

            // get the volumeTitle if the work is composed of several volumes 
            if (logical.getType().isAnchor()) {
                isMonograph = false;
                logical = logical.getAllChildren().get(0);
                // since this work is not a monograph, we have to assure that it has a valid volumeTitle
                if (volumeName.equals("")) {
                    log.error("The <volumeName> part in plugin_intranda_export_vlm.xml cannot be left empty, since this book is not a monograph. ");
                    log.error(ABORTION_MESSAGE + process.getId());
                    return false;
                }
                volumeTitle = findID(logical, volumeName).replace(" ", "_");
                if (volumeTitle.equals("")) {
                    log.error("No valid volumeTitle found. It seems that " + volumeName + " is invalid. Recheck it please.");
                    log.error(ABORTION_MESSAGE + process.getId());
                    return false;
                }
            }

            log.debug("isMonograph = " + isMonograph);
            log.debug("id = " + id);
            if (!isMonograph) {
                log.debug("volumeTitle = " + volumeTitle);
            }

        } catch (ReadException | PreferencesException | IOException | SwapException e) {
            log.error(e);
            log.error(ABORTION_MESSAGE + process.getId());
            return false;
        }

        // id is already assured valid, let's create a folder named after it
        savingPath += id;
        if (!createFolder(savingPath)) {
            log.error("Something went wrong trying to create the directory: " + savingPath);
            log.error(ABORTION_MESSAGE + process.getId());
            return false;
        }
        // now we have the root folder, great, let's find out if we need to create subfolders
        // subfolders are only needed if the book is not a monograph
        if (!isMonograph) {
            // volumeTitle is already assured, let's try to create a subfolder
            String subfolderName = "T_34_L_" + volumeTitle;
            savingPath += "/" + subfolderName;
            if (!createFolder(savingPath)) {
                log.error("Something went wrong trying to create the directory: " + savingPath);
                log.error(ABORTION_MESSAGE + process.getId());
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
        if (files.length == 0) {
            log.debug("There is nothing to copy from \"" + fromPath + "\", it is empty!");
            return;
        }
        for (String filename : files) {
            Path originalPath = Paths.get(fromPath + "/" + filename);
            Path destPath = Paths.get(toPath + "/" + filename);
            Files.copy(originalPath, destPath);
        }
        log.debug("Images from \"" + fromPath + " are successfully copied to \"" + toPath + "\".");
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
                log.error("The directory: \"" + toPath + "\" is not empty!");
                log.error(ABORTION_MESSAGE + process.getId());
                return false;
            }
            // if the folder is empty, great!
            copyImages(fromPath, toPath);
            log.info(COMPLETION_MESSAGE + process.getId());
            log.debug("=============================== Stopping VLM Export ===============================");
            return true;
        }
    }

}