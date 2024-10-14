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
        String directoryPath = "C:\\Users\\nacla\\Downloads\\exhibitor-master\\exhibitor-master\\exhibitor-core\\src\\test\\java\\com\\netflix\\exhibitor\\core";
        String outputDirectoryPath = "saida";

        File directory = new File(directoryPath);
        if (!directory.isDirectory()) {
            System.err.println("O caminho fornecido não é uma pasta válida.");
            return;
        }

        File outputDirectory = new File(outputDirectoryPath);
        if (!outputDirectory.exists()) {
            outputDirectory.mkdir();
        }

        processFilesRecursively(directory, outputDirectory);
        System.out.println("Arquivos de teste XML gerados com sucesso.");
    }

    private static void processFilesRecursively(File directory, File outputDirectory) {
        File[] files = directory.listFiles((File dir, String name) -> name.endsWith(".java"));
        if (files != null) {
            for (File file : files) {
                try {
                    CompilationUnit cu = parseJavaFile(file.getAbsolutePath());
                    processCompilationUnit(cu, outputDirectory, file.getAbsolutePath()); // Passando o caminho do arquivo
                } catch (FileNotFoundException e) {
                    System.err.println("Arquivo não encontrado: " + e.getMessage());
                }
            }
        }

        File[] subdirectories = directory.listFiles(File::isDirectory);
        if (subdirectories != null) {
            for (File subdirectory : subdirectories) {
                processFilesRecursively(subdirectory, outputDirectory);
            }
        }
    }

    private static CompilationUnit parseJavaFile(String filePath) throws FileNotFoundException {
        File file = new File(filePath);
        if (!file.exists()) {
            throw new FileNotFoundException("Arquivo não encontrado: " + filePath);
        }
        return StaticJavaParser.parse(file);
    }

    private static void processCompilationUnit(CompilationUnit cu, File outputDirectory, String filePath) { // Novo parâmetro filePath
        cu.findAll(MethodDeclaration.class).forEach(method -> {
            boolean hasTestAnnotation = method.getAnnotations().stream()
                    .anyMatch(annotation -> annotation.getNameAsString().equals("Test"));

            if (!hasTestAnnotation) {
                return;
            }

            String methodName = method.getNameAsString();
            StringBuilder outputBuilder = new StringBuilder();
            outputBuilder.append("<test_method name=\"").append(methodName).append("\">\n");

            // Adicionando o caminho do arquivo onde o teste foi encontrado
            outputBuilder.append("\t<file_path>").append(filePath).append("</file_path>\n");

            if (method.getBody().isPresent() && method.getBody().get().getStatements().isEmpty()) {
                outputBuilder.append("\t<empty/>\n");
            } else {
                processStatements(method.getBody().get(), outputBuilder);
            }

            outputBuilder.append("</test_method>\n");
            saveToFile(outputBuilder.toString(), new File(outputDirectory, methodName + ".xml"));
        });
    }


    private static void processStatements(NodeWithStatements<?> nodeWithStatements, StringBuilder outputBuilder) {
        List<Statement> statements = nodeWithStatements.getStatements();
        Set<String> processedStatements = new HashSet<>();

        for (Statement stmt : statements) {
            String statementContent = stmt.toString();

            if (processedStatements.contains(statementContent)) {
                continue;
            }

            processedStatements.add(statementContent);

            // Escapar <, > e &
            statementContent = statementContent.replace("&", "&amp;")
                    .replace("<", "&lt;")
                    .replace(">", "&gt;");

            outputBuilder.append("\t<statement>").append(statementContent).append("</statement>\n");

            if (stmt instanceof ExpressionStmt) {
                processExpressionStmt((ExpressionStmt) stmt, outputBuilder);
            } else if (stmt instanceof ForStmt) {
                processForStatement((ForStmt) stmt, outputBuilder);
            } else if (stmt instanceof IfStmt) {
                processIfStatement((IfStmt) stmt, outputBuilder);
            } else if (stmt instanceof TryStmt) {
                processTryStatement((TryStmt) stmt, outputBuilder);
            }
        }
    }


    private static void processExpressionStmt(ExpressionStmt exprStmt, StringBuilder outputBuilder) {
        if (exprStmt.getExpression() instanceof MethodCallExpr) {
            MethodCallExpr methodCall = (MethodCallExpr) exprStmt.getExpression();

            if (methodCall.toString().contains("Lists.newArrayList")) {
                return;
            }

            if (!methodCall.toString().startsWith("System.out.println")) {
                return;
            }
        } else {
            return;
        }
    }

    private static void processMethodCall(MethodCallExpr methodCall, StringBuilder outputBuilder) {
        if (methodCall.toString().startsWith("System.out.println")) {
            processPrintStatement(methodCall, outputBuilder);
        } else {
            outputBuilder.append("\t<methodCall>").append(methodCall.toString()).append("</methodCall>\n");
        }
    }

    private static void processForStatement(ForStmt forStmt, StringBuilder outputBuilder) {
        outputBuilder.append("\t<LoopFor>\n");  // Alterado para apenas <LoopFor>
        processStatements((NodeWithStatements<?>) forStmt.getBody(), outputBuilder);
        outputBuilder.append("\t</LoopFor>\n");
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
        String content = methodCall.getArguments().toString().replaceAll("[\\[\\]]", "").trim();
        outputBuilder.append("\t<print>").append(content).append("</print>\n");
    }

    private static boolean isNumericLiteral(LiteralExpr literalExpr) {
        return literalExpr.isDoubleLiteralExpr() ||
                literalExpr.isIntegerLiteralExpr() ||
                literalExpr.isLongLiteralExpr() ||
                literalExpr.isCharLiteralExpr();
    }

    private static void saveToFile(String content, File file) {
        try (FileWriter writer = new FileWriter(file)) {
            writer.write(content);
            System.out.println("Conteúdo salvo com sucesso em " + file.getAbsolutePath());
        } catch (IOException e) {
            System.err.println("Erro ao salvar arquivo " + file.getAbsolutePath() + ": " + e.getMessage());
        }
    }
}
