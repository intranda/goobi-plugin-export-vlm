package de.intranda.goobi.plugins;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import org.goobi.beans.Process;
import org.goobi.beans.Step;
import org.goobi.production.enums.PluginType;
import org.goobi.production.plugin.interfaces.IExportPlugin;
import org.goobi.production.plugin.interfaces.IPlugin;

import de.sub.goobi.config.ConfigPlugins;
import de.sub.goobi.export.dms.ExportDms;
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
        String benutzerHome = process.getProjekt().getDmsImportImagesPath();
        log.debug("benutzerHome is: " + benutzerHome);
        return startExport(process, benutzerHome);
    }

    @Override
    public boolean startExport(Process process, String destination) throws IOException, InterruptedException, DocStructHasNoTypeException,
    PreferencesException, WriteException, MetadataTypeNotAllowedException, ExportFileException, UghHelperException, ReadException,
    SwapException, DAOException, TypeNotAllowedForParentException {
        problems = new ArrayList<>();

        // read information from config file
        log.debug("===========================Starting VLM Export =============================");
        String test = ConfigPlugins.getPluginConfig(title).getString("value", "");
        log.debug("value from configuration file: " + test);

        String idName = ConfigPlugins.getPluginConfig(title).getString("name", "");
        if (idName.equals("")) {
            log.error("The <name> part in plugin_intranda_export_vlm.xml cannot be left empty.");
            return false;
        }
        String bandName = ConfigPlugins.getPluginConfig(title).getString("bandname", "");
        String savingPath = ConfigPlugins.getPluginConfig(title).getString("path", "").trim();
        String masterPath = process.getImagesOrigDirectory(false);
        log.debug("masterPath is: " + masterPath);
        savingPath = savingPath.equals("") ? "/home/zehong/work/test/vlm_test/" : savingPath; // TODO: use destination to replace the default directory
        if (!savingPath.endsWith("/")) {
            savingPath += "/";
        }
        String id = ""; // aimed to be ALMA MMS-ID
        String bandTitle = "";
        
        boolean isMonograph = true;

        // read mets file to test if it is readable
        try {
            //            Prefs prefs = process.getRegelsatz().getPreferences();
            Fileformat ff = process.readMetadataFile();
            DigitalDocument dd = ff.getDigitalDocument();
            DocStruct logical = dd.getLogicalDocStruct();

            id = findID(logical, idName);

            if (logical.getType().isAnchor()) {
                isMonograph = false;
                logical = logical.getAllChildren().get(0);
                bandTitle = findID(logical, bandName).replace(" ", "_");
            }

            log.debug("isMonograph = " + isMonograph);
            log.debug("id = " + id);
            log.debug("bandTitle = " + bandTitle);

            //            VariableReplacer replacer = new VariableReplacer(dd, prefs, process, null);
        } catch (ReadException | PreferencesException | IOException | SwapException e) {
            log.error(e);
            problems.add("Cannot read metadata file.");
            return false;
        }

        // create a folder named after id
        if (id.equals("")) {
            log.error("No valid id found. It seems that " + idName + " is not valid.");
            return false;
        }
        savingPath += id;
        if (createFolder(savingPath)) {
            // pathMaster := get path to the master folder
            // if monograph:
            //      if folder is not empty: 
            //          warning
            //      else:
            //          copy pictures from ${pathMaster} to this new folder
            // else:
            //      subfolderName := "T_34_L_" + configured name (e.g. CatalogIDDigital)
            //      try to create a sub-folder named ${subfolderName}
            //      if sub-folder successfully created:
            //          copy pictures from ${pathMaster} into this sub-folder
            //      else: 
            //          exception or warning (like "sub-folder" already exists)
            if (isMonograph) {
                if (Files.list(Path.of(savingPath)).findFirst().isPresent()) { // if the folder is not empty
                    log.debug("Warning: the directory: \"" + savingPath + "\" is not empty!");
                } else {
                    copyImages(masterPath, savingPath);
                }
            } else { // !isMonograph
                if (bandTitle.equals("")) {
                    log.error(
                            "The <bandname> part in plugin_intranda_export_vlm.xml is not supposed to be left empty, since this book is not a monograph. ");
                } else { // bandTitle != ""
                    String subfolderName = "T_34_L_" + bandTitle;
                    log.debug(subfolderName);
                    savingPath += "/" + subfolderName;
                    if (createFolder(savingPath)) {
                        if (Files.list(Path.of(savingPath)).findFirst().isPresent()) {
                            log.error("The directory: \"" + savingPath + "\" is not empty!");
                        } else {
                            copyImages(masterPath, savingPath);
                        }
                    } else { // !createFolder(savingPath)
                        log.error("Something went wrong trying to create the directory: " + savingPath);
                    }
                }
            }
            
            log.info("Done.");
        } else {
            log.error("Something went wrong.");
        }


        // do a regular export here
        IExportPlugin export = new ExportDms();
        export.setExportFulltext(true);
        export.setExportImages(true);

        // execute the export and check the success
        boolean success = export.startExport(process);
        if (!success) {
            log.error("Export aborted for process with ID " + process.getId());
        } else {
            log.info("Export executed for process with ID " + process.getId());
        }
        return success;
    }

    private boolean createFolder(String path) {
        File directory = new File(path);
        if (directory.exists()) {
            log.debug("Directory already exisits: " + path);
        } else if (directory.mkdirs()) {
            log.debug("Directory created: " + path);
        } else {
            log.debug("Failed to create directory: " + path);
            return false;
        }
        return true;
    }
    
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

    private void copyImages(String fromPath, String toPath) throws IOException {
        log.debug("copy images from \"" + fromPath + "\" to \"" + toPath + "\".");
        String[] files = new File(fromPath).list();
        for (String filename : files) {
            Path originalPath = Paths.get(fromPath + "/" + filename);
            Path destPath = Paths.get(toPath + "/" + filename);
            Files.copy(originalPath, destPath);
        }
    }

}