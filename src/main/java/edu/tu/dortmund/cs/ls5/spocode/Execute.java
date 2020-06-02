package edu.tu.dortmund.cs.ls5.spocode;

import com.intellij.codeInsight.actions.FileInEditorProcessor;
import com.intellij.codeInsight.actions.LastRunReformatCodeOptionsProvider;
import com.intellij.codeInsight.actions.ReformatCodeRunOptions;
import com.intellij.codeInsight.actions.TextRangeType;
import com.intellij.codeInsight.generation.GenerationInfo;
import com.intellij.codeInsight.generation.PsiFieldMember;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.impl.search.AllClassesSearchExecutor;
import com.intellij.psi.scope.util.PsiScopesUtil;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.refactoring.listeners.RefactoringElementListener;
import com.intellij.refactoring.rename.RenameUtil;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.containers.hash.HashMap;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static edu.tu.dortmund.cs.ls5.spocode.ClassResolver.*;


public class Execute {
    private Command command = null;

    public void setCommand(String command) {
        if (command != null) {
            String[] commands = command.split("then");
            for (String s : commands) {
                Command c = new Command(s);
                if (c.isCommand()) {
                    this.command = c;
                    executeCommand();
                }

            }
        }
    }

    private boolean executeCommand() {
        return createClass() || initializeVariable() || addAttribute() || openClass() || renameClass() || renameVariable() ||
                declareVariable() || addMethod() || goToLine(-1) || deleteLine() || removeLine() || undo() || redo() ||
                formatLines() || addGetter() || addSetter() || CommitAndPush() || addIf() || addElseIf() || addElse() || addWhile()
                || addReturn() || increment() || callMethod() || assignVariable();
    }

    private boolean increment() {
        return false;
    }

    private boolean formatLines() {
        // cmd: format <<lines>>
        if (command.getKey().equals("format")) {
            LastRunReformatCodeOptionsProvider provider = new LastRunReformatCodeOptionsProvider(PropertiesComponent.getInstance());
            ReformatCodeRunOptions currentRunOptions = provider.getLastRunOptions(GetCommandAction.psiJavaFile);
            currentRunOptions.setProcessingScope(TextRangeType.WHOLE_FILE);
            ApplicationManager.getApplication().invokeLater(() ->
                    new FileInEditorProcessor(GetCommandAction.psiJavaFile, GetCommandAction.editor, currentRunOptions).processCode()
            );
            return true;
        }
        return false;
    }

    private boolean addGetter() {
        // cmd: add getter -> generate all getters
        // cmd: add getter for {att} -> generate getter for the attribute att
        if (command.getKey().equals("add") && command.getTarget().equals("getter")) {
            if (command.hasParameters() && command.getParameters().get(0).equals("for")) {
                String attributeName = variableNameFromIndex(command.getParameters(), 1);
                ApplicationManager.getApplication().invokeLater(() -> WriteCommandAction.runWriteCommandAction(GetCommandAction.project, () -> {
                    PsiField[] fields = GetCommandAction.psiClass.getFields();
                    for (PsiField field : fields) {
                        if (field.getName().equals(attributeName)) {
                            GenerationInfo info = new PsiFieldMember(field).generateGetter();
                            if (info != null) {
                                info.insert(GetCommandAction.psiClass, GetCommandAction.psiClass.getLastChild(), true);
                            }
                            break;
                        }
                    }
                }));
            } else {
                ApplicationManager.getApplication().invokeLater(() -> WriteCommandAction.runWriteCommandAction(GetCommandAction.project, () -> {
                    PsiField[] fields = GetCommandAction.psiClass.getFields();
                    for (PsiField field : fields) {
                        GenerationInfo info = new PsiFieldMember(field).generateGetter();
                        if (info != null) {
                            info.insert(GetCommandAction.psiClass, GetCommandAction.psiClass.getLastChild(), true);
                        }
                    }
                }));
            }
            return true;
        }
        return false;
    }

    private boolean addSetter() {
        // cmd: add setter -> generate all setters
        // cmd: add setter for {att} -> generate setter for the attribute att
        if (command.getKey().equals("add") && command.getTarget().equals("setter")) {
            if (command.hasParameters() && command.getParameters().get(0).equals("for")) {
                String attributeName = variableNameFromIndex(command.getParameters(), 1);
                ApplicationManager.getApplication().invokeLater(() -> WriteCommandAction.runWriteCommandAction(GetCommandAction.project, () -> {
                    PsiField[] fields = GetCommandAction.psiClass.getFields();
                    for (PsiField field : fields) {
                        if (field.getName().equals(attributeName)) {
                            GenerationInfo info = new PsiFieldMember(field).generateSetter();
                            if (info != null) {
                                info.insert(GetCommandAction.psiClass, GetCommandAction.psiClass.getLastChild(), true);
                            }
                            break;
                        }
                    }
                }));
            } else {
                ApplicationManager.getApplication().invokeLater(() -> WriteCommandAction.runWriteCommandAction(GetCommandAction.project, () -> {
                    PsiField[] fields = GetCommandAction.psiClass.getFields();
                    for (PsiField field : fields) {
                        GenerationInfo info = new PsiFieldMember(field).generateSetter();
                        if (info != null) {
                            info.insert(GetCommandAction.psiClass, GetCommandAction.psiClass.getLastChild(), true);
                        }
                    }
                }));
                return true;
            }
        }
        return false;
    }

    private boolean CommitAndPush() {
        // push with the message {message} -> commit with the message {message} the push the code to the remote repo. still not working
        if (command.getKey().equals("push") && command.getTarget().equals("with the message") && command.hasParameters()) {
            String message = "";
            ArrayList<String> parameters = command.getParameters();
            for (String parameter : parameters) {
                message += parameter + " ";
            }
            message = "\"" + message + "\"";


            try {
                File project = new File(GetCommandAction.project.getBasePath());
                String exec = "git add ."; // && git commit -m \"test 5\" && git push";
                Runtime.getRuntime().exec(exec, new String[0], project);
            } catch (IOException e) {
                e.printStackTrace();
            }
            return true;
        }
        return false;
    }

    private boolean addAttribute() {
        // cmd: add attribute {type} {name}
        if (command.getKey().equals("add") && command.getTarget().equals("attribute") && command.hasParameters()) {
            ClassResolver.TypeAndName typeAndName = ClassResolver.resolve(command.getParameters(), GetCommandAction.project, GetCommandAction.psiClass);
            if (typeAndName != null) {
                PsiField newField;
                if (!command.hasAccessModifier()) {
                    newField = JavaPsiFacade.getElementFactory(GetCommandAction.project)
                            .createField(typeAndName.getName(), typeAndName.getType());
                } else {
                    newField = JavaPsiFacade.getElementFactory(GetCommandAction.project)
                            .createFieldFromText(command.getAccessModifier() + " " + typeAndName.typeAsString() + " " + typeAndName.getName() + ";",
                                    GetCommandAction.psiClass);
                }
                ApplicationManager.getApplication().invokeLater(
                        () -> WriteCommandAction.runWriteCommandAction(GetCommandAction.project, () -> {
                            GetCommandAction.psiClass.add(newField);
                        }));
                return true;
            }
        }
        return false;
    }

    private boolean addMethod() {
        // cmd: add method {type} {name}
        // cmd: add method {type} {name} parameters {type} {name} and {type} {name} and ....

        if (command.getKey().equals("add") && command.getTarget().equals("method") && command.hasParameters()) {
            String accessModifier;
            if (!command.hasAccessModifier()) {
                accessModifier = "public";
            } else {
                accessModifier = command.getAccessModifier();
            }
            String signature = methodName(command.getParameters());
            if (signature != null) {
                PsiMethod newMethod = JavaPsiFacade.getElementFactory(GetCommandAction.project)
                        .createMethodFromText(accessModifier + " " + signature,
                                GetCommandAction.psiClass);
                int line = newMethod.getTextOffset();
                PsiMethod[] methods = GetCommandAction.psiClass.getMethods();
                if (methods.length > 0) {
                    PsiElement insertAfter = methods[methods.length - 1];
                    ApplicationManager.getApplication().invokeLater(
                            () -> WriteCommandAction.runWriteCommandAction(GetCommandAction.project, () -> {
                                GetCommandAction.psiClass.addAfter(newMethod, insertAfter);
                            }));
                    int offset = insertAfter.getTextRange().getEndOffset();
                    goToLine(GetCommandAction.document.getLineNumber(offset) + 3);
                } else {
                    ApplicationManager.getApplication().invokeLater(
                            () -> WriteCommandAction.runWriteCommandAction(GetCommandAction.project, () -> {
                                GetCommandAction.psiClass.add(newMethod);
                            }));
                    int offset = GetCommandAction.psiClass.getTextRange().getStartOffset();
                    goToLine(GetCommandAction.document.getLineNumber(offset) + GetCommandAction.psiClass.getFields().length + 4);
                }


                return true;
            }
        }
        return false;
    }

