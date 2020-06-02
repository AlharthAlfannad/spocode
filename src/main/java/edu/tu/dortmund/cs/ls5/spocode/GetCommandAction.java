package edu.tu.dortmund.cs.ls5.spocode;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiJavaFile;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

import static com.intellij.openapi.project.ProjectManager.getInstance;

public class GetCommandAction extends AnAction {
    public static Project project;
    public static Editor editor;
    public static Document document;
    public static PsiClass psiClass;
    private Execute execute = new Execute();
    public static PsiJavaFile psiJavaFile;
    private BlockingQueue<String> commandQueue;

    @Override

    public void actionPerformed(@NotNull AnActionEvent e) {
        project = getInstance().getOpenProjects()[0]; // e.getProject();
        editor = FileEditorManager.getInstance(project).getSelectedTextEditor(); // e.getRequiredData(CommonDataKeys.EDITOR);
        document = editor.getDocument();
        psiJavaFile = (PsiJavaFile) PsiDocumentManager.getInstance(project).getPsiFile(document);
        psiClass = psiJavaFile.getClasses()[0];
        boolean startThread = false;
        synchronized (GetCommandAction.class) {
            if (commandQueue == null) {
                startThread = true;
                commandQueue = new ArrayBlockingQueue<>(100);
            }
        }
        if (startThread) {
            new Thread(() -> {
                while (true) {
                    String command;
                    try {
                        command = commandQueue.take();
                    } catch (InterruptedException ex) {
                        throw new RuntimeException(ex);
                    }
                    ApplicationManager.getApplication().runReadAction(() -> {
                        try {
                            execute.setCommand(command);
                        } catch (Exception ex) {
                            ex.printStackTrace();
                        }
                    });
                }
            }).start();
        }
        new Thread(() -> {
            try {
                Recognition.streamingMicRecognize(commandQueue);
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        }).start();
    }

    /*


        public void actionPerformed(@NotNull AnActionEvent e) {
            project = e.getProject();
            editor = e.getRequiredData(CommonDataKeys.EDITOR);
            document = editor.getDocument();
            psiJavaFile = (PsiJavaFile) PsiDocumentManager.getInstance(project).getPsiFile(document);
            psiClass =  psiJavaFile.getClasses()[0];
            //execute.setAnActionEvent(e);
            String command = Messages.showInputDialog(project,"command", "command", Messages.getQuestionIcon());
            execute.setCommand(command);
        }


    */
    @Override
    public void update(@NotNull final AnActionEvent e) {
        // Get required data keys
        final Project project = e.getProject();
        final Editor editor = e.getData(CommonDataKeys.EDITOR);
        // Set visibility and enable only in case of existing project and editor and if a selection exists
        e.getPresentation().setEnabledAndVisible(project != null && editor != null);
    }
}
