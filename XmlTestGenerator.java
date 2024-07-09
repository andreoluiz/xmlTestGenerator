package com.howtoprogram.junit5;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.comments.Comment;
import com.github.javaparser.ast.expr.LiteralExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.stmt.*;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class XmlTestGenerator {

    private static final String DIRECTORY_PATH = "C:\\Users\\andre\\Downloads\\accumulo-main\\accumulo-main\\test\\src\\test\\java\\org\\apache\\accumulo";

    public static void main(String[] args) {
        List<File> javaFiles = listJavaFiles(DIRECTORY_PATH);

        for (File file : javaFiles) {
            try {
                CompilationUnit cu = parseJavaFile(file);
                String output = processCompilationUnit(cu);
                if (!output.isEmpty()) {
                    saveToFile(output, file.getPath().replace(".java", ".xml"));
                }
            } catch (FileNotFoundException e) {
                System.err.println("Arquivo não encontrado: " + e.getMessage());
            } catch (IOException e) {
                System.err.println("Erro ao manipular arquivo: " + e.getMessage());
            }
        }
    }

    private static List<File> listJavaFiles(String directoryPath) {
        List<File> javaFiles = new ArrayList<>();
        listJavaFilesRecursive(new File(directoryPath), javaFiles);
        return javaFiles;
    }

    private static void listJavaFilesRecursive(File directory, List<File> javaFiles) {
        if (directory.isDirectory()) {
            File[] files = directory.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isDirectory()) {
                        listJavaFilesRecursive(file, javaFiles);
                    } else if (file.getName().endsWith(".java")) {
                        javaFiles.add(file);
                    }
                }
            }
        }
    }

    private static CompilationUnit parseJavaFile(File file) throws FileNotFoundException {
        return StaticJavaParser.parse(file);
    }

    private static String processCompilationUnit(CompilationUnit cu) {
        StringBuilder outputBuilder = new StringBuilder();
        cu.findAll(MethodDeclaration.class).forEach(method -> {
            if (method.isAnnotationPresent("Test")) {
                String methodName = method.getNameAsString();
                outputBuilder.append("<test_method name=\"").append(methodName).append("\">\n");

                if (method.getBody().isPresent()) {
                    processStatements(method.getBody().get(), outputBuilder);
                } else {
                    outputBuilder.append("\t<empty/>\n");
                }

                outputBuilder.append("</test_method>\n\n");
            }
        });
        return outputBuilder.toString();
    }

    private static void processStatements(BlockStmt nodeWithStatements, StringBuilder outputBuilder) {
        List<Statement> statements = nodeWithStatements.getStatements();
        int lastLineProcessed = -1;

        for (Statement stmt : statements) {
            int currentLine = stmt.getBegin().map(begin -> begin.line).orElse(-1);

            Optional<Comment> commentOpt = stmt.getComment();
            if (commentOpt.isPresent() && currentLine != lastLineProcessed) {
                outputBuilder.append("\t<comment>").append(commentOpt.get().getContent()).append("</comment>\n");
                lastLineProcessed = currentLine;
            }

            if (stmt instanceof ExpressionStmt) {
                processExpressionStmt((ExpressionStmt) stmt, outputBuilder);
            } else if (stmt instanceof ForStmt) {
                processForStatement((ForStmt) stmt, outputBuilder);
            } else if (stmt instanceof ForEachStmt) {
                processForEachStatement((ForEachStmt) stmt, outputBuilder); // Adicionar esta linha
            } else if (stmt instanceof IfStmt) {
                processIfStatement((IfStmt) stmt, outputBuilder);
            } else if (stmt instanceof WhileStmt) {
                processWhileStatement((WhileStmt) stmt, outputBuilder);
            } else if (stmt instanceof TryStmt) {
                processTryStatement((TryStmt) stmt, outputBuilder);
            } else {
                outputBuilder.append("\t<statement>").append(stmt.toString()).append("</statement>\n");
                lastLineProcessed = currentLine;
            }
        }
    }


    private static void processExpressionStmt(ExpressionStmt exprStmt, StringBuilder outputBuilder) {
        if (exprStmt.getExpression() instanceof MethodCallExpr) {
            processMethodCall((MethodCallExpr) exprStmt.getExpression(), outputBuilder);
        } else {
            outputBuilder.append("\t<statement>").append(exprStmt.toString()).append("</statement>\n");
        }
    }

    private static void processMethodCall(MethodCallExpr methodCall, StringBuilder outputBuilder) {
        List<String> assertMethods = List.of(
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
                .anyMatch(XmlTestGenerator::isNumericLiteral);

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
        // Simplificação para checagem de "assertion smells"
        outputBuilder.append("\t<assertion_check>").append(expr.toString()).append("</assertion_check>\n");
    }

    private static void processPrintStatement(MethodCallExpr expr, StringBuilder outputBuilder) {
        outputBuilder.append("\t<print>").append(expr.toString()).append("</print>\n");
    }

    private static boolean isNumericLiteral(LiteralExpr literalExpr) {
        try {
            Double.parseDouble(literalExpr.toString());
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static void processForStatement(ForStmt stmt, StringBuilder outputBuilder) {
        outputBuilder.append("\t<for>\n");
        processStatements((BlockStmt) stmt.getBody(), outputBuilder); // Corrigido aqui
        outputBuilder.append("\t</for>\n");
    }

    private static void processForEachStatement(ForEachStmt stmt, StringBuilder outputBuilder) {
        outputBuilder.append("\t<forEach>\n");
        outputBuilder.append("\t\t<variable>").append(stmt.getVariable()).append("</variable>\n");
        outputBuilder.append("\t\t<iterable>").append(stmt.getIterable()).append("</iterable>\n");
        processStatements((BlockStmt) stmt.getBody(), outputBuilder);
        outputBuilder.append("\t</forEach>\n");
    }


    private static void processIfStatement(IfStmt stmt, StringBuilder outputBuilder) {
        outputBuilder.append("\t<if>").append(stmt.getCondition().toString()).append("\n");
        processStatements((BlockStmt) stmt.getThenStmt(), outputBuilder); // Corrigido aqui
        stmt.getElseStmt().ifPresent(elseStmt -> processStatements((BlockStmt) elseStmt, outputBuilder));
        outputBuilder.append("\t</if>\n");
    }

    private static void processWhileStatement(WhileStmt stmt, StringBuilder outputBuilder) {
        outputBuilder.append("\t<while>\n");
        processStatements((BlockStmt) stmt.getBody(), outputBuilder); // Corrigido aqui
        outputBuilder.append("\t</while>\n");
    }

    private static void processTryStatement(TryStmt stmt, StringBuilder outputBuilder) {
        outputBuilder.append("\t<try>\n");
        processStatements(stmt.getTryBlock(), outputBuilder); // Corrigido aqui
        stmt.getCatchClauses().forEach(catchClause -> processStatements(catchClause.getBody(), outputBuilder));
        stmt.getFinallyBlock().ifPresent(finallyBlock -> processStatements(finallyBlock, outputBuilder));
        outputBuilder.append("\t</try>\n");
    }

    private static void saveToFile(String content, String filePath) throws IOException {
        try (FileWriter writer = new FileWriter(filePath)) {
            writer.write(content);
        }
    }
}
