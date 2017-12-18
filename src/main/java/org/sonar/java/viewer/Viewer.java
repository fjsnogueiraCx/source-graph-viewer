/*
 * SonarQube SourgeGraph Viewer
 * Copyright (C) 2017-2017 SonarSource SA
 * mailto:info AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.sonar.java.viewer;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.sonar.sslr.api.typed.ActionParser;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.CheckForNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonar.java.ast.ASTDotGraph;
import org.sonar.java.ast.parser.JavaParser;
import org.sonar.java.bytecode.loader.SquidClassLoader;
import org.sonar.java.cfg.CFG;
import org.sonar.java.cfg.CFGDotGraph;
import org.sonar.java.cfg.CFGPrinter;
import org.sonar.java.resolve.SemanticModel;
import org.sonar.java.se.EGDotGraph;
import org.sonar.plugins.java.api.tree.ClassTree;
import org.sonar.plugins.java.api.tree.CompilationUnitTree;
import org.sonar.plugins.java.api.tree.MethodTree;
import org.sonar.plugins.java.api.tree.Tree;
import spark.ModelAndView;
import spark.Request;
import spark.template.velocity.VelocityTemplateEngine;
import spark.utils.IOUtils;

import static spark.Spark.awaitInitialization;
import static spark.Spark.exception;
import static spark.Spark.get;
import static spark.Spark.port;
import static spark.Spark.post;
import static spark.Spark.staticFiles;

public class Viewer {

  private static final Logger LOGGER = LoggerFactory.getLogger(Viewer.class);
  private static final String DEFAULT_SOURCE_CODE = fileContent("/public/example/example.java");
  private static final int DEFAULT_PORT = 9999;

  private Viewer() {
  }

  public static void main(String[] args) {
    startWebServer(DEFAULT_PORT, DEFAULT_SOURCE_CODE);
  }

  @VisibleForTesting
  static void startWebServer(int port, String defaultSourceCode) {
    // print all exceptions
    exception(Exception.class, (e, req, res) -> LOGGER.error("Unexpected exception.", e));

    staticFiles.location("/public");
    port(port);

    get("/", (req, res) -> generate(defaultSourceCode));
    post("/", (req, res) -> generate(req, defaultSourceCode));

    awaitInitialization();
    LOGGER.info("Viewer at http://localhost:{}", port);
  }

  private static String generate(Request request, String defaultSourceCode) {
    String javaCode = request.queryParams("javaCode");
    if (javaCode == null) {
      javaCode = defaultSourceCode;
    }
    return generate(javaCode);
  }

  private static String generate(String javaCode) {
    Map<String, String> values;
    try {
      values = getValues(javaCode);
    } catch (Exception e) {
      values = getErrorValues(e);
    }
    return renderWithValues(javaCode, values);
  }

  @VisibleForTesting
  static Map<String, String> getValues(String javaCode) {
    Map<String, String> values = new HashMap<>();

    Base base = new Base(javaCode);

    values.put("cfg", CFGPrinter.toString(base.cfgFirstMethodOrConstructor));

    values.put("dotAST", new ASTDotGraph(base.cut).toDot());
    values.put("dotCFG", new CFGDotGraph(base.cfgFirstMethodOrConstructor).toDot());
    values.put("dotEG", new EGDotGraph(base).toDot());

    // explicitly force empty message and stack trace
    values.put("errorMessage", "");
    values.put("errorStackTrace", "");

    return values;
  }

  @VisibleForTesting
  static Map<String, String> getErrorValues(Exception e) {
    Map<String, String> values = new HashMap<>();

    StringWriter sw = new StringWriter();
    e.printStackTrace(new PrintWriter(sw));
    String stackTrace = sw.toString();

    String message = e.getMessage();
    values.put("errorMessage", message == null ? "Unexpected error" : message);
    values.put("errorStackTrace", stackTrace.replace(System.getProperty("line.separator"), "<br/>\n"));

    return values;
  }

  private static String renderWithValues(String javaCode, Map<String, String> values) {
    values.put("javaCode", javaCode);
    return new VelocityTemplateEngine().render(new ModelAndView(values, "velocity/index.vm"));
  }

  @VisibleForTesting
  static String fileContent(String location) {
    try {
      InputStream reader = Viewer.class.getResourceAsStream(location);
      return IOUtils.toString(reader);
    } catch (Exception e) {
      LOGGER.error("Unable to read file at location: " + location + ".", e);
    }
    return "// Unable to read file at location:\n// \"" + location + "\"\n";
  }

  public static class Base {
    private static final ActionParser<Tree> PARSER = JavaParser.createParser();
    public final CompilationUnitTree cut;
    public final MethodTree firstMethodOrConstructor;
    public final SemanticModel semanticModel;
    public final CFG cfgFirstMethodOrConstructor;

    public Base(String source) {
      this.cut = (CompilationUnitTree) PARSER.parse(source);

      List<File> classpath = getFilesRecursively(new String[] {"jar", "zip"});
      this.semanticModel = SemanticModel.createFor(cut, new SquidClassLoader(classpath));
      this.firstMethodOrConstructor = getFirstMethodOrConstructor(cut);

      Preconditions.checkNotNull(firstMethodOrConstructor, "Unable to find a method/constructor in first class.");

      this.cfgFirstMethodOrConstructor = CFG.build(firstMethodOrConstructor);
    }

    private static List<File> getFilesRecursively(final String[] extensions) {
      List<File> files = new ArrayList<>();

      FileVisitor<Path> visitor = new SimpleFileVisitor<Path>() {
        @Override
        public FileVisitResult visitFile(Path filePath, BasicFileAttributes attrs) throws IOException {
          for (String extension : extensions) {
            if (filePath.toString().endsWith("." + extension)) {
              files.add(filePath.toFile());
              break;
            }
          }
          return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
          return FileVisitResult.CONTINUE;
        }
      };

      try {
        Files.walkFileTree(Paths.get("target/test-jars"), visitor);
      } catch (IOException e) {
        // we already ignore errors in the visitor
      }

      return files;
    }

    @CheckForNull
    private static MethodTree getFirstMethodOrConstructor(CompilationUnitTree cut) {
      return (MethodTree) cut.types().stream()
        .findFirst()
        .map(ClassTree.class::cast)
        .map(ClassTree::members)
        .map(List::stream)
        .flatMap(members -> members.filter(m -> m.is(Tree.Kind.METHOD, Tree.Kind.CONSTRUCTOR)).findFirst())
        .orElse(null);
    }
  }
}
