package com.igormaznitsa.ideamindmap.editor;

import com.igormaznitsa.ideamindmap.utils.IdeaUtils;
import com.igormaznitsa.ideamindmap.utils.SelectIn;
import com.igormaznitsa.ideamindmap.utils.SwingUtils;
import com.igormaznitsa.mindmap.model.*;
import com.igormaznitsa.mindmap.swing.panel.DialogProvider;
import com.igormaznitsa.mindmap.swing.panel.MindMapListener;
import com.igormaznitsa.mindmap.swing.panel.MindMapPanel;
import com.igormaznitsa.mindmap.swing.panel.ui.AbstractElement;
import com.igormaznitsa.mindmap.swing.panel.utils.Utils;
import com.intellij.codeHighlighting.BackgroundEditorHighlighter;
import com.intellij.ide.structureView.StructureViewBuilder;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.impl.EditorChangeAction;
import com.intellij.openapi.command.impl.UndoManagerImpl;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.fileEditor.*;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.xml.ui.UndoHelper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.dnd.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.geom.Rectangle2D;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.util.ResourceBundle;

import static com.igormaznitsa.mindmap.swing.panel.StandardTopicAttribute.doesContainOnlyStandardAttributes;

public class MindMapDocumentEditor implements DocumentsEditor, MindMapController, MindMapListener, DropTargetListener, DocumentListener {

    private static final Logger LOGGER = Logger.getInstance(MindMapDocumentEditor.class);
    private static final ResourceBundle BUNDLE = java.util.ResourceBundle.getBundle("/i18n/Bundle");

    private static final String FILELINK_ATTR_OPEN_IN_SYSTEM = "useSystem"; //NOI18N

    private final JScrollPane mainScrollPane;
    private final MindMapPanel mindMapPanel;
    private final Project project;
    private final VirtualFile file;
    private final Document[] documents;
    private boolean dragAcceptableType = false;
    private final MindMapPanelControllerImpl panelController;

