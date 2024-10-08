package org.example;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.LiteralExpr;
import com.github.javaparser.ast.nodeTypes.NodeWithStatements;
import com.github.javaparser.ast.stmt.*;
import com.github.javaparser.ast.comments.Comment;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;

public class XmlTestConversor {

    public static void main(String[] args) {
        String filePath = "C:\\Users\\andre\\Desktop\\junit5-assertions-examples\\src\\test\\java\\com\\howtoprogram\\junit5\\StringUtilsTestUnit5.java";
        String outputFilePath = "output.xml";

        try {
            CompilationUnit cu = parseJavaFile(filePath);
            String output = processCompilationUnit(cu);
            saveToFile(output, outputFilePath);
        } catch (FileNotFoundException e) {
            System.err.println("Arquivo não encontrado: " + e.getMessage());
        } catch (IOException e) {
            System.err.println("Erro ao manipular arquivo: " + e.getMessage());
        }
    }

    private static CompilationUnit parseJavaFile(String filePath) throws FileNotFoundException {
        File file = new File(filePath);
        if (!file.exists()) {
            throw new FileNotFoundException("Arquivo não encontrado: " + filePath);
        }
        return StaticJavaParser.parse(file);
    }

    private static String processCompilationUnit(CompilationUnit cu) {
        StringBuilder outputBuilder = new StringBuilder();
        cu.findAll(MethodDeclaration.class).forEach(method -> {
            String methodName = method.getNameAsString();
            outputBuilder.append("<test_method name=\"").append(methodName).append("\">\n");

            if (method.getBody().isPresent() && method.getBody().get().getStatements().isEmpty()) {
                outputBuilder.append("\t<empty/>\n");
            } else {
                processStatements(method.getBody().get(), outputBuilder);
            }

            outputBuilder.append("</test_method>\n\n");
        });

        return outputBuilder.toString();
    }


    private static void processStatements(NodeWithStatements<?> nodeWithStatements, StringBuilder outputBuilder) {
        List<Statement> statements = nodeWithStatements.getStatements();
        int lastLineProcessed = -1;

        for (Statement stmt : statements) {
            int currentLine = stmt.getBegin().map(begin -> begin.line).orElse(-1);

            // Processar comentários
            Optional<Comment> commentOpt = stmt.getComment();
            if (commentOpt.isPresent() && currentLine != lastLineProcessed) {
                String commentContent = "\t<comment>" + commentOpt.get().getContent() + "</comment>\n";
                outputBuilder.append(commentContent);
                lastLineProcessed = currentLine;
            }

            // Processar outras lógicas específicas, se houver
            if (stmt instanceof ExpressionStmt) {
                ExpressionStmt exprStmt = (ExpressionStmt) stmt;
                processExpressionStmt(exprStmt, outputBuilder);
            } else if (stmt instanceof ForStmt) {
                ForStmt forStmt = (ForStmt) stmt;
                processForStatement(forStmt, outputBuilder);
            } else if (stmt instanceof IfStmt) {
                IfStmt ifStmt = (IfStmt) stmt;
                processIfStatement(ifStmt, outputBuilder);
            } else if (stmt instanceof TryStmt) {
                TryStmt tryStmt = (TryStmt) stmt;
                processTryStatement(tryStmt, outputBuilder);
            } else {
                // Tratar o que sobra como declarações regulares
                if (currentLine != lastLineProcessed) {
                    String statementContent = "\t<statement>" + stmt.toString() + "</statement>\n";
                    outputBuilder.append(statementContent);
                    lastLineProcessed = currentLine;
                }
            }
        }

        // Processamento do que sobra como declarações regulares
        for (Statement stmt : statements) {
            Optional<Comment> commentOpt = stmt.getComment();
            int currentLine = stmt.getBegin().map(begin -> begin.line).orElse(-1);

            if (!(stmt instanceof ExpressionStmt || stmt instanceof ForStmt || stmt instanceof IfStmt || stmt instanceof TryStmt)) {
                if (commentOpt.isPresent() && currentLine == lastLineProcessed) {
                    String commentContent = "\t<comment>" + commentOpt.get().getContent() + "</comment>\n";
                    outputBuilder.append(commentContent);
                } else {
                    String statementContent = "\t<statement>" + stmt.toString() + "</statement>\n";
                    outputBuilder.append(statementContent);
                }

                lastLineProcessed = currentLine;
            }
        }
    }


    private static void processExpressionStmt(ExpressionStmt exprStmt, StringBuilder outputBuilder) {
        if (exprStmt.getExpression() instanceof MethodCallExpr) {
            MethodCallExpr methodCall = (MethodCallExpr) exprStmt.getExpression();
            processMethodCall(methodCall, outputBuilder);
        } else {
            outputBuilder.append("\t<statement>").append(exprStmt.toString()).append("</statement>\n");
        }
    }

    private static void processMethodCall(MethodCallExpr methodCall, StringBuilder outputBuilder) {
        List<String> assertMethods = Arrays.asList(
                "assertEquals", "assertNotEquals", "assertTrue", "assertFalse",
                "assertNull", "assertNotNull", "assertSame", "assertNotSame",
                "assertArrayEquals", "assertThrows"
        );

        if (assertMethods.contains(methodCall.getNameAsString())) {
            outputBuilder.append("\t<assert>").append(methodCall.toString()).append("</assert>\n");
            processAssertionLiterals(methodCall, outputBuilder);
            checkForAssertionSmells(methodCall, outputBuilder);
        } else if (methodCall.toString().startsWith("System.out.println")) {
            processPrintStatement(methodCall, outputBuilder);
        } else {
            outputBuilder.append("\t<methodCall>").append(methodCall.toString()).append("</methodCall>\n");
        }
    }