    private boolean createClass() {
        //cmd: create class {name}
        //TODO: check the accessModifier of the class

        if (command.getKey().equals("create") &&
                command.getTarget().equals("class") && command.hasParameters()) {
            String className = className(command.getParameters());
            PsiDirectory directory = GetCommandAction.psiJavaFile.getContainingDirectory();
            ApplicationManager.getApplication().invokeLater(
                    () -> WriteCommandAction.runWriteCommandAction(GetCommandAction.project, () -> {
                        PsiClass createdClass = JavaDirectoryService.getInstance().createClass(directory, className);
                        createdClass.getContainingFile().navigate(true);
                    }));
            return true;
        }
        return false;
    }

    private boolean openClass() {
        //cmd: open class {name}
        if (command.getKey().equals("open") &&
                command.getTarget().equals("class") && command.hasParameters()) {
            String qualifiedName = GetCommandAction.psiClass.getQualifiedName();
            PsiPackage psiPackage = JavaPsiFacade.getInstance(GetCommandAction.project)
                    .findPackage(Objects.requireNonNull(StringUtils.substringBeforeLast(qualifiedName, ".")));
            if (psiPackage != null) {
                psiPackage.findClassByShortName(className(command.getParameters()),
                        GlobalSearchScope.projectScope(GetCommandAction.project))[0]
                        .navigate(true);
                return true;
            } else {
                AllClassesSearchExecutor.processClassesByNames(
                        GetCommandAction.project,
                        GlobalSearchScope.projectScope(GetCommandAction.project),
                        Collections.singletonList(className(command.getParameters())),
                        psiClass -> {
                            psiClass.navigate(true);
                            return false;
                        }
                );
            }
        }
        return false;
    }

    private boolean renameClass() {
        //cmd: rename class {name1} to {name2}
        if (command.getKey().equals("rename") &&
                command.getTarget().equals("class") && command.hasParameters()) {
            WriteCommandAction.runWriteCommandAction(GetCommandAction.project, () -> {
                GetCommandAction.psiClass.setName(className(command.getParameters()));
            });
            return true;
        }
        return false;
    }

    private boolean renameVariable() {
        //cmd: rename variable {name1} to {name2}
        // if {name1} or {name2} contains "to" rename variable {name1} to the name {name2}
        if (command.getKey().equals("rename") && command.getTarget().equals("variable") && command.hasParameters()) {
            String p = toSingleString(command.getParameters());
            String parameters[] = p.split("to the name", 2);
            if (parameters.length == 1) {
                parameters = p.split("to", 2);
            }
            if (parameters.length == 2) {
                String toBeRenamed = variableName(parameters[0]);
                String newName = variableName(parameters[1]);
                PsiElement currentElement = GetCommandAction.psiJavaFile.findElementAt(GetCommandAction.editor.getCaretModel().getOffset());
                AtomicBoolean stopAtThisScope = new AtomicBoolean(true);
                assert currentElement != null;
                PsiScopesUtil.treeWalkUp((element, state) -> {
                    if (element instanceof PsiVariable && stopAtThisScope.get() &&
                            ((PsiVariable) element).getNameIdentifier().getText().equals(toBeRenamed)) {
                        doRename(GetCommandAction.project, element, newName);
                        return false;
                    }
                    return true;
                }, currentElement, null);
                return true;
            }
        }
        return false;
    }

    private boolean declareVariable() {
        //cmd: declare variable {type} {name}
        if (command.getKey().equals("declare") && command.getTarget().equals("variable") && command.hasParameters()) {
            PsiElement insertAfter = GetCommandAction.psiJavaFile.findElementAt(GetCommandAction.editor.getCaretModel().getOffset());
            int line = GetCommandAction.editor.getCaretModel().getPrimaryCaret().getLogicalPosition().line;
            PsiElement superElement = insertAfter.getParent();
            ClassResolver.TypeAndName typeAndName = ClassResolver.resolve(command.getParameters(),
                    GetCommandAction.project, GetCommandAction.psiClass);
            if (superElement != null && typeAndName != null) {
                PsiElement newVar = JavaPsiFacade.getElementFactory(GetCommandAction.project)
                        .createVariableDeclarationStatement(typeAndName.getName(), typeAndName.getType(), null);
                ApplicationManager.getApplication().invokeLater(
                        () -> {
                            WriteCommandAction.runWriteCommandAction(GetCommandAction.project, () -> {
                                if (typeAndName.getPsiClass() != null) {
                                    GetCommandAction.psiJavaFile.importClass(typeAndName.getPsiClass());
                                }
                                superElement.addBefore(newVar, insertAfter);
                            });
                            goToLine(line + 1);
                        });
            } else {
                // No possible class found
            }
            //setCommand("format");
            return true;
        }

        return false;
    }

    private boolean callMethod() {
        // cmd: call method x on y
        // or call method x
        if (command.getKey().equals("call") && command.getTarget().equals("method") && command.hasParameters()) {
            PsiElement insertAfter = GetCommandAction.psiJavaFile.findElementAt(GetCommandAction.editor.getCaretModel().getOffset());
            assert insertAfter != null;
            PsiElement superElement = insertAfter.getParent();
            String code = extractCall(command.getParameters(), insertAfter);
            if (!code.isEmpty()) {
                createStatement(superElement, insertAfter, code + ";");
                return true;
            }
        }
        return false;
    }

    private boolean addReturn() {
        // cmd: return {name}
        // cmd: return nothing
        if (command.getKey().equals("return") && command.hasParameters()) {
            PsiElement insertAfter = GetCommandAction.psiJavaFile.findElementAt(GetCommandAction.editor.getCaretModel().getOffset());
            PsiElement currentElement = insertAfter;
            PsiElement superElement = insertAfter.getParent();
            String code;
            if (command.getParameters().size() > 1 && command.getParameters().get(0).equals("nothing")) {
                createStatement(superElement, insertAfter, "return;");
            } else if (command.getParameters().size() > 1 && command.getParameters().get(0).equals("new")) {
                List<String> initialization = command.getParameters().subList(1, command.getParameters().size());
                PsiType type = PsiType.VOID;
                boolean typeFound = false;
                while (currentElement != null) {
                    if (currentElement instanceof PsiMethod) {
                        type = ((PsiMethod) currentElement).getReturnType();
                        if (!type.getPresentableText().equals("void") && !type.getPresentableText().equals("Void")) {
                            typeFound = true;
                        }
                        break;
                    }
                    currentElement = currentElement.getParent();
                }
                if (typeFound) {
                    code = extractNewInitialization(initialization, type);
                    if (!code.isEmpty()) {
                        createStatement(superElement, insertAfter, "return new " + code + ";");
                    }
                }
            } else if (command.getParameters().size() > 1 && command.getParameters().get(0).equals("call")) {
                List<String> initialization = command.getParameters().subList(1, command.getParameters().size());
                code = extractCall(initialization, insertAfter);
                if (!code.isEmpty()) {
                    createStatement(superElement, insertAfter, "return " + code + ";");
                }

            } else if (command.getParameters().size() > 1 && command.getParameters().get(0).equals("value")) {
                List<String> initialization = command.getParameters().subList(1, command.getParameters().size());
                PsiType type = PsiType.VOID;
                boolean typeFoundAndNotVoid = false;
                while (currentElement != null) {
                    if (currentElement instanceof PsiMethod) {
                        type = ((PsiMethod) currentElement).getReturnType();
                        if (!type.getPresentableText().equals("void") && !type.getPresentableText().equals("Void")) {
                            typeFoundAndNotVoid = true;
                        }
                        break;
                    }
                    currentElement = currentElement.getParent();
                }
                if (typeFoundAndNotVoid) {
                    code = extractValueForAssignment(initialization, type);
                    if (!code.isEmpty()) {
                        createStatement(superElement, insertAfter, "return " + code + ";");
                    }
                }
            } else {
                PsiType type = PsiType.VOID;
                boolean typeFound = false;
                while (currentElement != null) {
                    if (currentElement instanceof PsiMethod) {
                        type = ((PsiMethod) currentElement).getReturnType();
                        if (!type.getPresentableText().equals("void") && !type.getPresentableText().equals("Void")) {
                            typeFound = true;
                        }
                        break;
                    }
                    currentElement = currentElement.getParent();
                }
                if (typeFound) {
                    code = extractAssignment(command.getParameters(), type, insertAfter);
                    if (!code.isEmpty()) {
                        createStatement(superElement, insertAfter, "return " + code + ";");

                    }
                }
            }
        }
        return false;
    }