    public MindMapDocumentEditor(final Project project, final VirtualFile file) {
        this.project = project;
        this.file = file;

        this.panelController = new MindMapPanelControllerImpl(this);

        this.mindMapPanel = new MindMapPanel(panelController);
        this.mindMapPanel.addMindMapListener(this);
        this.mainScrollPane = new JBScrollPane();
        this.mainScrollPane.setViewportView(this.mindMapPanel);
        this.mainScrollPane.setWheelScrollingEnabled(true);
        this.mainScrollPane.setAutoscrolls(true);

        this.mindMapPanel.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                mainScrollPane.getViewport().revalidate();
            }
        });


        final Document document = FileDocumentManager.getInstance().getDocument(this.file);

        this.documents = new Document[]{document};

        this.mindMapPanel.setDropTarget(new DropTarget(this.mindMapPanel, this));

        loadMindMapFromDocument();

        getDocument().addDocumentListener(this);
    }

    @Nullable
    public Document getDocument() {
        return this.documents[0];
    }

    private void saveMindMapToDocument() {
        final Document document = getDocument();
        final MindMap model = this.mindMapPanel.getModel();
        if (document != null && model != null) {
            CommandProcessor.getInstance().executeCommand(getProject(), new Runnable() {
                @Override
                public void run() {
                    ApplicationManager.getApplication().runWriteAction(new Runnable() {
                        @Override
                        public void run() {
                            document.replaceString(0, document.getTextLength(), model.packToString());
                        }
                    });
                }
            }, null, null, document);
        }
    }

    private void loadMindMapFromDocument() {
        final Document document = getDocument();
        if (document != null) {
            final MindMapDocumentEditor theEditor = this;
            CommandProcessor.getInstance().executeCommand(getProject(), new Runnable() {
                @Override
                public void run() {
                    ApplicationManager.getApplication().runReadAction(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                mindMapPanel.setModel(new MindMap(theEditor, new StringReader(document.getText())));
                            } catch (IOException ex) {
                                LOGGER.error("Can't parse mind map", ex);
                                SwingUtils.safeSwing(new Runnable() {
                                    @Override
                                    public void run() {
                                        theEditor.mindMapPanel.setErrorText("Can't load mind map");
                                    }
                                });
                            }
                        }
                    });
                }
            },null,null,document);
        }
    }

    @Override
    public Document[] getDocuments() {
        return this.documents;
    }

    @NotNull
    @Override
    public JComponent getComponent() {
        return this.mainScrollPane;
    }

    @Nullable
    @Override
    public JComponent getPreferredFocusedComponent() {
        return this.mainScrollPane;
    }

    @NotNull
    @Override
    public String getName() {
        return "nb-mm-editor";
    }

    @NotNull
    @Override
    public FileEditorState getState(@NotNull FileEditorStateLevel fileEditorStateLevel) {
        LOGGER.debug("getState()");
        return MindMapFileEditorState.DUMMY;
    }

    @Override
    public void setState(@NotNull FileEditorState fileEditorState) {
        LOGGER.debug("setState()");
    }

    @Override
    public boolean isModified() {
        return FileDocumentManager.getInstance().isFileModified(this.file);
    }

    @Override
    public boolean isValid() {
        return true;
    }

    @Override
    public void selectNotify() {

    }

    @Override
    public void deselectNotify() {

    }

    @Override
    public void addPropertyChangeListener(@NotNull PropertyChangeListener propertyChangeListener) {

    }

    @Override
    public void removePropertyChangeListener(@NotNull PropertyChangeListener propertyChangeListener) {

    }

    @Nullable
    @Override
    public BackgroundEditorHighlighter getBackgroundHighlighter() {
        return null;
    }

    @Nullable
    @Override
    public FileEditorLocation getCurrentLocation() {
        return null;
    }

    @Nullable
    @Override
    public StructureViewBuilder getStructureViewBuilder() {
        return null;
    }

    @Override
    public void dispose() {

    }

    @Nullable
    @Override
    public <T> T getUserData(@NotNull Key<T> key) {
        return null;
    }

    @Override
    public <T> void putUserData(@NotNull Key<T> key, @Nullable T t) {

    }

    @Override
    public boolean canBeDeletedSilently(MindMap mindMap, Topic topic) {
        return topic.getText().isEmpty() && topic.getExtras().isEmpty() && doesContainOnlyStandardAttributes(topic);
    }

    @Override
    public void onMindMapModelChanged(final MindMapPanel mindMapPanel) {
        saveMindMapToDocument();
    }

    @Override
    public void onMindMapModelRealigned(MindMapPanel mindMapPanel, Dimension dimension) {
        this.mainScrollPane.getViewport().revalidate();
    }

    @Override
    public void onEnsureVisibilityOfTopic(MindMapPanel mindMapPanel, final Topic topic) {
        SwingUtilities.invokeLater(new Runnable() {

            @Override
            public void run() {
                if (topic == null) {
                    return;
                }

                final AbstractElement element = (AbstractElement) topic.getPayload();
                if (element == null) {
                    return;
                }

                final Rectangle2D orig = element.getBounds();
                if (orig == null) {
                    return;
                }

                final int GAP = 30;

                final Rectangle bounds = orig.getBounds();
                bounds.setLocation(Math.max(0, bounds.x - GAP), Math.max(0, bounds.y - GAP));
                bounds.setSize(bounds.width + GAP * 2, bounds.height + GAP * 2);

                final JViewport viewport = mainScrollPane.getViewport();
                final Rectangle visible = viewport.getViewRect();

                if (visible.contains(bounds)) {
                    return;
                }

                bounds.setLocation(bounds.x - visible.x, bounds.y - visible.y);

                viewport.scrollRectToVisible(bounds);
            }

        });

    }

    private
    @Nullable
    VirtualFile findRootFolderForEditedFile() {
        final Module module = ModuleUtil.findModuleForFile(this.file, this.project);

        final VirtualFile rootFolder;
        if (module == null) {
            rootFolder = this.project.getBaseDir();
        } else {
            VirtualFile moduleRoot = module.getModuleFile().getParent();
            if (moduleRoot.getName().equals(".idea")) {
                moduleRoot = moduleRoot.getParent();
            }
            rootFolder = moduleRoot;
        }

        return rootFolder;
    }

    @Override
    public void onClickOnExtra(final MindMapPanel source, final int clicks, final Topic topic, final Extra<?> extra) {
        if (clicks > 1) {
            switch (extra.getType()) {
                case FILE: {
                    final MMapURI fileURI = (MMapURI) extra.getValue();
                    final boolean flagOpenFileLinkInSystemViewer = Boolean.parseBoolean(fileURI.getParameters().getProperty(FILELINK_ATTR_OPEN_IN_SYSTEM, "false"));

                    final VirtualFile rootFolder = findRootFolderForEditedFile();
                    final VirtualFile theFile = LocalFileSystem.getInstance().findFileByIoFile(fileURI.asFile(IdeaUtils.vfile2iofile(rootFolder)));

                    if (theFile == null) {
                        // file not found
                        LOGGER.warn("Can't find FileObject for " + fileURI);
                        getDialogProvider().msgError(String.format(BUNDLE.getString("MMDGraphEditor.onClickExtra.errorCanfFindFile"), fileURI.toString()));
                    } else if (VfsUtilCore.isAncestor(rootFolder, theFile, false)) {
                        // inside project
                        if (flagOpenFileLinkInSystemViewer) {
                            SelectIn.SYSTEM.open(this, theFile);
                        } else {
                            SelectIn.IDE.open(this, theFile);
                        }
                    } else {
                        // outside project
                        if (flagOpenFileLinkInSystemViewer) {
                            SelectIn.SYSTEM.open(this, theFile);
                        } else {
                            SelectIn.IDE.open(this, theFile);
                        }
                    }
                }
                break;
                case LINK: {
                    final MMapURI uri = ((ExtraLink) extra).getValue();
                    if (!IdeaUtils.browseURI(uri.asURI(), IdeaUtils.getPreferences().getBoolean("useInsideBrowser", false))) { //NOI18N
                        getDialogProvider().msgError(String.format(BUNDLE.getString("MMDGraphEditor.onClickOnExtra.msgCantBrowse"), uri.toString()));
                    }
                }
                break;
                case NOTE: {
                    editTextForTopic(topic);
                }
                break;
                case TOPIC: {
                    final Topic theTopic = this.mindMapPanel.getModel().findTopicForLink((ExtraTopic) extra);
                    if (theTopic == null) {
                        // not presented
                        getDialogProvider().msgWarn(BUNDLE.getString("MMDGraphEditor.onClickOnExtra.msgCantFindTopic"));
                    } else {
                        // detected
                        this.mindMapPanel.focusTo(theTopic);
                    }
                }
                break;
                default:
                    throw new Error("Unexpected type " + extra);
            }
        }

    }

    @Override
    public void onChangedSelection(MindMapPanel mindMapPanel, Topic[] topics) {
        // do nothing at present
    }

    public DialogProvider getDialogProvider() {
        return this.panelController.getDialogProvider();
    }

    @Override
    public boolean allowedRemovingOfTopics(final MindMapPanel mindMapPanel, final Topic[] topics) {
        boolean topicsNotImportant = true;

        for (final Topic t : topics) {
            topicsNotImportant &= t.canBeLost();
            if (!topicsNotImportant) {
                break;
            }
        }

        final boolean result;

        if (topicsNotImportant) {
            result = true;
        } else {
            result = this.getDialogProvider().msgConfirmYesNo(BUNDLE.getString("MMDGraphEditor.allowedRemovingOfTopics,title"), String.format(BUNDLE.getString("MMDGraphEditor.allowedRemovingOfTopics.message"), topics.length));
        }
        return result;
    }

    private void editTextForTopic(final Topic topic) {
        final ExtraNote note = (ExtraNote) topic.getExtras().get(Extra.ExtraType.NOTE);
        final String result;
        if (note == null) {
            // create new
            result = IdeaUtils.editText(this.project, String.format(BUNDLE.getString("MMDGraphEditor.editTextForTopic.dlfAddNoteTitle"), Utils.makeShortTextVersion(topic.getText(), 16)), ""); //NOI18N
        } else {
            // edit
            result = IdeaUtils.editText(this.project, String.format(BUNDLE.getString("MMDGraphEditor.editTextForTopic.dlgEditNoteTitle"), Utils.makeShortTextVersion(topic.getText(), 16)), note.getValue());
        }
        if (result != null) {
            if (result.isEmpty()) {
                topic.removeExtra(Extra.ExtraType.NOTE);
            } else {
                topic.setExtra(new ExtraNote(result));
            }
            this.mindMapPanel.invalidate();
            this.mindMapPanel.repaint();
            onMindMapModelChanged(this.mindMapPanel);
        }
    }

    protected static boolean checkDragType(final DropTargetDragEvent dtde) {
        boolean result = false;
        for (final DataFlavor fl1 : dtde.getCurrentDataFlavors()) {
            if (fl1.isFlavorJavaFileListType()) {
                result = true;
                break;
            }
        }
        return result;
    }

    @Override
    public void dragEnter(DropTargetDragEvent dtde) {
        this.dragAcceptableType = checkDragType(dtde);
        if (!this.dragAcceptableType) {
            dtde.rejectDrag();
        }
    }

    @Override
    public void dragOver(DropTargetDragEvent dtde) {
        if (acceptOrRejectDragging(dtde)) {
            dtde.acceptDrag(DnDConstants.ACTION_COPY_OR_MOVE);
        } else {
            dtde.rejectDrag();
        }

    }

    @Override
    public void dropActionChanged(DropTargetDragEvent dtde) {

    }

    @Override
    public void dragExit(DropTargetEvent dte) {

    }

    @Override
    public void drop(final DropTargetDropEvent dtde) {
        try {
            final java.util.List<File> files = (java.util.List<File>) dtde.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);
            if (files.isEmpty()) {
                LOGGER.warn("Empty list of files in DnD");
            } else {
                final File file = files.get(0);
                addFiletToElement(file, this.mindMapPanel.findTopicUnderPoint(dtde.getLocation()));
            }
        } catch (Exception ex) {
            LOGGER.error("Can't complete DnD operation for error", ex);
        }
    }

    private void addFiletToElement(final File file, final AbstractElement element) {
        if (element != null) {
            final Topic topic = element.getModel();

            final VirtualFile theFile = VfsUtil.findFileByIoFile(file, true);
            if (theFile != null) {
                final File rootFolder = IdeaUtils.vfile2iofile(findRootFolderForEditedFile());
                final File theFileIo = IdeaUtils.vfile2iofile(theFile);

                final MMapURI theURI = IdeaUtils.getPreferences().getBoolean("makeRelativePathToProject", true) ? new MMapURI(rootFolder, theFileIo, null) : new MMapURI(null, theFileIo, null); //NOI18N

                if (topic.getExtras().containsKey(Extra.ExtraType.FILE)) {
                    if (!getDialogProvider().msgConfirmOkCancel(BUNDLE.getString("MMDGraphEditor.addDataObjectToElement.confirmTitle"), BUNDLE.getString("MMDGraphEditor.addDataObjectToElement.confirmMsg"))) {
                        return;
                    }
                }

                topic.setExtra(new ExtraFile(theURI));
                this.mindMapPanel.invalidate();
                this.mindMapPanel.repaint();
                onMindMapModelChanged(this.mindMapPanel);
            } else {
                LOGGER.warn("Can't find VirtualFile for " + file);
            }
        }
    }

    protected boolean acceptOrRejectDragging(final DropTargetDragEvent dtde) {
        final int dropAction = dtde.getDropAction();

        boolean result = false;

        if (this.dragAcceptableType && (dropAction & DnDConstants.ACTION_COPY_OR_MOVE) != 0 && this.mindMapPanel.findTopicUnderPoint(dtde.getLocation()) != null) {
            result = true;
        }

        return result;
    }

    public MindMapPanel getMindMapPanel() {
        return this.mindMapPanel;
    }

    public Project getProject() {
        return this.project;
    }

    @Override
    public void beforeDocumentChange(final DocumentEvent documentEvent) {

    }

    @Override
    public void documentChanged(final DocumentEvent documentEvent) {
        UndoManager.getInstance(getProject()).undoableActionPerformed(new EditorChangeAction(documentEvent));
        loadMindMapFromDocument();
    }
}