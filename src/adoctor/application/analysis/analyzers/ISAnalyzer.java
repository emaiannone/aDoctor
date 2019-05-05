package adoctor.application.analysis.analyzers;

import adoctor.application.ast.ASTUtilities;
import adoctor.application.bean.Method;
import adoctor.application.bean.smell.ISSmell;
import adoctor.application.bean.smell.MethodSmell;
import javafx.util.Pair;
import org.eclipse.jdt.core.dom.*;

import java.util.ArrayList;
import java.util.List;

@SuppressWarnings("unchecked")
public class ISAnalyzer extends MethodSmellAnalyzer {
    //TODO Rimuovere il livello di metodo di tutti gli analizzatori e promuoverli tutti a livello di classe?
    // Cosa implica questo cambiemento?
    @Override
    public MethodSmell analyzeMethod(Method method) {
        if (method == null) {
            return null;
        }
        MethodDeclaration methodDecl = method.getMethodDecl();
        if (methodDecl == null) {
            return null;
        }

        CompilationUnit cUnit = (CompilationUnit) methodDecl.getRoot();
        TypeDeclaration typeDecl = (TypeDeclaration) cUnit.types().get(0);
        // Fetch all instance variables names
        List<Pair<Type, String>> instanceVars = getInstanceVariables(typeDecl);

        // Fetch all setters
        List<Pair<MethodDeclaration, String>> setters = getSetters(typeDecl, instanceVars);

        // Look for an invocation to a setter
        List<MethodInvocation> invocations = ASTUtilities.getMethodInvocations(methodDecl);
        if (invocations == null) {
            return null;
        }
        for (MethodInvocation invocation : invocations) {
            for (Pair<MethodDeclaration, String> setter : setters) {
                String setterName = setter.getKey().getName().getIdentifier();
                String invocationName = invocation.getName().getIdentifier();
                if (setterName.equals(invocationName)) {
                    List callArgs = invocation.arguments();
                    if (callArgs != null && callArgs.size() == 1) {
                        ISSmell isSmell = new ISSmell();
                        isSmell.setMethod(method);
                        isSmell.setSmellyCall(invocation);
                        isSmell.setSmellySetter(setter);
                        return isSmell;
                    }
                }
            }
        }
        return null;
    }

    private List<Pair<Type, String>> getInstanceVariables(TypeDeclaration typeDecl) {
        List<Pair<Type, String>> instanceVars = new ArrayList<>();
        for (FieldDeclaration field : typeDecl.getFields()) {
            List<VariableDeclarationFragment> fragments = (List<VariableDeclarationFragment>) field.fragments();
            for (VariableDeclarationFragment fragment : fragments) {
                instanceVars.add(new Pair<>(field.getType(), fragment.getName().getIdentifier()));
            }
        }
        return instanceVars;
    }

    private List<Pair<MethodDeclaration, String>> getSetters(TypeDeclaration typeDecl
            , List<Pair<Type, String>> instanceVars) {
        MethodDeclaration[] methods = typeDecl.getMethods();
        List<Pair<MethodDeclaration, String>> setters = new ArrayList<>();
        for (MethodDeclaration methodDecl : methods) {
            // Return and number of arguments check
            if (setterSignatureCheck(methodDecl)) {
                SingleVariableDeclaration arg = (SingleVariableDeclaration) methodDecl.parameters().get(0);
                String methodName = methodDecl.getName().getIdentifier();
                boolean found = false;
                for (int i = 0; i < instanceVars.size() && !found; i++) {
                    // Argument type check
                    Pair<Type, String> instanceVar = instanceVars.get(i);
                    Type instanceVarType = instanceVar.getKey();
                    if (arg.getType().toString().equals(instanceVarType.toString())) {
                        // Setter name check
                        String instanceVarName = instanceVar.getValue();
                        char varFirstLetter = instanceVarName.charAt(0);
                        if (methodName.equals("set" + Character.toUpperCase(varFirstLetter) + instanceVarName
                                .substring(1))) {
                            // Assignment check
                            if (setterAssignmentCheck(methodDecl, instanceVarName)) {
                                Pair<MethodDeclaration, String> couple = new Pair<>(methodDecl, instanceVarName);
                                setters.add(couple);
                                found = true;
                            }
                        }
                    }
                }
            }
        }
        return setters;
    }

    private boolean setterSignatureCheck(MethodDeclaration methodDecl) {
        Type retType = methodDecl.getReturnType2();
        if (retType != null && retType.isPrimitiveType() && ((PrimitiveType) retType).getPrimitiveTypeCode()
                .equals(PrimitiveType.VOID)) {
            List<SingleVariableDeclaration> arguments = methodDecl.parameters();
            return arguments.size() == 1;
        }
        return false;
    }

    private boolean setterAssignmentCheck(MethodDeclaration methodDecl, String instanceVarName) {
        // Assignment check
        List<Statement> statements = methodDecl.getBody().statements();
        for (Statement statement : statements) {
            if (statement instanceof ExpressionStatement) {
                Expression expr = ((ExpressionStatement) statement).getExpression();
                if (expr instanceof Assignment) {
                    Expression leftExpr = ((Assignment) expr).getLeftHandSide();
                    Expression rightExpr = ((Assignment) expr).getRightHandSide();
                    if (rightExpr.toString().equals(instanceVarName)) {
                        return leftExpr.toString().equals(instanceVarName)
                                || leftExpr instanceof FieldAccess
                                && ((FieldAccess) leftExpr).getExpression().toString().equals("this")
                                && ((FieldAccess) leftExpr).getName().getIdentifier().equals(instanceVarName);
                    }
                }
            }
        }
        return false;
    }
}