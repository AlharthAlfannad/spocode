package edu.tu.dortmund.cs.ls5.spocode;
import com.intellij.codeInsight.completion.AllClassesGetter;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.impl.search.AllClassesSearchExecutor;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.Processor;
import org.apache.commons.text.CaseUtils;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

class ClassResolver {
    static TypeAndName resolve(ArrayList<String> names, Project project, PsiClass context) {
        for (int i = names.size() - 1; i > 0; i--) {
            // @todo resolve packaging: declare variable com package class name var name

            String className = concat(names, 0, i, true);
            String elementName = concat(names, i, names.size(), false);
            switch (className) { // it starts from the upper case
                case "Int":
                case "Integer":
                    return new TypeAndName(null, PsiType.INT, elementName);
                case "Double":
                    return new TypeAndName(null, PsiType.DOUBLE, elementName);
                case "Boolean":
                    return new TypeAndName(null, PsiType.BOOLEAN, elementName);
                case "Float":
                    return new TypeAndName(null, PsiType.FLOAT, elementName);
                case "Byte":
                    return new TypeAndName(null, PsiType.BYTE, elementName);
                case "Long":
                    return new TypeAndName(null, PsiType.LONG, elementName);
                case "Char":
                    return new TypeAndName(null, PsiType.CHAR, elementName);
            }

            TypeAndPsiClass result = processFirstNotNull(
                    () -> resolveInnerClasses(className, project, context),
                    () -> resolveLocalClasses(className, project, context),
                    () -> resolveImportedClasses(className, project, context),
                    () -> resolveLibraryClasses(className, project),
                    () -> resolveAllClasses(className, project, context)
            );
            if (result != null) {
                return new TypeAndName(result.psiClass, result.type, elementName);
            }
        }
        return null;
    }

    static TypeAndName resolveForMethod(ArrayList<String> names, Project project, PsiClass context) {
        for (int i = names.size() - 1; i > 0; i--) {
            // @todo resolve packaging: declare variable com package class name var name

            String className = concat(names, 0, i, true);
            String elementName = concat(names, i, names.size(), false);
            switch (className) { // it starts from the upper case
                case "Int":
                    return new TypeAndName(null, PsiType.INT, elementName);
                case "Double":
                    return new TypeAndName(null, PsiType.DOUBLE, elementName);
                case "Boolean":
                    return new TypeAndName(null, PsiType.BOOLEAN, elementName);
                case "Void":
                    return new TypeAndName(null, PsiType.VOID, elementName);
                case "Float":
                    return new TypeAndName(null, PsiType.FLOAT, elementName);
                case "Byte":
                    return new TypeAndName(null, PsiType.BYTE, elementName);
                case "Long":
                    return new TypeAndName(null, PsiType.LONG, elementName);
                case "Char":
                    return new TypeAndName(null, PsiType.CHAR, elementName);
            }

            TypeAndPsiClass result = processFirstNotNull(
                    () -> resolveInnerClasses(className, project, context),
                    () -> resolveLocalClasses(className, project, context),
                    () -> resolveImportedClasses(className, project, context),
                    () -> resolveLibraryClasses(className, project),
                    () -> resolveAllClasses(className, project, context)
            );
            if (result != null) {
                return new TypeAndName(result.psiClass, result.type, elementName);
            }
        }
        return null;
    }

    private static PsiClass getClass(PsiElement element) {
        PsiElement current = element;
        while (current != null) {
            if (current instanceof PsiClass) {
                return (PsiClass) current;
            }
            current = current.getParent();
        }
        return null;
    }

    static PsiClassType resolveInnerClasses(String name, Project project, PsiClass psiClass) {
        if (psiClass == null) {
            return null;
        }
        return getMatchedClass(name, project, psiClass.getInnerClasses());
    }

    static PsiClassType resolveLocalClasses(String name, Project project, PsiClass psiClass) {
        if (psiClass == null) {
            return null;
        }
        return getMatchedClass(name, project, ((PsiJavaFile) psiClass.getContainingFile()).getClasses());
    }

