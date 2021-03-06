/* 
 * Copyright (C) 2018 Igor Maznitsa.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA 02110-1301  USA
 */
package com.igormaznitsa.sciareto.ui.tree;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nonnull;
import com.igormaznitsa.meta.common.utils.Assertions;
import javax.annotation.Nullable;
import org.apache.commons.io.FileUtils;
import com.igormaznitsa.meta.annotation.MustNotContainNull;
import com.igormaznitsa.mindmap.model.MMapURI;
import com.igormaznitsa.mindmap.model.MindMap;
import com.igormaznitsa.mindmap.model.logger.Logger;
import com.igormaznitsa.mindmap.model.logger.LoggerFactory;
import com.igormaznitsa.sciareto.Context;
import com.igormaznitsa.sciareto.preferences.PrefUtils;
import com.igormaznitsa.sciareto.ui.MapUtils;
import com.igormaznitsa.sciareto.ui.SystemUtils;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicBoolean;

public class NodeProject extends NodeFileOrFolder implements NodeFileOrFolder.Cancelable {

  private static final Logger LOGGER = LoggerFactory.getLogger(NodeProject.class);

  private volatile File folder = null;
  private volatile boolean knowledgeFolderPresented;
  private final AtomicBoolean loading = new AtomicBoolean();

  public NodeProject(@Nonnull final NodeProjectGroup group, @Nonnull final File folder) throws IOException {
    super(group, true, folder.getName(), PrefUtils.isShowHiddenFilesAndFolders(), !Files.isWritable(folder.toPath()));
    this.folder = folder;
    this.knowledgeFolderPresented = new File(folder, Context.KNOWLEDGE_FOLDER).isDirectory();
    this.loading.set(true);
  }

  @Override
  public boolean isLoading() {
    return this.loading.get();
  }

  public boolean hasKnowledgeFolder() {
    return this.knowledgeFolderPresented;
  }

  @Override
  public void setName(@Nonnull final String name) throws IOException {
    this.name = name;
    this.folder = new File(folder.getParentFile(), name);
    reloadSubtree(PrefUtils.isShowHiddenFilesAndFolders(), this);
  }

  @Override
  @Nullable
  public File makeFileForNode() {
    return this.folder;
  }

  @Nonnull
  public File getFolder() {
    return this.folder;
  }

  public void setFolder(@Nonnull final File folder) throws IOException {
    Assertions.assertTrue("Must be directory", folder.isDirectory()); //NOI18N
    this.folder = folder;
    reloadSubtree(PrefUtils.isShowHiddenFilesAndFolders(), this);
  }

  @Nonnull
  public NodeProjectGroup getGroup() {
    return (NodeProjectGroup) this.parent;
  }

  @Nonnull
  @MustNotContainNull
  public List<File> findAffectedFiles(@Nonnull final File changedFile) {
    final File baseFolder = makeFileForNode();
    final boolean folder = changedFile.isDirectory();

    final List<File> result = new ArrayList<>();
    for (final File mindMapFile : FileUtils.listFiles(baseFolder, new String[]{"mmd", "MMD"}, true)) { //NOI18N
      try {
        final MindMap map = new MindMap(null, new StringReader(FileUtils.readFileToString(mindMapFile, "UTF-8"))); //NOI18N
        if (!MapUtils.findTopicsRelatedToFile(baseFolder, changedFile, map).isEmpty()) {
          result.add(mindMapFile);
        }
      } catch (IOException ex) {
        LOGGER.error("Can't process mind map file", ex); //NOI18N
      }
    }
    return result;
  }

