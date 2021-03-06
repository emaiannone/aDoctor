package adoctor.presentation.dialog;

import adoctor.application.proposal.ProposalDriver;
import adoctor.application.proposal.proposers.*;
import adoctor.application.proposal.undo.Undo;
import adoctor.application.smell.ClassSmell;
import com.intellij.diff.DiffContentFactory;
import com.intellij.diff.DiffManager;
import com.intellij.diff.DiffRequestPanel;
import com.intellij.diff.contents.DocumentContent;
import com.intellij.diff.requests.SimpleDiffRequest;
import com.intellij.diff.util.DiffUserDataKeysEx;
import com.intellij.ide.highlighter.JavaClassFileType;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.uiDesigner.core.GridConstraints;
import org.eclipse.jface.text.BadLocationException;

import javax.swing.*;
import javax.swing.plaf.basic.BasicComboBoxRenderer;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class SmellDialog extends AbstractDialog {
    private static final String TITLE = "aDoctor - Smell Instances";
    private static final String extendedHTML = "" +
            "<html>" +
            "<body>" +
            "<h1>" + "%s" + "</h1>" +
            "<h2>Affected Class</h2>" + "%s" +
            "<h2>Description</h2>" + "%s" +
            "</body>" +
            "</html>";

    private SmellCallback smellCallback;
    private Project project;
    private List<ClassSmell> classSmells;
    private ProposalDriver proposalDriver;
    private ClassSmell selectedSmell;
    private Undo undo;
    private DiffRequestPanel diffRequestPanel;

    private JPanel contentPane;
    private JPanel panelList;
    private JLabel labelNumber;
    private JComboBox<ClassSmell> boxSmell;
    private JTextPane paneDetails;
    private JPanel panelMain;
    private JLabel labelError;
    private JButton buttonApply;
    private JButton buttonBack;
    private JButton buttonUndo;
    private JSplitPane splitPane;

    private SmellDialog(SmellCallback smellCallback, Project project, List<ClassSmell> classSmells, List<Boolean> selections, boolean undoExists) {
        this.smellCallback = smellCallback;
        this.project = project;
        this.classSmells = classSmells;
        init(selections, undoExists);
    }

    public static void show(SmellCallback smellCallback, Project project, List<ClassSmell> smellMethodList, List<Boolean> selections, boolean undoExists) {
        SmellDialog smellDialog = new SmellDialog(smellCallback, project, smellMethodList, selections, undoExists);
        smellDialog.showInCenter();
    }

    private void init(List<Boolean> selections, boolean undoExists) {
        super.init(contentPane, TITLE, buttonApply);

        ArrayList<ClassSmellProposer> classSmellProposers = new ArrayList<>();
        if (selections.get(0)) {
            classSmellProposers.add(new DWProposer());
        }
        if (selections.get(1)) {
            classSmellProposers.add(new ERBProposer());
        }
        if (selections.get(2)) {
            classSmellProposers.add(new IDSProposer());
        }
        if (selections.get(3)) {
            classSmellProposers.add(new ISProposer());
        }
        if (selections.get(4)) {
            classSmellProposers.add(new LTProposer());
        }
        if (selections.get(5)) {
            classSmellProposers.add(new MIMProposer());
        }
        proposalDriver = new ProposalDriver(classSmellProposers);
        selectedSmell = null;
        undo = null;

        int loc = splitPane.getDividerLocation();
        splitPane.setLeftComponent(null);
        splitPane.setRightComponent(null);
        splitPane.setLeftComponent(panelList);
        splitPane.setRightComponent(panelMain);
        //splitPane.setDividerLocation(loc);
        splitPane.setDividerLocation(270);

        // The smell list
        boxSmell.setRenderer(new SmellRenderer());
        boxSmell.removeAllItems();
        // TODO Medium Rearrange in a certain way?
        for (ClassSmell classSmell : classSmells) {
            boxSmell.addItem(classSmell);
        }
        labelNumber.setText("" + classSmells.size());
        boxSmell.addActionListener(e -> onSelectItem());
        buttonApply.addActionListener(e -> onApply());
        buttonBack.addActionListener(e -> onBack());
        boxSmell.setSelectedIndex(0); // Select the first smell of the list

        // The undo button
        buttonUndo.setVisible(false);
        //Uncomment when the feature will be implemented: buttonUndo.setVisible(undoExists);
        buttonUndo.addActionListener(e -> onUndo());

        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                onQuit();
            }
        });
    }

    private void updateDetails() {
        selectedSmell = classSmells.get(boxSmell.getSelectedIndex());

        // Updates the extended description
        String smellName = selectedSmell.getName();
        String smellClass = selectedSmell.getClassBean().getTypeDeclaration().getName().toString();
        String smellDescription = selectedSmell.getDescription();
        String text = String.format(extendedHTML, smellName, smellClass, smellDescription);
        paneDetails.setText(text);

        // Clear the main panel
        panelMain.removeAll();
        GridConstraints gc = new GridConstraints();
        gc.setHSizePolicy(GridConstraints.SIZEPOLICY_CAN_GROW | GridConstraints.SIZEPOLICY_CAN_SHRINK);
        gc.setFill(GridConstraints.FILL_BOTH);
        try {
            // Compute the proposal of the selected smell and then show the diff if no errors
            undo = proposalDriver.computeProposal(selectedSmell);

            // Prepare a new Diff panel
            if (diffRequestPanel != null) {
                diffRequestPanel.dispose();
            }
            diffRequestPanel = createDiffRequestPanel(undo);
            if (diffRequestPanel == null) {
                panelMain.add(labelError, gc);
            } else {
                JComponent panelDiffComponent = diffRequestPanel.getComponent();
                /*
                int preferredWidth = 768;
                int preferredHeight = 768;
                panelDiffComponent.setPreferredSize(new Dimension(preferredWidth, preferredHeight));*/
                panelMain.add(panelDiffComponent, gc);
            }
        } catch (IOException | BadLocationException e) {
            panelMain.add(labelError, gc);
        }
        pack();
        setLocationRelativeTo(null);
    }

    private DiffRequestPanel createDiffRequestPanel(Undo undo) {
        VirtualFile vf = LocalFileSystem.getInstance().findFileByIoFile(selectedSmell.getClassBean().getSourceFile());
        if (undo == null || vf == null) {
            return null;
        }
        DocumentContent currentDocumentContent = DiffContentFactory.getInstance().createDocument(project, vf);
        if (currentDocumentContent == null) {
            return null;
        }
        String validDocumentContent = undo.getDocument().get().replaceAll("(\r\n|\r)", "\n");
        Document proposedDocument = EditorFactory.getInstance().createDocument(validDocumentContent);
        DocumentContent proposedDocumentContent = DiffContentFactory.getInstance().create(proposedDocument.getText(),
                JavaClassFileType.INSTANCE);
        SimpleDiffRequest request = new SimpleDiffRequest("Diff Panel", currentDocumentContent,
                proposedDocumentContent, "Current Code", "Proposed Code");

        // DiffPanel preparation
        diffRequestPanel = DiffManager.getInstance().createRequestPanel(project, () -> diffRequestPanel.setRequest(null), null);
        diffRequestPanel.putContextHints(DiffUserDataKeysEx.LANGUAGE, JavaLanguage.INSTANCE);
        diffRequestPanel.putContextHints(DiffUserDataKeysEx.FORCE_READ_ONLY, true);
        diffRequestPanel.setRequest(request);
        return diffRequestPanel;
    }

    private void onSelectItem() {
        updateDetails();
    }

    private void onApply() {
        diffRequestPanel.dispose();
        smellCallback.smellApply(this, selectedSmell, undo);
    }

    private void onBack() {
        diffRequestPanel.dispose();
        smellCallback.smellBack(this);
    }

    private void onQuit() {
        diffRequestPanel.dispose();
        smellCallback.smellQuit(this);
    }

    private void onUndo() {
        diffRequestPanel.dispose();
        int dialogResult = JOptionPane.showConfirmDialog(this, "Are you sure to undo the last refactoring?", "Warning", JOptionPane.YES_NO_OPTION);
        if (dialogResult == 0) {
            smellCallback.smellUndo(this);
        }
    }

    private static class SmellRenderer extends BasicComboBoxRenderer {
        private static final String baseHTML = "" +
                "<html>" +
                "<body>" +
                "<h2>#" + "%d " + "%s" + "</h2>" + "%s" +
                "</body>" +
                "</html>";

        @Override
        public Component getListCellRendererComponent(JList list, Object value, int index,
                                                      boolean isSelected, boolean cellHasFocus) {
            JLabel label = new JLabel();
            ClassSmell classSmell = (ClassSmell) value;
            label.setOpaque(true);
            if (isSelected) {
                label.setBackground(list.getSelectionBackground());
                label.setForeground(list.getSelectionForeground());
            } else {
                label.setBackground(list.getBackground());
                label.setForeground(list.getForeground());
            }

            int actualIndex = index;
            if (actualIndex == -1) {
                actualIndex = list.getSelectedIndex();
            }
            actualIndex++;

            String smellName = classSmell.getName();
            String smellClass = classSmell.getClassBean().getTypeDeclaration().getName().toString();
            String text = String.format(baseHTML, actualIndex, smellName, smellClass);
            label.setText(text);
            return label;
        }
    }

    public interface SmellCallback {
        void smellApply(SmellDialog smellDialog, ClassSmell targetSmell, Undo undo);

        void smellBack(SmellDialog smellDialog);

        void smellQuit(SmellDialog smellDialog);

        void smellUndo(SmellDialog smellDialog);
    }
}
