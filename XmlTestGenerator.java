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
        String directoryPath = "C:\\Users\\nacla\\Downloads\\exhibitor-master\\exhibitor-master\\exhibitor-core\\src\\test\\java\\com\\netflix\\exhibitor\\core"; // Caminho para a pasta
        String outputDirectoryPath = "output_tests"; // Caminho para a pasta onde os arquivos XML serão salvos

        File directory = new File(directoryPath);
        if (!directory.isDirectory()) {
            System.err.println("O caminho fornecido não é uma pasta válida.");
            return;
        }

        // Cria a pasta de saída se não existir
        File outputDirectory = new File(outputDirectoryPath);
        if (!outputDirectory.exists()) {
            outputDirectory.mkdir();
        }

        // Listar todos os arquivos .java no diretório e subdiretórios
        processFilesRecursively(directory, outputDirectory);

        System.out.println("Arquivos de teste XML gerados com sucesso.");
    }

    private static void processFilesRecursively(File directory, File outputDirectory) {
        // Lista todos os arquivos .java no diretório atual
        File[] files = directory.listFiles((File dir, String name) -> name.endsWith(".java"));
        if (files != null) {
            for (File file : files) {
                try {
                    // Processa cada arquivo Java encontrado
                    CompilationUnit cu = parseJavaFile(file.getAbsolutePath());
                    // Processa a unidade de compilação e gera os arquivos XML
                    processCompilationUnit(cu, outputDirectory);
                } catch (FileNotFoundException e) {
                    System.err.println("Arquivo não encontrado: " + e.getMessage());
                }
            }
        }

        // Agora, explora os subdiretórios recursivamente
        File[] subdirectories = directory.listFiles(File::isDirectory);
        if (subdirectories != null) {
            for (File subdirectory : subdirectories) {
                processFilesRecursively(subdirectory, outputDirectory); // Chama recursivamente para subdiretórios
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

    private static void processCompilationUnit(CompilationUnit cu, File outputDirectory) {
        // Encontra todos os métodos na unidade de compilação
        cu.findAll(MethodDeclaration.class).forEach(method -> {
            // Verifica se o método tem a anotação @Test
            boolean hasTestAnnotation = method.getAnnotations().stream()
                    .anyMatch(annotation -> annotation.getNameAsString().equals("Test"));

            // Se o método não tiver @Test, pula o processamento
            if (!hasTestAnnotation) {
                return;
            }

            String methodName = method.getNameAsString();
            StringBuilder outputBuilder = new StringBuilder();
            outputBuilder.append("<test_method name=\"").append(methodName).append("\">\n");

            // Verifica se o corpo do método está presente e se ele está vazio
            if (method.getBody().isPresent() && method.getBody().get().getStatements().isEmpty()) {
                outputBuilder.append("\t<empty/>\n");
            } else {
                // Processa as declarações do método
                processStatements(method.getBody().get(), outputBuilder);
            }

            outputBuilder.append("</test_method>\n");

            // Salva o conteúdo gerado no arquivo XML
            saveToFile(outputBuilder.toString(), new File(outputDirectory, methodName + ".xml"));
        });
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
        if (methodCall.toString().startsWith("System.out.println")) {
            processPrintStatement(methodCall, outputBuilder);  // Alteração aqui
        } else {
            outputBuilder.append("\t<methodCall>").append(methodCall.toString()).append("</methodCall>\n");
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
        // Extrai o conteúdo entre parênteses da chamada System.out.println()
        String content = methodCall.getArguments().toString().replaceAll("[\\[\\]]", "").trim();

        // Gera apenas a tag <print> com o conteúdo de impressão
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
