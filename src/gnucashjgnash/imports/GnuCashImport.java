/*
 * Copyright 2017 Albert Santos.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
*/
package gnucashjgnash.imports;

import jgnash.engine.DataStoreType;
import jgnash.engine.Engine;
import jgnash.engine.EngineFactory;
import jgnash.util.FileUtils;
import org.xml.sax.*;

import gnucashjgnash.GnuCashConvertUtil;
import gnucashjgnash.NoticeTree;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipException;

public class GnuCashImport {
    private static final Logger LOG = Logger.getLogger(GnuCashImport.class.getName());

    private String errorMsg;
    private NoticeTree warningNoticeTree;
    private static SAXParserFactory parserFactory;

    public GnuCashImport() {

    }

    public String getErrorMsg() {
        return errorMsg;
    }
    
    public NoticeTree getWarningNoticeTree() {
    	return this.warningNoticeTree;
    }


    public interface StatusCallback {
        void updateStatus(long progress, long total, String statusMsg);
    }

    
    /**
     * The main entry point.
     * @param gnuCashFileName
     * @param jGnashFileName
     * @param dataStoreType
     * @param statusCallback
     * @return	<code>false</code> if failed.
     */
    public boolean convertGnuCashToJGnash(final String gnuCashFileName, final String jGnashFileName, final DataStoreType dataStoreType,
                                          StatusCallback statusCallback) {
        errorMsg = null;
        String password = "";

        InputStream gnuCashInputStream;
        try {
            gnuCashInputStream = getUncompressedInputStream(gnuCashFileName);
        }
        catch (IOException e) {
            errorMsg = GnuCashConvertUtil.getString("Message.Error.FileOpenError", gnuCashFileName, e.getLocalizedMessage());
            return false;
        }

        EngineFactory.closeEngine(EngineFactory.DEFAULT);

        boolean isSuccess = false;

        // If the file exists, rename the old file. If we fail, we can put it back.
        String archivedFileName = null;
        if (Files.exists(Paths.get(jGnashFileName))) {
            archivedFileName = makeUniqueBackupFileName(jGnashFileName);
            File jGnashFile = new File(jGnashFileName);
            if (!jGnashFile.renameTo(new File(archivedFileName))) {
                String msg = "Could not rename the existing file '" + jGnashFileName + "' to '" + archivedFileName + "'.";
                LOG.warning(msg);
                System.out.println(msg);

                // TODO: Do we want to ask to continue?

                archivedFileName = null;
            }
        }

        try {
            Engine engine;
            try {

                Files.createDirectories(Paths.get(jGnashFileName).getParent());

                if (statusCallback != null) {
                		statusCallback.updateStatus(0, 100, GnuCashConvertUtil.getString("Message.Status.InitializingJGnashFile", jGnashFileName));
                }
                engine = EngineFactory.bootLocalEngine(jGnashFileName, EngineFactory.DEFAULT, password.toCharArray(), dataStoreType);

            } catch (IOException e) {
                this.errorMsg = GnuCashConvertUtil.getString("Message.Error.FileCreateError", jGnashFileName, e.getLocalizedMessage());
                return false;
            }

            if (!importGnuCashXML(gnuCashInputStream, gnuCashFileName, jGnashFileName, engine, statusCallback)) {
                return false;
            }

            archivedFileName = null;
            isSuccess = true;
        }
        catch (Exception e) {
                LOG.severe("Uncaught Exception: " + e.getLocalizedMessage());
        }
        finally {
            if (!isSuccess) {
                    LOG.severe("Import failed, shtting down engine.");
                    
                EngineFactory.closeEngine(EngineFactory.DEFAULT);
                EngineFactory.deleteDatabase(jGnashFileName);

                if (archivedFileName != null) {
                    // We apparently failed, rename the archived file to the original name.
                    File jGnashFile = new File(jGnashFileName);
                    if (jGnashFile.exists()) {
                        jGnashFile.delete();
                    }
                    File archivedFile = new File(archivedFileName);
                    if (!archivedFile.renameTo(jGnashFile)) {
                        String msg = "Could not rename the backup file '" + archivedFileName + "' back to '" + jGnashFileName + "'.";
                        LOG.warning(msg);
                        System.out.println(msg);
                    }
                }
            }
        }

        return true;
    }

    public static InputStream getUncompressedInputStream(final String fileName) throws IOException {
        try {
            final FileInputStream inputStream = new FileInputStream(fileName);
            InputStream gZipInputStream = new GZIPInputStream(inputStream);
            return gZipInputStream;
        } catch (ZipException e) {
            // We need a new input stream because we need to reset the read position to the beginning.
            return new FileInputStream(fileName);
        }
    }