  @Nonnull
  @MustNotContainNull
  public List<File> deleteAllLinksToFile(@Nonnull @MustNotContainNull final List<File> listOfFilesToProcess, @Nonnull final File fileToRemove) {
    final List<File> affectedFiles = new ArrayList<>();

    final File baseFolder = makeFileForNode();
    final MMapURI fileURI = new MMapURI(baseFolder, fileToRemove, null);

    for (final File file : listOfFilesToProcess) {
      if (file.isFile()) {
        try {
          final MindMap map = new MindMap(null, new StringReader(FileUtils.readFileToString(file, "UTF-8"))); //NOI18N
          if (map.deleteAllLinksToFile(baseFolder, fileURI)) {
            SystemUtils.saveUTFText(file, map.packToString());
            affectedFiles.add(file);
          }
        } catch (IOException ex) {
          LOGGER.error("Can't process mind map file", ex); //NOI18N
        }
      }
    }

    return affectedFiles;
  }

  @Override
  public boolean isCanceled() {
    return Thread.currentThread().isInterrupted() || this.loading.get() == false;
  }

  @Override
  public void cancel() {
    LOGGER.info("Canceling prject " + this.toString());
    this.loading.set(false);
  }

  @Override
  public void reloadSubtree(final boolean addHiddenFilesAndFolders, @Nonnull final Cancelable cancelable) throws IOException {
    final long startTime = System.currentTimeMillis();
    super.reloadSubtree(addHiddenFilesAndFolders, cancelable);
    LOGGER.info(String.format("Project %s reloaded, spent %d ms", this.toString(), System.currentTimeMillis() - startTime));
  }

  @Override
  @Nonnull
  @MustNotContainNull
  protected List<NodeFileOrFolder> _reloadSubtree(final boolean showHiddenFiles, @Nonnull final Cancelable cancelable) throws IOException {
    this.loading.set(true);
    
    this.getGroup().notifyProjectStateChanged(this);
    try {
      final List<NodeFileOrFolder> result = new ArrayList<>(super._reloadSubtree(showHiddenFiles, cancelable));
      final File knowledgeFolder = new File(this.folder, Context.KNOWLEDGE_FOLDER);
      this.knowledgeFolderPresented = knowledgeFolder.isDirectory();
     
      if (!showHiddenFiles && this.knowledgeFolderPresented) {
        boolean knowledgeFolderAdded = false;

        for (final NodeFileOrFolder f : result) {
          if (!cancelable.isCanceled() && f.isProjectKnowledgeFolder()) {
            knowledgeFolderAdded = true;
            break;
          }
        }

        if (!cancelable.isCanceled() && !knowledgeFolderAdded) {
          result.add(new NodeFileOrFolder(this, knowledgeFolder.isDirectory(), knowledgeFolder.getName(), showHiddenFiles, !Files.isWritable(knowledgeFolder.toPath())));
          Collections.sort(result, this);
        }
      }
      return result;
    } finally {
      try {
        if (!cancelable.isCanceled()) {
          this.loading.set(false);
          this.getGroup().notifyProjectStateChanged(this);
        }
      } finally {
        this.loading.set(false);
      }
    }
  }

  @Nonnull
  @MustNotContainNull
  public List<File> replaceAllLinksToFile(@Nonnull @MustNotContainNull final List<File> listOfFilesToProcess, @Nonnull final File oldFile, @Nonnull final File newFile) {
    final List<File> affectedFiles = new ArrayList<>();

    final File baseFolder = makeFileForNode();
    final MMapURI oldFileURI = new MMapURI(baseFolder, oldFile, null);
    final MMapURI newFileURI = new MMapURI(baseFolder, newFile, null);

    for (final File file : listOfFilesToProcess) {
      if (file.isFile()) {
        try {
          final MindMap map = new MindMap(null, new StringReader(FileUtils.readFileToString(file, "UTF-8"))); //NOI18N
          if (map.replaceAllLinksToFile(baseFolder, oldFileURI, newFileURI)) {
            SystemUtils.saveUTFText(file, map.packToString());
            affectedFiles.add(file);
          }
        } catch (IOException ex) {
          LOGGER.error("Can't process mind map file", ex); //NOI18N
        }
      }
    }

    return affectedFiles;
  }
}
