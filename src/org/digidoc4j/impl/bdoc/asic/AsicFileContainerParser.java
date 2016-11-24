/* DigiDoc4J library
*
* This software is released under either the GNU Library General Public
* License (see LICENSE.LGPL).
*
* Note that the only valid version of the LGPL license as far as this
* project is concerned is the original GNU Library General Public License
* Version 2.1, February 1999
*/

package org.digidoc4j.impl.bdoc.asic;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.apache.commons.io.IOUtils;
import org.digidoc4j.Configuration;
import org.digidoc4j.exceptions.TechnicalException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import eu.europa.esig.dss.InMemoryDocument;

public class AsicFileContainerParser extends AsicContainerParser{

  private final static Logger logger = LoggerFactory.getLogger(AsicFileContainerParser.class);
  private ZipFile zipFile;

  public AsicFileContainerParser(String containerPath, Configuration configuration) {
    super(configuration);
    try {
      zipFile = new ZipFile(containerPath);
    } catch (IOException e) {
      logger.error("Error reading container from " + containerPath + " - " + e.getMessage());
      throw new RuntimeException("Error reading container from " + containerPath);
    }
  }

  String getZipComment(ZipFile zipFile) {

    String retStr = null;
    try {
      File file = new File(zipFile.getName());
      int fileLen = (int)file.length();
      FileInputStream in = new FileInputStream(file);
      byte[] buffer = new byte[Math.min(fileLen, 8192)];
      int len;
      in.skip(fileLen - buffer.length);
      if ((len = in.read(buffer)) > 0) {
        byte[] magicDirEnd = {0x50, 0x4b, 0x05, 0x06};
        int buffLen = Math.min(buffer.length, len);
        for (int i = buffLen-magicDirEnd.length-22; i >= 0; i--) {
          boolean isMagicStart = true;
          for (int k=0; k < magicDirEnd.length; k++) {
            if (buffer[i+k] != magicDirEnd[k]) {
              isMagicStart = false;
              break;
            }
          }
          if (isMagicStart) {
          // Magic Start found!
            int commentLen = buffer[i+20] + buffer[i+21]*256;
            int realLen = buffLen - i - 22;
            System.out.println ("ZIP comment found at buffer position " + (i+22) + " with len="+commentLen+", good!");
            if (commentLen != realLen) {
              System.out.println ("WARNING! ZIP comment size mismatch: directory says len is "+
                  commentLen+", but file ends after " + realLen + " bytes!");
            }
            retStr = new String (buffer, i+22, Math.min(commentLen, realLen));
          }
        }
      }
      in.close();

      org.apache.commons.compress.archivers.zip.ZipFile ccZip = new org.apache.commons.compress.archivers.zip.ZipFile(zipFile.getName());

    } catch (IOException e) {
      logger.error("Error reading container from " + zipFile.getName() + " - " + e.getMessage());
      throw new RuntimeException("Error reading container from " + zipFile.getName());
    }

    return retStr;

  }

  @Override
  protected void parseContainer() {
    logger.debug("Parsing zip file");
    try {
      String zipFileComment = getZipComment(zipFile);
      setZipFileComment(zipFileComment);
      parseZipFileManifest();
      Enumeration<? extends ZipEntry> entries = zipFile.entries();
      while (entries.hasMoreElements()) {
        ZipEntry zipEntry = entries.nextElement();
        parseEntry(zipEntry);
      }
    } finally {
      try {
        zipFile.close();
      } catch (Exception ignore) {
      }
    }
  }

  @Override
  protected void extractManifest(ZipEntry entry) {
    extractAsicEntry(entry);
  }

  @Override
  protected InputStream getZipEntryInputStream(ZipEntry entry) {
    try {
      return zipFile.getInputStream(entry);
    } catch (IOException e) {
      logger.error("Error reading data file '" + entry.getName() + "' from the bdoc container: " + e.getMessage());
      throw new TechnicalException("Error reading data file '" + entry.getName() + "' from the bdoc container", e);
    }
  }

  private void parseZipFileManifest() {
    ZipEntry entry = zipFile.getEntry(MANIFEST);
    if (entry == null) {
      return;
    }
    try {
      InputStream manifestStream = getZipEntryInputStream(entry);
      InMemoryDocument manifestFile = new InMemoryDocument(IOUtils.toByteArray(manifestStream));
      parseManifestEntry(manifestFile);
    } catch (IOException e) {
      logger.error("Error parsing manifest file: " + e.getMessage());
      throw new TechnicalException("Error parsing manifest file", e);
    }
  }
}