    public static String makeUniqueBackupFileName(String fileName) {
        String extension = FileUtils.getFileExtension(fileName);
        if (extension.charAt(0) != '.') {
            extension = '.' + extension;
        }

        String baseName = FileUtils.stripFileExtension(fileName);
        baseName += "-original";
        int number = 1;

        String newFileName = baseName + extension;
        File file = new File(newFileName);
        while (file.exists()) {
            newFileName = baseName + "-" + number + extension;
            ++number;
            file = new File(newFileName);
        }

        return newFileName;
    }

    protected boolean importGnuCashXML(final InputStream inputStream, final String gnuCashFileName, final String jGnashFileName,
                                       final Engine engine, final StatusCallback statusCallback) {
        Logger jGnashEngineLogger = Logger.getLogger("jgnash.engine.Engine");
        Level savedEngineLoggingLevel = (jGnashEngineLogger != null) ? jGnashEngineLogger.getLevel() : Level.ALL;

        Logger jGnashTransactionFactoryLogger = Logger.getLogger("jgnash.engine.TransactionFactory");
        Level savedTransactionFactoryLoggingLevel = (jGnashTransactionFactoryLogger != null) ? jGnashTransactionFactoryLogger.getLevel() : Level.ALL;

        Logger jGnashBinaryContainerLogger = Logger.getLogger("jgnash.engine.xstream.BinaryContainer");
        Level savedBinaryContainerLoggingLevel = (jGnashBinaryContainerLogger != null) ? jGnashBinaryContainerLogger.getLevel() : Level.ALL;
        try {
            // TEST!!!
            if (jGnashEngineLogger != null) {
                jGnashEngineLogger.setLevel(Level.WARNING);
            }
            if (jGnashTransactionFactoryLogger != null) {
                jGnashTransactionFactoryLogger.setLevel(Level.WARNING);
            }
            if (jGnashBinaryContainerLogger != null) {
            	jGnashBinaryContainerLogger.setLevel(Level.WARNING);
            }
            
            if (statusCallback != null) {
            		statusCallback.updateStatus(1, 100, GnuCashConvertUtil.getString("Message.Status.ParsingGnuCashFile", gnuCashFileName));
            }
            
            if (parserFactory == null) {
                parserFactory = SAXParserFactory.newInstance();
                parserFactory.setNamespaceAware(true);
            }
    
            SAXParser saxParser;
            XMLReader xmlReader;
            try {
                saxParser = parserFactory.newSAXParser();
                xmlReader = saxParser.getXMLReader();
            } catch (ParserConfigurationException e) {
                this.errorMsg = GnuCashConvertUtil.getString("Message.Error.ParserConfigurationException", gnuCashFileName, e.getLocalizedMessage());
                return false;
            } catch (SAXException e) {
                this.errorMsg = GnuCashConvertUtil.getString("Message.Error.ParserCreationError", gnuCashFileName, e.getLocalizedMessage());
                return false;
            }
    
            GnuCashToJGnashContentHandler contentHandler = new GnuCashToJGnashContentHandler(engine, statusCallback);
            this.warningNoticeTree = contentHandler.warningNoticeTree;
            xmlReader.setContentHandler(contentHandler);
            try {
    
                xmlReader.parse(new InputSource(inputStream));
                LOG.info("Parsing of '" + gnuCashFileName + "' completed.");
    
                if (!contentHandler.generateJGnashDatabase()) {
                    this.errorMsg = contentHandler.getErrorMsg();
                    return false;
                }
                else {
                    LOG.info("'" + gnuCashFileName + "' imported as '" + jGnashFileName);
                }
    
            } catch (IOException e) {
                this.errorMsg = GnuCashConvertUtil.getString("Message.Error.FileReadError", gnuCashFileName, e.getLocalizedMessage());
                return false;
            } catch (SAXException e) {
                this.errorMsg = GnuCashConvertUtil.getString("Message.Error.XMLFormatError", gnuCashFileName, e.getLocalizedMessage());
                return false;
            }
        
        } finally {
            if (jGnashEngineLogger != null) {
                jGnashEngineLogger.setLevel(savedEngineLoggingLevel);
            }
            if (jGnashTransactionFactoryLogger != null) {
                jGnashTransactionFactoryLogger.setLevel(savedTransactionFactoryLoggingLevel);
            }
            if (jGnashBinaryContainerLogger != null) {
            	jGnashBinaryContainerLogger.setLevel(savedBinaryContainerLoggingLevel);
            }
        }

        return true;
    }


}