    private static void processAssertionLiterals(MethodCallExpr expr, StringBuilder outputBuilder) {
        boolean hasNumericLiteral = expr.getArguments().stream()
                .filter(arg -> arg instanceof LiteralExpr)
                .map(arg -> (LiteralExpr) arg)
                .anyMatch(XmlTestConversor::isNumericLiteral);

        if (hasNumericLiteral) {
            outputBuilder.append("\t<assert_literals>\n");
            expr.getArguments().forEach(arg -> {
                if (arg instanceof LiteralExpr && isNumericLiteral((LiteralExpr) arg)) {
                    outputBuilder.append("\t\t<literal type=\"number\">")
                            .append(arg.toString())
                            .append("</literal>\n");
                }
            });
            outputBuilder.append("\t</assert_literals>\n");
        }
    }

    private static void checkForAssertionSmells(MethodCallExpr expr, StringBuilder outputBuilder) {
        List<String> assertMethods = Arrays.asList(
                "assertEquals", "assertNotEquals", "assertTrue", "assertFalse",
                "assertNull", "assertNotNull", "assertSame", "assertNotSame",
                "assertArrayEquals", "assertThrows"
        );

        boolean hasAssertionRoulette = false;
        Set<String> uniqueAssertions = new HashSet<>();
        Set<String> duplicatedAssertions = new HashSet<>();

        if (assertMethods.contains(expr.getNameAsString())) {
            if (expr.getArguments().size() < 3) {
                if (uniqueAssertions.contains(expr.toString())) {
                    hasAssertionRoulette = true;
                }
                uniqueAssertions.add(expr.toString());
            } else {
                String assertionSignature = expr.toString();
                if (!uniqueAssertions.add(assertionSignature)) {
                    duplicatedAssertions.add(assertionSignature);
                }
            }
        }

        if (hasAssertionRoulette) {
            outputBuilder.append("\t<assertion_roulette/>\n");
        }

        if (!duplicatedAssertions.isEmpty()) {
            outputBuilder.append("\t<duplicated_asserts>\n");
            duplicatedAssertions.forEach(assertion -> outputBuilder.append("\t\t<duplicated_assert>").append(assertion).append("</duplicated_assert>\n"));
            outputBuilder.append("\t</duplicated_asserts>\n");
        }
    }

    private static void processForStatement(ForStmt forStmt, StringBuilder outputBuilder) {
        String condition = forStmt.getInitialization().toString() + "; " +
                forStmt.getCompare().map(Object::toString).orElse("") + "; " +
                forStmt.getUpdate().toString();
        outputBuilder.append("\t<loopFor condition=\"").append(condition).append("\">\n");
        processStatements((NodeWithStatements<?>) forStmt.getBody(), outputBuilder);
        outputBuilder.append("\t</loopFor>\n");
    }

    private static void processIfStatement(IfStmt ifStmt, StringBuilder outputBuilder) {
        String condition = ifStmt.getCondition().toString();
        outputBuilder.append("\t<if condition=\"").append(condition).append("\">\n");
        processStatements((NodeWithStatements<?>) ifStmt.getThenStmt(), outputBuilder);
        if (ifStmt.getElseStmt().isPresent()) {
            outputBuilder.append("\t<else>\n");
            processStatements((NodeWithStatements<?>) ifStmt.getElseStmt().get(), outputBuilder);
            outputBuilder.append("\t</else>\n");
        }
        outputBuilder.append("\t</if>\n");
    }

    private static void processTryStatement(TryStmt tryStmt, StringBuilder outputBuilder) {
        outputBuilder.append("\t<try>\n");
        processStatements(tryStmt.getTryBlock(), outputBuilder);
        tryStmt.getCatchClauses().forEach(catchClause -> {
            outputBuilder.append("\t\t<catch>\n");
            processStatements(catchClause.getBody(), outputBuilder);
            outputBuilder.append("\t\t</catch>\n");
        });
        if (tryStmt.getFinallyBlock().isPresent()) {
            outputBuilder.append("\t\t<finally>\n");
            processStatements(tryStmt.getFinallyBlock().get(), outputBuilder);
            outputBuilder.append("\t\t</finally>\n");
        }
        outputBuilder.append("\t</try>\n");
    }

    private static void processPrintStatement(MethodCallExpr methodCall, StringBuilder outputBuilder) {
        String content = methodCall.getArguments().toString().trim();
        outputBuilder.append("\t<print>\n");
        outputBuilder.append("\t\t<printStmt>").
                append(content).append("</printStmt>\n");
        outputBuilder.append("\t</print>\n");
    }

    private static boolean isNumericLiteral(LiteralExpr literalExpr) {
        return literalExpr.isDoubleLiteralExpr() ||
                literalExpr.isIntegerLiteralExpr() ||
                literalExpr.isLongLiteralExpr() ||
                literalExpr.isCharLiteralExpr();
    }

    private static void saveToFile(String content, String filePath) throws IOException {
        try (FileWriter writer = new FileWriter(filePath)) {
            writer.write(content);
            System.out.println("Conteúdo salvo com sucesso em " + filePath);
        }
    }
}

