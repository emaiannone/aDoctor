package adoctor.presentation.dialog;

import adoctor.application.bean.proposal.MethodProposal;
import adoctor.application.bean.smell.MethodSmell;
import com.intellij.ide.SaveAndSyncHandlerImpl;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.TransactionGuard;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ex.ProjectManagerEx;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileEvent;
import com.intellij.openapi.vfs.VirtualFileManager;

import java.util.ArrayList;

public class CoreDriver implements StartDialog.StartCallback,
        AboutDialog.AboutCallback,
        AnalysisDialog.AnalysisCallback,
        AbortedDialog.AbortedCallback,
        NoSmellDialog.NoSmellCallback,
        SmellDialog.SmellCallback,
        RefactoringDialog.RefactoringCallback,
        SuccessDialog.SuccessCallback,
        FailureDialog.FailureCallback {

    private Project project;
    private boolean[] selections;
    private String targetPackage;
    private ArrayList<MethodSmell> methodSmells;

    public CoreDriver(Project project) {
        this.project = project;
        this.methodSmells = null;
    }

    public void start() {
        StartDialog.show(this, project);
    }

    private void launchAnalysis() {
        // Save all files in the current project before starting the analysis
        project.save();
        FileDocumentManager.getInstance().saveAllDocuments();
        ProjectManagerEx.getInstanceEx().blockReloadingProjectOnExternalChanges();

        AnalysisDialog.show(this, project, selections, targetPackage);
    }

    ////////////////StartDialog///////////////
    @Override
    public void startAnalysis(StartDialog startDialog, boolean[] selections, String targetPackage) {
        startDialog.dispose();
        this.selections = selections;
        this.targetPackage = targetPackage;
        launchAnalysis();
    }

    @Override
    public void startAbout(StartDialog startDialog) {
        startDialog.dispose();
        AboutDialog.show(this);
    }

    @Override
    public void startQuit(StartDialog startDialog) {
        startDialog.dispose();
    }

    /////////////////AboutDialog///////////////
    @Override
    public void aboutBack(AboutDialog aboutDialog) {
        aboutDialog.dispose();
        start();
    }

    ////////////AnalysisDialog/////////////////
    @Override
    public void analysisAbort(AnalysisDialog analysisDialog) {
        analysisDialog.dispose();
        AbortedDialog.show(this);
    }

    @Override
    public void analysisDone(AnalysisDialog analysisDialog, ArrayList<MethodSmell> methodSmells) {
        this.methodSmells = methodSmells;
        analysisDialog.dispose();
        if (methodSmells == null || methodSmells.size() == 0) {
            NoSmellDialog.show(this);
        } else {
            SmellDialog.show(this, methodSmells, selections);
        }
    }

    ////////////AbortedDialog/////////////////
    @Override
    public void abortedQuit(AbortedDialog abortedDialog) {
        abortedDialog.dispose();
    }

    @Override
    public void abortedRestart(AbortedDialog abortedDialog) {
        abortedDialog.dispose();
        AnalysisDialog.show(this, project, selections, targetPackage);
    }


    //////////////NoSmellDialog/////////////
    @Override
    public void noSmellQuit(NoSmellDialog noSmellDialog) {
        noSmellDialog.dispose();
    }

    /////////////SmellDialog//////////////
    @Override
    public void smellApply(SmellDialog smellDialog, MethodProposal methodProposal) {
        smellDialog.dispose();
        RefactoringDialog.show(this, methodProposal, selections);
    }

    @Override
    public void smellBack(SmellDialog smellDialog) {
        smellDialog.dispose();
        start();
    }

    @Override
    public void smellQuit(SmellDialog smellDialog) {
        smellDialog.dispose();
    }

    /////////////RefactoringDialog//////////////
    @Override
    public void refactoringDone(RefactoringDialog refactoringDialog, boolean result) {
        refactoringDialog.dispose();

        // Refreshes the Editor in order to reflect the changes to the files
        TransactionGuard.getInstance().assertWriteSafeContext(ModalityState.NON_MODAL);
        SaveAndSyncHandlerImpl.getInstance().refreshOpenFiles();
        VirtualFileManager.getInstance().refreshWithoutFileWatcher(false);
        ProjectManagerEx.getInstanceEx().unblockReloadingProjectOnExternalChanges();

        if (!result) {
            FailureDialog.show(this);
        } else {
            // Updates the editor with the changes made to the files
            //TODO Funziona?
            Document[] documents = FileDocumentManager.getInstance().getUnsavedDocuments();
            for (Document document : documents) {
                FileDocumentManager.getInstance().reloadFromDisk(document);
            }

            SuccessDialog.show(this);
        }
    }

    @Override
    public void refactoringQuit(RefactoringDialog refactoringDialog) {
        refactoringDialog.dispose();
    }

    /////////////SuccessDialog//////////////
    @Override
    public void successAnalyze(SuccessDialog successDialog) {
        successDialog.dispose();
        launchAnalysis();
    }

    @Override
    public void successQuit(SuccessDialog successDialog) {
        successDialog.dispose();
    }

    @Override
    public void failureBack(FailureDialog failureDialog) {
        failureDialog.dispose();
        SmellDialog.show(this, methodSmells, selections);
    }

    @Override
    public void failureQuit(FailureDialog failureDialog) {
        failureDialog.dispose();
    }
}