    private boolean initializeVariable() {
        //cmd: initialize <<variable>> {type} {name} to new {type}
        //cmd: initialize <<variable>> {type} {name} to new {type} parameter {name} and {name} ...
        //cmd: initialize <<variable>> {type} {name} to call {name}
        //cmd: initialize <<variable>> {type} {name} to call {name} parameter {name} and {name} ...
        //cmd: initialize <<variable>> {type} {name} to {name} ->to a variable, if there is no variable found and the type is a primitive type then extract a value of {name}
        //cmd: initialize <<variable>> {type} {name} to value {name}  extract a value of {name}


        if (command.getKey().equals("initialize") && command.getTarget().equals("variable") && command.hasParameters()) {
            PsiElement insertAfter = GetCommandAction.psiJavaFile.findElementAt(GetCommandAction.editor.getCaretModel().getOffset());
            int line = GetCommandAction.editor.getCaretModel().getPrimaryCaret().getLogicalPosition().line;
            PsiElement superElement = insertAfter.getParent();
            int splitAt = command.getParameters().indexOf("to");
            if (splitAt > 0) {
                List variable = command.getParameters().subList(0, splitAt);
                ClassResolver.TypeAndName typeAndName = ClassResolver.resolve(new ArrayList(variable),
                        GetCommandAction.project, GetCommandAction.psiClass);
                if (typeAndName != null) {
                    if (splitAt + 2 < command.getParameters().size() && command.getParameters().get(splitAt + 1).equals("new")) {
                        List initialization = command.getParameters().subList(splitAt + 2, command.getParameters().size());
                        String code = extractNewInitialization(initialization, typeAndName.getType());
                        if (!code.isEmpty()) {
                            code = typeAndName.typeAsString() + " " + typeAndName.getName() + " = new " + code + ";";
                            createStatementWithLineJumpForInitialization(superElement, insertAfter, typeAndName, code, line);
                            return true;
                        }
                    } else if (splitAt + 2 < command.getParameters().size() && command.getParameters().get(splitAt + 1).equals("call")) {
                        List initialization = command.getParameters().subList(splitAt + 2, command.getParameters().size());
                        String code = extractCall(initialization, insertAfter);
                        if (!code.isEmpty()) {
                            code = typeAndName.typeAsString() + " " + typeAndName.getName() + " = " + code + ";";
                            createStatementWithLineJumpForInitialization(superElement, insertAfter, typeAndName, code, line);
                        }
                    } else if (splitAt + 2 < command.getParameters().size() && command.getParameters().get(splitAt + 1).equals("value")) {
                        List assignment = command.getParameters().subList(splitAt + 2, command.getParameters().size());
                        String value = extractValueForAssignment(assignment, typeAndName.getType());
                        if (!value.isEmpty()) {
                            String code = typeAndName.typeAsString() + " " + typeAndName.getName() + " " + " = " + value + ";";
                            createStatementWithLineJumpForInitialization(superElement, insertAfter, typeAndName, code, line);
                            return true;
                        }
                    }
                    if (splitAt + 1 < command.getParameters().size() && typeAndName != null) {
                        List assignment = command.getParameters().subList(splitAt + 1, command.getParameters().size());
                        String value = extractAssignment(assignment, typeAndName.getType(), insertAfter);
                        if (!value.isEmpty()) {
                            String code = typeAndName.typeAsString() + " " + typeAndName.getName() + " = " + value + ";";
                            createStatementWithLineJumpForInitialization(superElement, insertAfter, typeAndName, code, line);
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    private boolean assignVariable() {
        //cmd: assign <<variable>> {name} to new {type}
        //cmd: assign <<variable>> {name} to new {type} parameter {name} and {name} ...
        //cmd: assign <<variable>> {name} to call {name}
        //cmd: assign <<variable>> {name} to call {name} parameter {name} and {name} ...
        //cmd: assign <<variable>> {name} to {name} ->to a variable, if there is no variable found and the type is a primitive type then extract a value of {name}
        //cmd: assign <<variable>> {name} to value {name}  extract a value of {name}
        if (command.getKey().equals("assign") && command.getTarget().equals("variable") && command.hasParameters()) {
            PsiElement insertAfter = GetCommandAction.psiJavaFile.findElementAt(GetCommandAction.editor.getCaretModel().getOffset());
            int line = GetCommandAction.editor.getCaretModel().getPrimaryCaret().getLogicalPosition().line;
            PsiElement superElement = insertAfter.getParent();
            int splitAt = command.getParameters().indexOf("to");
            if (splitAt > 0) {
                String variable = variableName(new ArrayList<String>(command.getParameters().subList(0, splitAt)));
                AtomicBoolean variableFound = new AtomicBoolean(false);
                AtomicReference<PsiType> type = new AtomicReference<>(PsiType.VOID);
                PsiScopesUtil.treeWalkUp((element, state) -> {
                    if (element instanceof PsiVariable &&
                            ((PsiVariable) element).getNameIdentifier().getText().equals(variable)) {
                        type.set(((PsiVariable) element).getType());
                        variableFound.set(true);
                        return false;
                    }
                    return true;
                }, insertAfter, null);
                if (variableFound.get() && !type.get().equals(PsiType.VOID)) {
                    if (splitAt + 2 < command.getParameters().size() && command.getParameters().get(splitAt + 1).equals("new")) {
                        List initialization = command.getParameters().subList(splitAt + 2, command.getParameters().size());
                        String code = extractNewInitialization(initialization, type.get());
                        if (!code.isEmpty()) {
                            code = variable + " = new " + code + ";";
                            createStatementWithLineJump(superElement, insertAfter, code, line);
                            return true;
                        }
                    } else if (splitAt + 2 < command.getParameters().size() && command.getParameters().get(splitAt + 1).equals("call")) {
                        List initialization = command.getParameters().subList(splitAt + 2, command.getParameters().size());
                        String code = extractCall(initialization, insertAfter);
                        if (!code.isEmpty()) {
                            code = variable + " = " + code + ";";
                            createStatementWithLineJump(superElement, insertAfter, code, line);
                        }
                    } else if (splitAt + 2 < command.getParameters().size() && command.getParameters().get(splitAt + 1).equals("value")) {
                        List assignment = command.getParameters().subList(splitAt + 2, command.getParameters().size());
                        String value = extractValueForAssignment(assignment, type.get());
                        if (!value.isEmpty()) {
                            String code = variable + " " + " = " + value + ";";
                            createStatementWithLineJump(superElement, insertAfter, code, line);
                            return true;
                        }
                    }
                    if (splitAt + 1 < command.getParameters().size()) {
                        List assignment = command.getParameters().subList(splitAt + 1, command.getParameters().size());
                        String value = extractAssignment(assignment, type.get(), insertAfter);
                        if (!value.isEmpty()) {
                            String code = variable + " = " + value + ";";
                            createStatementWithLineJump(superElement, insertAfter, code, line);
                            return true;
                        }
                    }

                }
                return true;
            }
        }
        return false;
    }

    private boolean addIf() {
        // cmd: add if expression {name} {arithmetic exp} {name}
        // the command: add if expression {name} {arithmetic exp} {name}
        if (command.getKey().equals("add") && command.getTarget().equals("if") && command.hasParameters()) {
            if (command.getParameters().get(0).equals("expression") && command.getParameters().size() > 1) {
                String expression = extractBoolean(toSingleString(new ArrayList<>(command.getParameters().subList(1, command.getParameters().size()))));
                PsiElement insertAfter = GetCommandAction.psiJavaFile.findElementAt(GetCommandAction.editor.getCaretModel().getOffset());
                int line = GetCommandAction.editor.getCaretModel().getPrimaryCaret().getLogicalPosition().line;
                PsiElement superElement = insertAfter.getParent();
                if (superElement != null) {
                    PsiElement newVar = JavaPsiFacade.getElementFactory(GetCommandAction.project)
                            .createStatementFromText("if (" + expression + ") {\n  \n }", GetCommandAction.psiClass);
                    ApplicationManager.getApplication().invokeLater(
                            () -> {
                                WriteCommandAction.runWriteCommandAction(GetCommandAction.project, () -> {
                                    superElement.addBefore(newVar, insertAfter);
                                });
                            });
                    goToLine(line + 1);
                }
            } else if (command.getParameters().get(0).equals("call")) {

            } else {
                String expression = extractBoolean(toSingleString(command.getParameters()));
                PsiElement insertAfter = GetCommandAction.psiJavaFile.findElementAt(GetCommandAction.editor.getCaretModel().getOffset());
                int line = GetCommandAction.editor.getCaretModel().getPrimaryCaret().getLogicalPosition().line;
                PsiElement superElement = insertAfter.getParent();
                if (superElement != null) {
                    PsiElement newVar = JavaPsiFacade.getElementFactory(GetCommandAction.project)
                            .createStatementFromText("if (" + expression + ") {\n  \n }", GetCommandAction.psiClass);
                    ApplicationManager.getApplication().invokeLater(
                            () -> {
                                WriteCommandAction.runWriteCommandAction(GetCommandAction.project, () -> {
                                    superElement.addBefore(newVar, insertAfter);
                                });
                            });
                    goToLine(line + 1);
                }

            }
            return true;
        }
        return false;
    }

    private boolean addElseIf() {
        // the command: add else if expression ten less than five
        if (command.getKey().equals("add") && command.getTarget().equals("else if") && command.hasParameters()) {
            if (command.getParameters().get(0).equals("expression") && command.getParameters().size() > 1) {
                String expression = extractBoolean(toSingleString(new ArrayList<>(command.getParameters().subList(1, command.getParameters().size()))));
                PsiElement element = GetCommandAction.psiJavaFile.findElementAt(GetCommandAction.editor.getCaretModel().getOffset());
                while (element != null) {
                    if (element instanceof PsiIfStatement) {
                        PsiIfStatement ifStatement = (PsiIfStatement) element;
                        ApplicationManager.getApplication().invokeLater(
                                () -> WriteCommandAction.runWriteCommandAction(GetCommandAction.project, () -> {
                                    ifStatement.setElseBranch(JavaPsiFacade.getElementFactory(GetCommandAction.project)
                                            .createStatementFromText("if (" + expression + ") {\n   \n}", GetCommandAction.psiClass));
                                }));
                        int offset = element.getTextRange().getEndOffset();
                        int line = GetCommandAction.document.getLineNumber(offset);
                        goToLine(line + 1);
                        break;
                    }
                    element = element.getParent();
                }
            } else if (command.getParameters().get(0).equals("call")) {

            } else {
                String expression = extractBoolean(toSingleString(command.getParameters()));
                PsiElement element = GetCommandAction.psiJavaFile.findElementAt(GetCommandAction.editor.getCaretModel().getOffset());
                while (element != null) {
                    if (element instanceof PsiIfStatement) {
                        PsiIfStatement ifStatement = (PsiIfStatement) element;
                        ApplicationManager.getApplication().invokeLater(
                                () -> WriteCommandAction.runWriteCommandAction(GetCommandAction.project, () -> {
                                    ifStatement.setElseBranch(JavaPsiFacade.getElementFactory(GetCommandAction.project)
                                            .createStatementFromText("if (" + expression + ") {\n   \n}", GetCommandAction.psiClass));
                                }));
                        int offset = element.getTextRange().getEndOffset();
                        int line = GetCommandAction.document.getLineNumber(offset);
                        goToLine(line + 1);
                        break;
                    }
                    element = element.getParent();
                }
            }
            return true;
        }
        return false;
    }


    private boolean addElse() {
        // the command: add else
        if (command.getKey().equals("add") && command.getTarget().equals("else")) {
            PsiElement element = GetCommandAction.psiJavaFile.findElementAt(GetCommandAction.editor.getCaretModel().getOffset());
            while (element != null) {
                if (element instanceof PsiIfStatement) {
                    PsiIfStatement ifStatement = (PsiIfStatement) element;
                    ApplicationManager.getApplication().invokeLater(
                            () -> WriteCommandAction.runWriteCommandAction(GetCommandAction.project, () -> {
                                ifStatement.setElseBranch(JavaPsiFacade.getElementFactory(GetCommandAction.project)
                                        .createStatementFromText("{\n   \n}", GetCommandAction.psiClass));
                            }));
                    int offset = element.getTextRange().getEndOffset();
                    int line = GetCommandAction.document.getLineNumber(offset);
                    goToLine(line + 1);
                    break;
                }
                element = element.getParent();
            }
            return true;
        }
        return false;
    }

    private boolean addWhile() {
        if (command.getKey().equals("add") && command.getTarget().equals("while") && command.hasParameters()) {
            if (command.getParameters().get(0).equals("expression") && command.getParameters().size() > 1) {
                String expression = extractBoolean(toSingleString(new ArrayList<>(command.getParameters().subList(1, command.getParameters().size()))));
                PsiElement insertAfter = GetCommandAction.psiJavaFile.findElementAt(GetCommandAction.editor.getCaretModel().getOffset());
                int line = GetCommandAction.editor.getCaretModel().getPrimaryCaret().getLogicalPosition().line;
                PsiElement superElement = insertAfter.getParent();
                if (superElement != null) {
                    PsiElement newVar = JavaPsiFacade.getElementFactory(GetCommandAction.project)
                            .createStatementFromText("while (" + expression + ") {\n     \n}", GetCommandAction.psiClass);
                    ApplicationManager.getApplication().invokeLater(
                            () -> {
                                WriteCommandAction.runWriteCommandAction(GetCommandAction.project, () -> {
                                    superElement.addBefore(newVar, insertAfter);
                                });
                            });
                    goToLine(line + 1);
                }
            } else if (command.getParameters().get(0).equals("call")) {

            } else {
                String expression = extractBoolean(toSingleString(command.getParameters()));
                PsiElement insertAfter = GetCommandAction.psiJavaFile.findElementAt(GetCommandAction.editor.getCaretModel().getOffset());
                int line = GetCommandAction.editor.getCaretModel().getPrimaryCaret().getLogicalPosition().line;
                PsiElement superElement = insertAfter.getParent();
                if (superElement != null) {
                    PsiElement newVar = JavaPsiFacade.getElementFactory(GetCommandAction.project)
                            .createStatementFromText("while (" + expression + ") {\n     \n}", GetCommandAction.psiClass);
                    ApplicationManager.getApplication().invokeLater(
                            () -> {
                                WriteCommandAction.runWriteCommandAction(GetCommandAction.project, () -> {
                                    superElement.addBefore(newVar, insertAfter);
                                });
                            });
                    goToLine(line + 1);
                }
            }
            return true;
        }
        return false;
    }

    private boolean undo() {
        if (command.getKey().equals("undo")) {
            ApplicationManager.getApplication().invokeLater(
                    () -> WriteCommandAction.runWriteCommandAction(GetCommandAction.project, () ->
                            UndoManager.getInstance(GetCommandAction.project)
                                    .undo(FileEditorManager.getInstance(GetCommandAction.project).getSelectedEditor())
                    )
            );
            return true;
        }
        return false;
    }

    private boolean redo() {
        if (command.getKey().equals("redo")) {
            ApplicationManager.getApplication().invokeLater(
                    () -> WriteCommandAction.runWriteCommandAction(GetCommandAction.project, () ->
                            UndoManager.getInstance(GetCommandAction.project)
                                    .redo(FileEditorManager.getInstance(GetCommandAction.project).getSelectedEditor())
                    )
            );
            return true;
        }
        return false;
    }

    private boolean removeLine() {
        if (command.getKey().equals("remove") && command.getTarget().equals("line") && command.hasParameters()) {
            int line = -1;
            ArrayList<String> parameters = command.getParameters();
            String[] parameter = new String[parameters.size()];
            parameter[0] = parameters.get(0);
            for (int i = 1; i < parameter.length; i++) {
                parameter[i] = parameter[i - 1] + " " + parameters.get(i);
            }
            for (int i = parameter.length - 1; i > -1; i--) {
                line = convertWordToInt(parameter[i]) - 1;
                if (line > 0) {
                    break;
                }
            }
            if (line > 0) {
                int finalLine = line;
                Document document = GetCommandAction.document;
                ApplicationManager.getApplication().invokeLater(
                        () -> WriteCommandAction.runWriteCommandAction(GetCommandAction.project, () -> {
                            document.deleteString(
                                    document.getLineStartOffset(finalLine),
                                    Math.min(document.getLineEndOffset(finalLine) + 1, document.getTextLength())
                            );
                        })
                );
                return true;
            }
        }
        return false;
    }

    private boolean deleteLine() {
        if (command.getKey().equals("delete") && command.getTarget().equals("line") && command.hasParameters()) {
            int line = -1;
            ArrayList<String> parameters = command.getParameters();
            String[] parameter = new String[parameters.size()];
            parameter[0] = parameters.get(0);
            for (int i = 1; i < parameter.length; i++) {
                parameter[i] = parameter[i - 1] + " " + parameters.get(i);
            }
            for (int i = parameter.length - 1; i > -1; i--) {
                line = convertWordToInt(parameter[i]) - 1;
                if (line > 0) {
                    break;
                }
            }
            if (line > 0) {
                int finalLine = line;
                ApplicationManager.getApplication().invokeLater(
                        () -> WriteCommandAction.runWriteCommandAction(GetCommandAction.project, () -> {
                            GetCommandAction.document.deleteString(
                                    GetCommandAction.document.getLineStartOffset(finalLine), GetCommandAction.document.getLineEndOffset(finalLine)
                            );
                        })
                );
                goToLine(finalLine);
                return true;
            }
        }
        return false;
    }

    private boolean goToLine(int line) {
        if (line == -1 && command.getKey().equals("go") &&
                command.getTarget().equals("line") && command.hasParameters()) {
            ArrayList<String> parameters = command.getParameters();
            String[] parameter = new String[parameters.size()];
            parameter[0] = parameters.get(0);
            for (int i = 1; i < parameter.length; i++) {
                parameter[i] = parameter[i - 1] + " " + parameters.get(i);
            }
            for (int i = parameter.length - 1; i > -1; i--) {
                line = convertWordToInt(parameter[i]) - 1;
                if (line > 0) {
                    break;
                }
            }

        }
        if (line > 0) {
            int finalLine = line;
            ApplicationManager.getApplication().invokeLater(
                    () -> WriteCommandAction.runWriteCommandAction(GetCommandAction.project, () -> {
                        GetCommandAction.editor.getCaretModel().moveToOffset((GetCommandAction.document.getLineEndOffset(finalLine)));
                    })
            );
            return true;
        }
        return false;
    }

    public String extractNewInitialization(List initialization, PsiType type) {
        int splitAt = Math.max(initialization.indexOf("parameter"), initialization.indexOf("parameters"));
        String constructor;
        if (splitAt > 0) {
            constructor = className(new ArrayList(initialization.subList(0, splitAt)));
            // TODO: how to pass the arguments to the constructor
        } else {
            constructor = className(new ArrayList(initialization));
        }
        String className = type.getPresentableText();
        int indexOfAngleBracket = className.indexOf('<');
        if (indexOfAngleBracket > 0) {
            className = className.substring(0, indexOfAngleBracket);
        }
        String finalClassName = className;
        TypeAndPsiClass result = processFirstNotNull(
                () -> resolveInnerClasses(finalClassName, GetCommandAction.project, GetCommandAction.psiClass),
                () -> resolveLocalClasses(finalClassName, GetCommandAction.project, GetCommandAction.psiClass),
                () -> resolveImportedClasses(finalClassName, GetCommandAction.project, GetCommandAction.psiClass),
                () -> resolveLibraryClasses(finalClassName, GetCommandAction.project),
                () -> resolveAllClasses(finalClassName, GetCommandAction.project, GetCommandAction.psiClass)
        );
        if (result != null) {
            PsiType newType = result.type;
            if (type.isAssignableFrom(newType)) {
                return constructor + "(" + ")";
            }
        }

        return "";
    }

    public String extractCall(List<String> call, PsiElement anchor) {
        String code = "";
        if (call != null && anchor != null) {
            final PsiVariable[] variable = {null};
            List<String> var, methods, method, parameters = new ArrayList<>();
            String methodName, completeCall = "", variableName;
            PsiClass psiClass = null;
            int splitAt = call.indexOf("on");
            if (splitAt > 0 && splitAt + 1 < call.size()) {
                var = call.subList(splitAt + 1, call.size());
                methods = call.subList(0, splitAt);
                String varName = variableName(new ArrayList<>(var));
                variableName = varName + ".";
                AtomicBoolean ab = new AtomicBoolean(true);
                PsiScopesUtil.treeWalkUp((element, state) -> {
                    if (element instanceof PsiVariable && ((PsiVariable) element).getNameIdentifier().getText().equals(varName)) {
                        variable[0] = (PsiVariable) element;
                        ab.set(false);
                        return false;
                    }
                    return true;
                }, anchor, null);
                if (variable[0] != null) {
                    String className = variable[0].getType().getPresentableText();
                    int indexOfAngleBracket = className.indexOf('<');
                    if (indexOfAngleBracket > 0) {
                        className = className.substring(0, indexOfAngleBracket);
                    }
                    String finalClassName = className;
                    ClassResolver.TypeAndPsiClass result = processFirstNotNull(
                            () -> resolveInnerClasses(finalClassName, GetCommandAction.project, GetCommandAction.psiClass),
                            () -> resolveLocalClasses(finalClassName, GetCommandAction.project, GetCommandAction.psiClass),
                            () -> resolveImportedClasses(finalClassName, GetCommandAction.project, GetCommandAction.psiClass),
                            () -> resolveLibraryClasses(finalClassName, GetCommandAction.project),
                            () -> resolveAllClasses(finalClassName, GetCommandAction.project, GetCommandAction.psiClass)
                    );
                    if (result != null) {
                        psiClass = result.psiClass;
                    }

                }
            } else {
                psiClass = GetCommandAction.psiClass;
                methods = call;
                variableName = "";
            }
            splitAt = methods.indexOf("call");
            while (splitAt > 0) {
                method = methods.subList(0, splitAt);
                if (splitAt + 1 < methods.size()) {
                    methods = methods.subList(splitAt + 1, methods.size());
                }
                int splitAtPara = Math.max(method.indexOf("parameter"), method.indexOf("parameters"));
                int parametersAmount = 0;
                String arguments = "";
                if (splitAtPara > 0 && splitAtPara + 1 < methods.size()) {
                    parameters = method.subList(splitAtPara + 1, method.size());
                    for (String parameter : parameters) {
                        if (parameter.equals("and")) {
                            parametersAmount++;
                        }
                    }
                    method = method.subList(0, splitAtPara);
                }
                methodName = variableName(new ArrayList<>(method));
                if (psiClass != null) {
                    for (PsiMethod psiMethod : psiClass.getAllMethods())
                        if (methodName.equals(psiMethod.getName()) &&
                                psiMethod.getParameterList().getParametersCount() == parametersAmount) {
                            completeCall += methodName + "(" +
                                    argumentsBuilder(parameters, psiMethod.getParameterList().getParameters(), anchor) + ").";
                            String className = psiMethod.getReturnType().getPresentableText();
                            int indexOfAngleBracket = className.indexOf('<');
                            if (indexOfAngleBracket > 0) {
                                className = className.substring(0, indexOfAngleBracket);
                            }
                            String finalClassName = className;
                            ClassResolver.TypeAndPsiClass result = processFirstNotNull(
                                    () -> resolveInnerClasses(finalClassName, GetCommandAction.project, GetCommandAction.psiClass),
                                    () -> resolveLocalClasses(finalClassName, GetCommandAction.project, GetCommandAction.psiClass),
                                    () -> resolveImportedClasses(finalClassName, GetCommandAction.project, GetCommandAction.psiClass),
                                    () -> resolveLibraryClasses(finalClassName, GetCommandAction.project),
                                    () -> resolveAllClasses(finalClassName, GetCommandAction.project, GetCommandAction.psiClass)
                            );
                            if (result != null) {
                                psiClass = result.psiClass;
                            }
                            break;
                        }
                }
                splitAt = methods.indexOf("call");
            }
            int splitAtPara = Math.max(methods.indexOf("parameter"), methods.indexOf("parameters"));
            int parametersAmount = 0;
            if (splitAtPara > 0 && splitAtPara + 1 < methods.size()) {
                parameters = methods.subList(splitAtPara + 1, methods.size());
                for (String parameter : parameters) {
                    if (parameter.equals("and")) {
                        parametersAmount++;
                    }
                }
                methods = methods.subList(0, splitAtPara);
            }
            methodName = variableName(new ArrayList<>(methods));
            if (psiClass != null) {
                for (PsiMethod psiMethod : psiClass.getAllMethods()) {
                    if (methodName.equals(psiMethod.getName()) &&
                            psiMethod.getParameterList().getParametersCount() == parametersAmount + 1) {
                        completeCall += methodName + "(" +
                                argumentsBuilder(parameters, psiMethod.getParameterList().getParameters(), anchor) + ")";
                        break;
                    }
                }
            }
            if (!completeCall.isEmpty()) {
                code = variableName + completeCall;
            }
        }
        return code;
    }

    private String extractArithmetic(String exp) {
        if (exp.contains("plus")) {
            String[] words = exp.split("plus", 2);
            return extractArithmetic(words[0]) + " \u002B " + extractArithmetic(words[1]);
        }

        if (exp.contains("minus")) {
            String[] words = exp.split("minus", 2);
            return extractArithmetic(words[0]) + " - " + extractArithmetic(words[1]);
        }

        if (exp.contains("times")) {
            String[] words = exp.split("times", 2);
            return extractArithmetic(words[0]) + " * " + extractArithmetic(words[1]);
        }

        if (exp.contains("by")) {
            String[] words = exp.split("by", 2);
            return extractArithmetic(words[0]) + " / " + extractArithmetic(words[1]);
        }

        if (exp.contains(" x ")) {
            String[] words = exp.split(" x ", 2);
            return extractArithmetic(words[0]) + "  *  " + extractArithmetic(words[1]);
        }

        if (exp.contains(" modulo ")) {
            String[] words = exp.split(" modulo ", 2);
            return extractArithmetic(words[0]) + "  %  " + extractArithmetic(words[1]);
        }
        return exp;
    }

    public String extractAssignment(List assignment, PsiType type, PsiElement anchor) {
        String value = "";
        if (type != null && anchor != null && !assignment.isEmpty()) {
            String init = toSingleString(new ArrayList<>(assignment));
            String exp = extractArithmetic(init);
            if (!hasSigns(exp)) {
                String finalValue = variableName(new ArrayList<>(assignment));
                AtomicBoolean variableFound = new AtomicBoolean(false);
                PsiScopesUtil.treeWalkUp((element, state) -> {
                    if (element instanceof PsiVariable
                            && ((PsiVariable) element).getNameIdentifier().getText().equals(finalValue)
                            && type.isAssignableFrom(((PsiVariable) element).getType())) {
                        variableFound.set(true);
                        return false;
                    }
                    return true;
                }, anchor, null);
                if (variableFound.get()) {
                    value = finalValue;
                }
                if (value.isEmpty()) {
                    value = extractValueForAssignment(assignment, type);
                }
            } else {
                String[] assis = exp.split("\\s+");
                ArrayList<String> as = new ArrayList<>();
                if (assis.length > 0) {
                    for (String assi : assis) {
                        if (assi.equals("/") || assi.equals("*") || assi.equals("\u002B")
                                || assi.equals("-") || assi.equals("%")) {
                            String result = extractAssignment(as, type, anchor);
                            as.clear();
                            if (!result.isEmpty()) {
                                value += result + " " + assi;
                            }
                        } else {
                            as.add(assi);
                        }
                    }
                }
                if (as.size() != assignment.size()) {
                    value += " " + extractAssignment(as, type, anchor);
                } else {
                    value = extractValueForAssignment(assignment, type);
                }
            }
        }
        return value;
    }

    private boolean hasSigns(String exp) {
        return exp.contains("/") || exp.contains("*") || exp.contains("\u002B") || exp.contains("-");
    }

    public String extractValueForAssignment(List assignment, PsiType type) {
        String value = "";
        if (type != null) {
            String init = toSingleString(new ArrayList<>(assignment));
            if (PsiType.INT.equals(type)) {
                int val = convertWordToInt(init);
                if (val > Integer.MIN_VALUE) {
                    value = String.valueOf(val);
                }
            } else if (PsiType.DOUBLE.equals(type)) {
                value = convertWordToDouble(init);
            } else if (PsiType.BOOLEAN.equals(type)) {
                if (init.equals("false")) {
                    value = "false";
                } else if (init.equals("true")) {
                    value = "true";
                } else {
                    value = extractBoolean(init);
                }
            } else if (PsiType.FLOAT.equals(type)) {
                value = convertWordToDouble(init) + "f";
                if (value.equals("f")) {
                    value = "";
                }
            } else if (PsiType.BYTE.equals(type)) {
                value = "";
            } else if (PsiType.LONG.equals(type)) {
                int val = convertWordToInt(init);
                if (val > Integer.MIN_VALUE) {
                    value = val + "L";
                }
            } else if (PsiType.CHAR.equals(type)) {
                // TODO: implement avoidHomonyms
                value = "";
            } else if (type.getPresentableText().equals("String")) {
                value = "\"" + init + "\"";
            }
        }
        return value;
    }

    private String className(ArrayList<String> parameters) {
        // TODO: take care of class name conventions; no dots commas etc.
        String name = "";
        for (String parameter : parameters) {
            StringUtils.capitalize(parameter);
            name += StringUtils.capitalize(parameter);
        }
        return name;
    }

    private String variableName(ArrayList<String> parameters) {
        int length = parameters.size();
        String name = parameters.get(0);
        for (int i = 1; i < length; i++) {
            name += StringUtils.capitalize(parameters.get(i));
        }
        return name;
    }

    private String variableName(String word) {
        String[] parameters = word.trim().split("\\s+");
        String name = parameters[0];
        for (int i = 1; i < parameters.length; i++) {
            name += StringUtils.capitalize(parameters[i]);
        }
        return name;
    }

    private String variableNameFromIndex(ArrayList<String> parameters, int index) {
        int length = parameters.size();
        String name = parameters.get(index);
        index++;
        for (; index < length; index++) {
            name += StringUtils.capitalize(parameters.get(index));
        }
        return name;
    }

    private String methodName(ArrayList<String> parameters) {
        String name = null;
        if (parameters.contains("parameter") || parameters.contains("parameters")) {
            List<String> parameterList = new ArrayList<>();
            int splitAt = Math.max(parameters.indexOf("parameter"), parameters.indexOf("parameters"));
            List<String> signature = parameters.subList(0, splitAt);
            if (splitAt + 1 < parameters.size()) {
                parameterList = parameters.subList(splitAt + 1, parameters.size());
            }
            ClassResolver.TypeAndName typeAndName = ClassResolver.resolveForMethod(new ArrayList<>(signature),
                    GetCommandAction.project, GetCommandAction.psiClass);
            if (typeAndName != null) {
                ApplicationManager.getApplication().invokeLater(
                        () -> WriteCommandAction.runWriteCommandAction(GetCommandAction.project, () -> {
                            if (typeAndName.getPsiClass() != null) {
                                GetCommandAction.psiJavaFile.importClass(typeAndName.getPsiClass());
                            }
                        }));
                name = typeAndName.typeAsString() + " " + typeAndName.getName() + "(" + parametersBuilder(parameterList) + ") {\n   \n}";
            }
        } else {
            ClassResolver.TypeAndName typeAndName = ClassResolver.resolveForMethod(parameters,
                    GetCommandAction.project, GetCommandAction.psiClass);
            if (typeAndName != null) {
                ClassResolver.TypeAndName finalTypeAndName = typeAndName;
                ApplicationManager.getApplication().invokeLater(
                        () -> WriteCommandAction.runWriteCommandAction(GetCommandAction.project, () -> {
                            if (finalTypeAndName.getPsiClass() != null) {
                                GetCommandAction.psiJavaFile.importClass(finalTypeAndName.getPsiClass());
                            }
                        }));
                name = typeAndName.typeAsString() + " " + typeAndName.getName() + "() {\n   \n}";
            }
        }
        return name;
    }

    private String parametersBuilder(List<String> parameterList) {
        String name = "";
        int splitAt;
        ClassResolver.TypeAndName typeAndName;
        while (parameterList.contains("and")) {
            splitAt = parameterList.indexOf("and");
            List<String> parameter = parameterList.subList(0, splitAt);
            if (splitAt + 1 < parameterList.size()) {
                parameterList = parameterList.subList(splitAt + 1, parameterList.size());
            } else {
                break;
            }
            typeAndName = ClassResolver.resolve(new ArrayList<>(parameter),
                    GetCommandAction.project, GetCommandAction.psiClass);
            if (typeAndName != null) {
                ClassResolver.TypeAndName finalTypeAndName = typeAndName;
                ApplicationManager.getApplication().invokeLater(
                        () -> WriteCommandAction.runWriteCommandAction(GetCommandAction.project, () -> {
                            if (finalTypeAndName.getPsiClass() != null) {
                                GetCommandAction.psiJavaFile.importClass(finalTypeAndName.getPsiClass());
                            }
                        }));
                name += typeAndName.typeAsString() + " " + typeAndName.getName() + ", ";
            }
        }
        typeAndName = ClassResolver.resolve(new ArrayList<>(parameterList),
                GetCommandAction.project, GetCommandAction.psiClass);
        if (typeAndName != null) {
            ClassResolver.TypeAndName finalTypeAndName1 = typeAndName;
            ApplicationManager.getApplication().invokeLater(
                    () -> WriteCommandAction.runWriteCommandAction(GetCommandAction.project, () -> {
                        if (finalTypeAndName1.getPsiClass() != null) {
                            GetCommandAction.psiJavaFile.importClass(finalTypeAndName1.getPsiClass());
                        }
                    }));
            name += typeAndName.typeAsString() + " " + typeAndName.getName();
        }
        return name;
    }

    private String argumentsBuilder(List<String> argumentList, PsiParameter[] parameters, PsiElement anchor) {
        String arguments = "";
        if (!argumentList.isEmpty()) {
            int splitAt;
            int parameterIndex = 0;
            while (argumentList.contains("and")) {
                splitAt = argumentList.indexOf("and");
                List<String> argument = argumentList.subList(0, splitAt);
                if (splitAt + 1 < argumentList.size()) {
                    argumentList = argumentList.subList(splitAt + 1, argumentList.size());
                } else {
                    break;
                }
                String arg = extractAssignment(argument, parameters[parameterIndex].getType(), anchor);
                parameterIndex++;
                if (!arg.isEmpty()) {
                    arguments += arg + ", ";
                }
            }
            String arg = extractAssignment(argumentList, parameters[parameterIndex].getType(), anchor);
            parameterIndex++;
            if (!arg.isEmpty()) {
                arguments += arg;
            }

        }
        return arguments;
    }

    private int convertWordToInt(String input) {
        int result;
        int minus = 1;
        try {
            return Integer.parseInt(input);
        } catch (NumberFormatException e) {
            boolean isValidInput = true;
            result = 0;
            long finalResult = 0;
            List<String> allowedStrings = Arrays.asList
                    (
                            "zero", "one", "two", "three", "four", "five", "six", "seven",
                            "eight", "nine", "ten", "eleven", "twelve", "thirteen", "fourteen",
                            "fifteen", "sixteen", "seventeen", "eighteen", "nineteen", "twenty",
                            "thirty", "forty", "fifty", "sixty", "seventy", "eighty", "ninety",
                            "hundred", "thousand", "million", "billion", "trillion"
                    );

            //"One hundred two thousand and thirty four"

            if (input != null && input.length() > 0) {
                input = input.trim();
                if (input.startsWith("-")) {
                    minus = -1;
                } else if (input.startsWith("minus")) {
                    minus = -1;
                    input = input.substring(5);
                } else if (input.startsWith("negative")) {
                    minus = -1;
                    input = input.substring(8);
                }
                input = input.replaceAll("-", " ");
                input = input.toLowerCase().replaceAll(" and", " ");
                String[] splittedParts = input.trim().split("\\s+");

                for (String str : splittedParts) {
                    if (!allowedStrings.contains(str)) {
                        return Integer.MIN_VALUE;
                    }
                }
                if (isValidInput) {
                    for (String str : splittedParts) {
                        if (str.equalsIgnoreCase("zero")) {
                            result += 0;
                        } else if (str.equalsIgnoreCase("one")) {
                            result += 1;
                        } else if (str.equalsIgnoreCase("two")) {
                            result += 2;
                        } else if (str.equalsIgnoreCase("three")) {
                            result += 3;
                        } else if (str.equalsIgnoreCase("four")) {
                            result += 4;
                        } else if (str.equalsIgnoreCase("five")) {
                            result += 5;
                        } else if (str.equalsIgnoreCase("six")) {
                            result += 6;
                        } else if (str.equalsIgnoreCase("seven")) {
                            result += 7;
                        } else if (str.equalsIgnoreCase("eight")) {
                            result += 8;
                        } else if (str.equalsIgnoreCase("nine")) {
                            result += 9;
                        } else if (str.equalsIgnoreCase("ten")) {
                            result += 10;
                        } else if (str.equalsIgnoreCase("eleven")) {
                            result += 11;
                        } else if (str.equalsIgnoreCase("twelve")) {
                            result += 12;
                        } else if (str.equalsIgnoreCase("thirteen")) {
                            result += 13;
                        } else if (str.equalsIgnoreCase("fourteen")) {
                            result += 14;
                        } else if (str.equalsIgnoreCase("fifteen")) {
                            result += 15;
                        } else if (str.equalsIgnoreCase("sixteen")) {
                            result += 16;
                        } else if (str.equalsIgnoreCase("seventeen")) {
                            result += 17;
                        } else if (str.equalsIgnoreCase("eighteen")) {
                            result += 18;
                        } else if (str.equalsIgnoreCase("nineteen")) {
                            result += 19;
                        } else if (str.equalsIgnoreCase("twenty")) {
                            result += 20;
                        } else if (str.equalsIgnoreCase("thirty")) {
                            result += 30;
                        } else if (str.equalsIgnoreCase("forty")) {
                            result += 40;
                        } else if (str.equalsIgnoreCase("fifty")) {
                            result += 50;
                        } else if (str.equalsIgnoreCase("sixty")) {
                            result += 60;
                        } else if (str.equalsIgnoreCase("seventy")) {
                            result += 70;
                        } else if (str.equalsIgnoreCase("eighty")) {
                            result += 80;
                        } else if (str.equalsIgnoreCase("ninety")) {
                            result += 90;
                        } else if (str.equalsIgnoreCase("hundred")) {
                            result *= 100;
                        } else if (str.equalsIgnoreCase("thousand")) {
                            result *= 1000;
                            finalResult += result;
                            result = 0;
                        } else if (str.equalsIgnoreCase("million")) {
                            result *= 1000000;
                            finalResult += result;
                            result = 0;
                        } else if (str.equalsIgnoreCase("billion")) {
                            result *= 1000000000;
                            finalResult += result;
                            result = 0;
                        } else if (str.equalsIgnoreCase("trillion")) {
                            result *= 1000000000000L;
                            finalResult += result;
                            result = 0;
                        }
                    }

                    finalResult += result;
                }
            }
            return (int) finalResult * minus;
        }
    }

    private String convertWordToDouble(String input) {
        String result = "";
        try {
            return String.valueOf(Double.parseDouble(input));
        } catch (NumberFormatException e) {
            String[] doubleNum = input.split(" point ", 2);
            String[] doubleNum2 = input.split(" . ", 2);
            if (doubleNum.length == 2) {
                int a = convertWordToInt(doubleNum[0]);
                int b = convertWordToInt(doubleNum[1]);
                if (a > Integer.MIN_VALUE && b > 0) {
                    result = a + "." + b;
                }
            } else if (doubleNum2.length == 2) {
                int a = convertWordToInt(doubleNum[0]);
                int b = convertWordToInt(doubleNum[1]);
                if (a > Integer.MIN_VALUE && b > 0) {
                    result = a + "." + b;
                }
            } else {
                int r = convertWordToInt(input);
                if (r > Integer.MIN_VALUE) {
                    result = "" + r;
                }
            }
        }
        return result;
    }

    private void doRename(Project project, PsiElement element, String newName) {
        ApplicationManager.getApplication().invokeLater(() -> {
            WriteCommandAction.runWriteCommandAction(project, () -> {
                UsageInfo[] usages = RenameUtil.findUsages(element, newName, false,
                        false, new HashMap<>());
                RenameUtil.doRename(element, newName, usages, project, new RefactoringElementListener() {
                    @Override
                    public void elementMoved(@NotNull PsiElement newElement) {

                    }

                    @Override
                    public void elementRenamed(@NotNull PsiElement newElement) {

                    }
                });
            });
        });
    }

    private String toSingleString(ArrayList<String> parameters) {
        String parameter = "";
        for (String s : parameters) {
            parameter += s + " ";
        }
        return parameter.trim();
    }

    private ArrayList<String> toListString(String word) {
        ArrayList<String> variableName = new ArrayList<>();
        String[] words = word.trim().split("\\s+");
        for (String s : words) {
            variableName.add(s);
        }
        return variableName;
    }


    private String extractBoolean(String bool) {
        if (bool.contains("or or")) {
            String[] words = bool.split("or or", 2);
            return extractBoolean(words[0]) + " || " + extractBoolean(words[1]);
        }

        if (bool.contains("and and")) {
            String[] words = bool.split("and and", 2);
            return extractBoolean(words[0]) + " && " + extractBoolean(words[1]);
        }

        if (bool.contains("not not")) {
            String[] words = bool.split("not not", 2);
            return extractBoolean(words[0]) + "! " + extractBoolean(words[1]);
        }

        if (bool.contains("less than or equal to")) {
            String[] words = bool.split("less than or equal to");
            String firstParameter;
            String secondParameter;
            Integer num;
            String openingBracket0 = "", closingBracket0 = "";
            String openingBracket1 = "", closingBracket1 = "";
            if (words[0].contains("opening bracket")) {
                words[0] = words[0].replace("opening bracket", "");
                openingBracket0 = "(";
            }
            if (words[0].contains("closing bracket")) {
                words[0] = words[0].replace("closing bracket", "");
                closingBracket0 = ")";
            }
            if (words[1].contains("opening bracket")) {
                words[1] = words[1].replace("opening bracket", "");
                openingBracket1 = "(";
            }
            if (words[1].contains("closing bracket")) {
                words[1] = words[1].replace("closing bracket", "");
                closingBracket1 = ")";
            }
            if ((num = convertWordToInt(words[0])) > Integer.MIN_VALUE) {
                firstParameter = num.toString();
            } else {
                firstParameter = variableName(words[0]);
            }
            if ((num = convertWordToInt(words[1])) > Integer.MIN_VALUE) {
                secondParameter = num.toString();
            } else {
                secondParameter = variableName(words[1]);
            }
            return openingBracket0 + firstParameter + closingBracket0 + " <= " + openingBracket1 + secondParameter + closingBracket1;
        }

        if (bool.contains("greater than or equal to")) {
            String[] words = bool.split("greater than or equal to");
            String firstParameter;
            String secondParameter;
            Integer num;
            String openingBracket0 = "", closingBracket0 = "";
            String openingBracket1 = "", closingBracket1 = "";
            if (words[0].contains("opening bracket")) {
                words[0] = words[0].replace("opening bracket", ""); //open ex
                openingBracket0 = "(";
            }
            if (words[0].contains("closing bracket")) {
                words[0] = words[0].replace("closing bracket", "");
                closingBracket0 = ")";
            }
            if (words[1].contains("opening bracket")) {
                words[1] = words[1].replace("opening bracket", "");
                openingBracket1 = "(";
            }
            if (words[1].contains("closing bracket")) {
                words[1] = words[1].replace("closing bracket", "");
                closingBracket1 = ")";
            }
            if ((num = convertWordToInt(words[0])) > Integer.MIN_VALUE) {
                firstParameter = num.toString();
            } else {
                firstParameter = variableName(words[0]);
            }
            if ((num = convertWordToInt(words[1])) > Integer.MIN_VALUE) {
                secondParameter = num.toString();
            } else {
                secondParameter = variableName(words[1]);
            }
            return openingBracket0 + firstParameter + closingBracket0 + " >= " + openingBracket1 + secondParameter + closingBracket1;
        }

        if (bool.contains("less than")) {
            String[] words = bool.split("less than");
            String firstParameter;
            String secondParameter;
            Integer num;
            String openingBracket0 = "", closingBracket0 = "";
            String openingBracket1 = "", closingBracket1 = "";
            if (words[0].contains("opening bracket")) {
                words[0] = words[0].replace("opening bracket", "");
                openingBracket0 = "(";
            }
            if (words[0].contains("closing bracket")) {
                words[0] = words[0].replace("closing bracket", "");
                closingBracket0 = ")";
            }
            if (words[1].contains("opening bracket")) {
                words[1] = words[1].replace("opening bracket", "");
                openingBracket1 = "(";
            }
            if (words[1].contains("closing bracket")) {
                words[1] = words[1].replace("closing bracket", "");
                closingBracket1 = ")";
            }
            if ((num = convertWordToInt(words[0])) > Integer.MIN_VALUE) {
                firstParameter = num.toString();
            } else {
                firstParameter = variableName(words[0]);
            }
            if ((num = convertWordToInt(words[1])) > Integer.MIN_VALUE) {
                secondParameter = num.toString();
            } else {
                secondParameter = variableName(words[1]);
            }
            return openingBracket0 + firstParameter + closingBracket0 + " < " + openingBracket1 + secondParameter + closingBracket1;
        }

        if (bool.contains("greater than")) {
            String[] words = bool.split("greater than");
            String firstParameter;
            String secondParameter;
            Integer num;
            String openingBracket0 = "", closingBracket0 = "";
            String openingBracket1 = "", closingBracket1 = "";
            if (words[0].contains("opening bracket")) {
                words[0] = words[0].replace("opening bracket", "");
                openingBracket0 = "(";
            }
            if (words[0].contains("closing bracket")) {
                words[0] = words[0].replace("closing bracket", "");
                closingBracket0 = ")";
            }
            if (words[1].contains("opening bracket")) {
                words[1] = words[1].replace("opening bracket", "");
                openingBracket1 = "(";
            }
            if (words[1].contains("closing bracket")) {
                words[1] = words[1].replace("closing bracket", "");
                closingBracket1 = ")";
            }
            if ((num = convertWordToInt(words[0])) > Integer.MIN_VALUE) {
                firstParameter = num.toString();
            } else {
                firstParameter = variableName(words[0]);
            }
            if ((num = convertWordToInt(words[1])) > Integer.MIN_VALUE) {
                secondParameter = num.toString();
            } else {
                secondParameter = variableName(words[1]);
            }
            return openingBracket0 + firstParameter + closingBracket0 + " > " + openingBracket1 + secondParameter + closingBracket1;
        }

        if (bool.contains("unequal to")) {
            String[] words = bool.split("unequal to");
            String firstParameter;
            String secondParameter;
            Integer num;
            String openingBracket0 = "", closingBracket0 = "";
            String openingBracket1 = "", closingBracket1 = "";
            if (words[0].contains("opening bracket")) {
                words[0] = words[0].replace("opening bracket", "");
                openingBracket0 = "(";
            }
            if (words[0].contains("closing bracket")) {
                words[0] = words[0].replace("closing bracket", "");
                closingBracket0 = ")";
            }
            if (words[1].contains("opening bracket")) {
                words[1] = words[1].replace("opening bracket", "");
                openingBracket1 = "(";
            }
            if (words[1].contains("closing bracket")) {
                words[1] = words[1].replace("closing bracket", "");
                closingBracket1 = ")";
            }
            if ((num = convertWordToInt(words[0])) > Integer.MIN_VALUE) {
                firstParameter = num.toString();
            } else {
                firstParameter = variableName(words[0]);
            }
            if ((num = num = convertWordToInt(words[1])) > 0) {
                secondParameter = num.toString();
            } else {
                secondParameter = variableName(words[1]);
            }
            return openingBracket0 + firstParameter + closingBracket0 + " != " + openingBracket1 + secondParameter + closingBracket1;
        }

        if (bool.contains(" not equal to ")) {
            String[] words = bool.split(" not equal to ");
            String firstParameter;
            String secondParameter;
            Integer num;
            String openingBracket0 = "", closingBracket0 = "";
            String openingBracket1 = "", closingBracket1 = "";
            if (words[0].contains("opening bracket")) {
                words[0] = words[0].replace("opening bracket", "");
                openingBracket0 = "(";
            }
            if (words[0].contains("closing bracket")) {
                words[0] = words[0].replace("closing bracket", "");
                closingBracket0 = ")";
            }
            if (words[1].contains("opening bracket")) {
                words[1] = words[1].replace("opening bracket", "");
                openingBracket1 = "(";
            }
            if (words[1].contains("closing bracket")) {
                words[1] = words[1].replace("closing bracket", "");
                closingBracket1 = ")";
            }
            if ((num = convertWordToInt(words[0])) > Integer.MIN_VALUE) {
                firstParameter = num.toString();
            } else {
                firstParameter = variableName(words[0]);
            }
            if ((num = convertWordToInt(words[1])) > Integer.MIN_VALUE) {
                secondParameter = num.toString();
            } else {
                secondParameter = variableName(words[1]);
            }
            return openingBracket0 + firstParameter + closingBracket0 + " != " + openingBracket1 + secondParameter + closingBracket1;
        }

        if (bool.contains(" equal to ")) {
            String[] words = bool.split(" equal to ");
            String firstParameter;
            String secondParameter;
            Integer num;
            String openingBracket0 = "", closingBracket0 = "";
            String openingBracket1 = "", closingBracket1 = "";
            if (words[0].contains("opening bracket")) {
                words[0] = words[0].replace("opening bracket", "");
                openingBracket0 = "(";
            }
            if (words[0].contains("closing bracket")) {
                words[0] = words[0].replace("closing bracket", "");
                closingBracket0 = ")";
            }
            if (words[1].contains("opening bracket")) {
                words[1] = words[1].replace("opening bracket", "");
                openingBracket1 = "(";
            }
            if (words[1].contains("closing bracket")) {
                words[1] = words[1].replace("closing bracket", "");
                closingBracket1 = ")";
            }
            if ((num = convertWordToInt(words[0])) > Integer.MIN_VALUE) {
                firstParameter = num.toString();
            } else {
                firstParameter = variableName(words[0]);
            }
            if ((num = convertWordToInt(words[1])) > Integer.MIN_VALUE) {
                secondParameter = num.toString();
            } else {
                secondParameter = variableName(words[1]);
            }
            return openingBracket0 + firstParameter + closingBracket0 + " == " + openingBracket1 + secondParameter + closingBracket1;
        }
        return bool;
    }

    public void createStatement(PsiElement superElement, PsiElement insertAfter, String text) {
        if (superElement != null && !text.isEmpty()) {
            PsiElement newVar = JavaPsiFacade.getElementFactory(GetCommandAction.project)
                    .createStatementFromText(text, GetCommandAction.psiClass);
            ApplicationManager.getApplication().invokeLater(
                    () -> {
                        WriteCommandAction.runWriteCommandAction(GetCommandAction.project, () -> {
                            superElement.addBefore(newVar, insertAfter);
                        });
                    });
        }
    }

    public void createStatementWithLineJumpForInitialization(PsiElement superElement, PsiElement
            insertAfter, ClassResolver.TypeAndName typeAndName, String text, int line) {
        if (superElement != null && typeAndName != null && !text.isEmpty() && line > -1) {
            PsiElement newVar = JavaPsiFacade.getElementFactory(GetCommandAction.project)
                    .createStatementFromText(text, GetCommandAction.psiClass);
            ApplicationManager.getApplication().invokeLater(
                    () -> {
                        WriteCommandAction.runWriteCommandAction(GetCommandAction.project, () -> {
                            if (typeAndName.getPsiClass() != null) {
                                GetCommandAction.psiJavaFile.importClass(typeAndName.getPsiClass());
                            }
                            superElement.addBefore(newVar, insertAfter);
                        });
                        goToLine(line + 1);
                    });
        }
    }

    public void createStatementWithLineJump(PsiElement superElement, PsiElement
            insertAfter, String text, int line) {
        if (superElement != null && !text.isEmpty() && line > -1) {
            PsiElement newVar = JavaPsiFacade.getElementFactory(GetCommandAction.project)
                    .createStatementFromText(text, GetCommandAction.psiClass);
            ApplicationManager.getApplication().invokeLater(
                    () -> {
                        WriteCommandAction.runWriteCommandAction(GetCommandAction.project, () -> {
                            superElement.addBefore(newVar, insertAfter);
                        });
                        goToLine(line + 1);
                    });
        }
    }

}
