package com.howtoprogram.junit5;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.LiteralExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.ForStmt;
import com.github.javaparser.ast.stmt.IfStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.stmt.TryStmt;
import com.github.javaparser.ast.comments.Comment;
import com.github.javaparser.ast.comments.LineComment;
import com.github.javaparser.ast.comments.BlockComment;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

public class xmlTestGenerator {

    public static void main(String[] args) {
        //Caminho para o arquivo de testes que deseja converter!!!
        String filePath = "";
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
                processComments(method.getAllContainedComments(), outputBuilder);
                processAssertions(method, outputBuilder);
                processLoops(method, outputBuilder);
                processConditionals(method, outputBuilder);
                processPrintStatements(method, outputBuilder);
                processTryCatchBlocks(method, outputBuilder);
            }

            outputBuilder.append("</test_method>\n\n");
        });

        return outputBuilder.toString();
    }

    private static void processComments(List<Comment> comments, StringBuilder outputBuilder) {
        for (Comment comment : comments) {
            if (comment instanceof LineComment) {
                outputBuilder.append("\t<comment>").append(((LineComment) comment).getContent()).append("</comment>\n");
            } else if (comment instanceof BlockComment) {
                outputBuilder.append("\t<comment>").append(((BlockComment) comment).getContent()).append("</comment>\n");
            }
        }
    }


    //Lista dos tipos de asserts em java, caso existir mais é só adicionar aqui que ele conseguira identificar
    // E vai conseguir por dentro da tag <assert>
    private static void processAssertions(MethodDeclaration method, StringBuilder outputBuilder) {
        List<String> assertMethods = Arrays.asList(
                "assertEquals", "assertNotEquals", "assertTrue", "assertFalse",
                "assertNull", "assertNotNull", "assertSame", "assertNotSame",
                "assertArrayEquals", "assertThrows"
        );

        List<MethodCallExpr> assertions = method.findAll(MethodCallExpr.class);
        boolean hasAssertionRoulette = false;

        for (MethodCallExpr expr : assertions) {
            if (assertMethods.contains(expr.getNameAsString())) {
                outputBuilder.append("\t<assert>").append(expr.toString()).append("</assert>\n");

                boolean hasNumericLiteral = expr.getArguments().stream()
                        .filter(arg -> arg instanceof LiteralExpr)
                        .map(arg -> (LiteralExpr) arg)
                        .anyMatch(xmlTestGenerator::isNumericLiteral);

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

                // Check if the assertion lacks a descriptive message
                if (expr.getArguments().size() < 3) {
                    hasAssertionRoulette = true;
                }
            }
        }

        if (hasAssertionRoulette) {
            outputBuilder.append("\t<assertion_roulette/>\n");
        }
    }

    //Identifica for
    private static void processLoops(MethodDeclaration method, StringBuilder outputBuilder) {
        method.findAll(ForStmt.class).forEach(forStmt -> {
            String condition = forStmt.getInitialization().toString() + "; " +
                    forStmt.getCompare().map(Object::toString).orElse("") + "; " +
                    forStmt.getUpdate().toString();
            outputBuilder.append("\t<loopFor condition=\"").append(condition).append("\">\n");
            outputBuilder.append("\t\t<loopStmt>").append(forStmt.getBody()).append("</loopStmt>\n");
            outputBuilder.append("\t</loopFor>\n");
        });
    }


    private static void processConditionals(MethodDeclaration method, StringBuilder outputBuilder) {
        method.findAll(IfStmt.class).forEach(ifStmt -> {
            String condition = ifStmt.getCondition().toString();
            outputBuilder.append("\t<if condition=\"").append(condition).append("\">\n");

            Statement thenStmt = ifStmt.getThenStmt();
            if (thenStmt.isBlockStmt()) {
                BlockStmt block = thenStmt.asBlockStmt();
                outputBuilder.append("\t\t<ifStmt>\n");
                block.getStatements().forEach(stmt -> outputBuilder.append("\t\t\t").append(stmt.toString()).append("\n"));
                outputBuilder.append("\t\t</ifStmt>\n");
            } else {
                outputBuilder.append("\t\t<ifStmt>").append(thenStmt.toString()).append("</ifStmt>\n");
            }

            outputBuilder.append("\t</if>\n");
        });
    }

    private static void processPrintStatements(MethodDeclaration method, StringBuilder outputBuilder) {
        method.findAll(Statement.class).forEach(stmt -> {
            if (stmt.toString().startsWith("System.out.println")) {
                String content = stmt.toString().substring("System.out.println".length()).trim();
                outputBuilder.append("\t<print>\n");
                outputBuilder.append("\t\t<printStmt>(").append(content).append(");</printStmt>\n");
                outputBuilder.append("\t</print>\n");
            }
        });
    }

    private static void processTryCatchBlocks(MethodDeclaration method, StringBuilder outputBuilder) {
        method.findAll(TryStmt.class).forEach(tryStmt -> {
            outputBuilder.append("\t<try>\n");
            outputBuilder.append("\t\t<try_stmt>").append(tryStmt.getTryBlock().toString()).append("</try_stmt>\n");
            tryStmt.getCatchClauses().forEach(catchClause -> {
                outputBuilder.append("\t\t<catch>\n");
                outputBuilder.append("\t\t\t<catch_stmt>").append(catchClause.getBody().toString()).append("</catch_stmt>\n");
                outputBuilder.append("\t\t</catch>\n");
            });
            outputBuilder.append("\t</try>\n");
        });
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