    static PsiClassType resolveImportedClasses(String name, Project project, PsiClass psiClass) {
        if (psiClass == null) {
            return null;
        }
        PsiImportList importList = ((PsiJavaFile) psiClass.getContainingFile()).getImportList();
        assert importList != null;
        for (PsiImportStatementBase statement : importList.getAllImportStatements()) {
            if (statement.isOnDemand()) { // package
                PsiPackage importedPackage = (PsiPackage) statement.resolve();
                if (importedPackage == null) {
                    return null;
                }
                return PsiType.getTypeByName(importedPackage.getName() + "." + name,
                        project, GlobalSearchScope.allScope(project));
            } else if (statement.resolve() instanceof PsiClass) { // imported class
                PsiClass importedClass = (PsiClass) statement.resolve();
                if (importedClass == null || !name.equals(importedClass.getName())) {
                    return null;
                }
                return PsiType.getTypeByName(Objects.requireNonNull(importedClass.getQualifiedName()),
                        project, GlobalSearchScope.allScope(project));
            }
        }
        return null;
    }

    static PsiClassType resolveLibraryClasses(String name, Project project) {
        final String[] packages = {"java.lang.", "java.util.", "java.io.", "java.math.", "java.net.", "java.nio.", "java.time."};
        for (String pkg : packages) {
            PsiClassType type = PsiType.getTypeByName(pkg + name, project, GlobalSearchScope.allScope(project));
            if (type.resolve() != null) {
                return type;
            }
        }
        return null;
    }

    static PsiClassType resolveAllClasses(String name, Project project, PsiElement context) {
        final PsiClassType[] result = { null };
        Processor<PsiClass> processor = psiClass -> {
            if (context == null || AllClassesGetter.isAcceptableInContext(context, psiClass, true, true)) {
                result[0] = PsiType.getTypeByName(psiClass.getQualifiedName(), project, GlobalSearchScope.everythingScope(project));
                return false;
            }
            return true;
        };
        AllClassesSearchExecutor.processClassesByNames(
                project,
                GlobalSearchScope.everythingScope(project),
                Collections.singletonList(name),
                processor
        );
        return result[0];
    }

    private static PsiClassType getMatchedClass(String name, Project project, PsiClass[] classes) {
        for (PsiClass psiClass : classes) {
            if (name.equals(psiClass.getName())) {
                return PsiType.getTypeByName(psiClass.getQualifiedName(), project, GlobalSearchScope.projectScope(project));
            }
        }
        return null;
    }

    static TypeAndPsiClass processFirstNotNull(Supplier<PsiClassType>... types) {
        for (Supplier<PsiClassType> supplier : types) {
            PsiClassType type = supplier.get();
            if (type != null) {
                PsiClass psiClass = type.resolve();
                if (psiClass != null) {
                    return new TypeAndPsiClass(psiClass, type);
                }
            }
        }
        return null;
    }

    private static String concat(List<String> strings, int from, int to, boolean capitalizeFirst) {
        return CaseUtils.toCamelCase(String.join(" ", strings.subList(from, to)),
                capitalizeFirst, ' ');
    }

    static class TypeAndPsiClass {
        final PsiClass psiClass;
        final PsiType type;

        private TypeAndPsiClass(PsiClass psiClass, PsiType type) {
            this.psiClass = psiClass;
            this.type = type;
        }
    }

    public static class TypeAndName {
        private final PsiClass psiClass;
        private final PsiType type;
        private final String name;

        TypeAndName(PsiClass psiClass, PsiType type, String name) {
            this.psiClass = psiClass;
            this.type = type;
            this.name = name;
        }

        public PsiClass getPsiClass() {
            return psiClass;
        }

        public PsiType getType() {
            return type;
        }

        public String typeAsString(){ return type.getPresentableText();}

        public String getName() {
            return name;
        }
    }
}
