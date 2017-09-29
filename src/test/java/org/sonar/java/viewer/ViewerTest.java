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

import java.io.File;
import java.io.FileInputStream;
import java.lang.reflect.Constructor;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.sonar.plugins.java.api.semantic.Symbol;
import org.sonar.plugins.java.api.tree.ExpressionStatementTree;
import org.sonar.plugins.java.api.tree.MethodInvocationTree;
import spark.utils.IOUtils;

import static org.assertj.core.api.Assertions.assertThat;

public class ViewerTest {

  @Rule
  public final ExpectedException exception = ExpectedException.none();

  @Test
  public void private_constructor() throws Exception {
    Constructor<Viewer> constructor = Viewer.class.getDeclaredConstructor();
    assertThat(constructor.isAccessible()).isFalse();
    constructor.setAccessible(true);
    constructor.newInstance();
  }

  @Test
  public void code_without_method_trigger_an_exception() {
    exception.expect(NullPointerException.class);
    exception.expectMessage("Unable to find a method/constructor in first class.");

    new Viewer.Base("class A { }");
  }

  @Test
  public void should_resolve_methods_from_external_dep() {
    String source = "import com.google.common.base.Strings;\n"
      + "\n"
      + "class A {\n"
      + "  void foo(String s) {\n"
      + "    Strings.isNullOrEmpty(s);\n"
      + "  }\n"
      + "}";
    Viewer.Base base = new Viewer.Base(source);
    MethodInvocationTree methodInvocation = (MethodInvocationTree) ((ExpressionStatementTree) base.firstMethodOrConstructor.block().body().get(0)).expression();
    Symbol symbol = methodInvocation.symbol();
    assertThat(symbol.isUnknown()).isFalse();
    assertThat(symbol.owner().type().is("com.google.common.base.Strings")).isTrue();
  }

  @Test
  public void code_with_method_provide_everything_but_error_messages() {
    String source = "class A {"
      + "  int foo(boolean b) {"
      + "    if (b) {"
      + "      return 42;"
      + "    }"
      + "    return 21;"
      + "  }"
      + "}";
    Map<String, String> values = Viewer.getValues(source);

    assertThat(values.get("cfg")).isNotEmpty();

    assertThat(values.get("dotAST")).isNotEmpty();
    assertThat(values.get("dotCFG")).isNotEmpty();
    assertThat(values.get("dotEG")).isNotEmpty();

    assertThat(values.get("errorMessage")).isEmpty();
    assertThat(values.get("errorStackTrace")).isEmpty();
  }

  @Test
  public void values_with_error() {
    String message = "my exception message";
    Map<String, String> values = Viewer.getErrorValues(new Exception(message));

    assertThat(values.get("cfg")).isNull();

    assertThat(values.get("dotAST")).isNull();
    assertThat(values.get("dotCFG")).isNull();
    assertThat(values.get("dotEG")).isNull();

    assertThat(values.get("errorMessage")).isEqualTo(message);
    assertThat(values.get("errorStackTrace")).startsWith("java.lang.Exception: " + message);
  }

  @Test
  public void start_server_and_test_requests() throws Exception {
    ServerSocket serverSocket = new ServerSocket(0);
    int localPort = serverSocket.getLocalPort();
    serverSocket.close();
    String uri = "http://localhost:" + localPort + "/";
    Viewer.startWebServer(localPort, "class A {void fun() {}}");
    try(CloseableHttpClient client = HttpClients.createMinimal()) {
      CloseableHttpResponse resp = client.execute(new HttpGet(uri));
      assertThat(resp.getStatusLine().getStatusCode()).isEqualTo(200);
      assertThat(EntityUtils.toString(resp.getEntity())).isEqualTo(IOUtils.toString(new FileInputStream(new File("src/test/resources/viewer_result1.html"))));

      // post with no data, answer with default code.
      HttpPost httpPost = new HttpPost(uri);
      resp = client.execute(httpPost);
      assertThat(resp.getStatusLine().getStatusCode()).isEqualTo(200);
      assertThat(EntityUtils.toString(resp.getEntity())).isEqualTo(IOUtils.toString(new FileInputStream(new File("src/test/resources/viewer_result1.html"))));

      // render something besides default code
      httpPost = new HttpPost(uri);
      List<NameValuePair> postParameters = new ArrayList<>();
      postParameters.add(new BasicNameValuePair("javaCode", "class B{void meth() {}}"));
      httpPost.setEntity(new UrlEncodedFormEntity(postParameters, "UTF-8"));
      resp = client.execute(httpPost);
      assertThat(resp.getStatusLine().getStatusCode()).isEqualTo(200);
      assertThat(EntityUtils.toString(resp.getEntity())).isEqualTo(IOUtils.toString(new FileInputStream(new File("src/test/resources/viewer_result2.html"))));

      // send unproper code.
      httpPost = new HttpPost(uri);
      postParameters = new ArrayList<>();
      postParameters.add(new BasicNameValuePair("javaCode", "class B{}"));
      httpPost.setEntity(new UrlEncodedFormEntity(postParameters, "UTF-8"));
      resp = client.execute(httpPost);
      assertThat(resp.getStatusLine().getStatusCode()).isEqualTo(200);
      assertThat(EntityUtils.toString(resp.getEntity())).contains("<p>java.lang.NullPointerException: Unable to find a method/constructor in first class.<br/>");
    }

  }

  @Test
  public void test_complex_code_generation() throws Exception {
    String source = "class A {"
      + "  private Object foo(boolean b) {"
      + "    if (bar()) {"
      + "      if (b) {"
      + "        return null;"
      + "      }"
      + "      this.throwing();"
      + "    }"
      + "    return new Object();"
      + "  }"
      + "  private boolean bar() {"
      + "    return true;"
      + "  }"
      + "  private Object throwing() {"
      + "    throw new IllegalStateException(\"ise?\");"
      + "  }"
      + "}";
    Map<String, String> values = Viewer.getValues(source);

    assertThat(values.get("cfg")).isNotEmpty();
    assertThat(values.get("dotAST")).isEqualTo("graph AST {0[details=\"{?kind?:?COMPILATION_UNIT?}\",label=\"COMPILATION_UNIT L#1\",highlighting=\"firstNode\"];1[details=\"{?kind?:?CLASS?}\",label=\"CLASS L#1\",highlighting=\"classKind\"];2[details=\"{?kind?:?MODIFIERS?}\",label=\"MODIFIERS\"];1->2[];3[details=\"{?kind?:?TOKEN?}\",label=\"TOKEN L#1\"];3[details=\"{?kind?:?TOKEN?}\",label=\"class\",highlighting=\"tokenKind\"];1->3[];4[details=\"{?kind?:?IDENTIFIER?}\",label=\"IDENTIFIER L#1\"];5[details=\"{?kind?:?TOKEN?}\",label=\"TOKEN L#1\"];5[details=\"{?kind?:?TOKEN?}\",label=\"A\",highlighting=\"tokenKind\"];4->5[];1->4[];6[details=\"{?kind?:?TYPE_PARAMETERS?}\",label=\"TYPE_PARAMETERS\"];1->6[];7[details=\"{?kind?:?LIST?}\",label=\"LIST\"];1->7[];8[details=\"{?kind?:?TOKEN?}\",label=\"TOKEN L#1\"];8[details=\"{?kind?:?TOKEN?}\",label=\"{\",highlighting=\"tokenKind\"];1->8[];9[details=\"{?kind?:?METHOD?}\",label=\"METHOD L#1\",highlighting=\"methodKind\"];10[details=\"{?kind?:?MODIFIERS?}\",label=\"MODIFIERS L#1\"];11[details=\"{?kind?:?TOKEN?}\",label=\"TOKEN L#1\"];11[details=\"{?kind?:?TOKEN?}\",label=\"private\",highlighting=\"tokenKind\"];10->11[];9->10[];12[details=\"{?kind?:?TYPE_PARAMETERS?}\",label=\"TYPE_PARAMETERS\"];9->12[];13[details=\"{?kind?:?IDENTIFIER?}\",label=\"IDENTIFIER L#1\"];14[details=\"{?kind?:?TOKEN?}\",label=\"TOKEN L#1\"];14[details=\"{?kind?:?TOKEN?}\",label=\"Object\",highlighting=\"tokenKind\"];13->14[];9->13[];15[details=\"{?kind?:?IDENTIFIER?}\",label=\"IDENTIFIER L#1\"];16[details=\"{?kind?:?TOKEN?}\",label=\"TOKEN L#1\"];16[details=\"{?kind?:?TOKEN?}\",label=\"foo\",highlighting=\"tokenKind\"];15->16[];9->15[];17[details=\"{?kind?:?TOKEN?}\",label=\"TOKEN L#1\"];17[details=\"{?kind?:?TOKEN?}\",label=\"(\",highlighting=\"tokenKind\"];9->17[];18[details=\"{?kind?:?VARIABLE?}\",label=\"VARIABLE L#1\"];19[details=\"{?kind?:?MODIFIERS?}\",label=\"MODIFIERS\"];18->19[];20[details=\"{?kind?:?PRIMITIVE_TYPE?}\",label=\"PRIMITIVE_TYPE L#1\"];21[details=\"{?kind?:?TOKEN?}\",label=\"TOKEN L#1\"];21[details=\"{?kind?:?TOKEN?}\",label=\"boolean\",highlighting=\"tokenKind\"];20->21[];18->20[];22[details=\"{?kind?:?IDENTIFIER?}\",label=\"IDENTIFIER L#1\"];23[details=\"{?kind?:?TOKEN?}\",label=\"TOKEN L#1\"];23[details=\"{?kind?:?TOKEN?}\",label=\"b\",highlighting=\"tokenKind\"];22->23[];18->22[];9->18[];24[details=\"{?kind?:?TOKEN?}\",label=\"TOKEN L#1\"];24[details=\"{?kind?:?TOKEN?}\",label=\")\",highlighting=\"tokenKind\"];9->24[];25[details=\"{?kind?:?BLOCK?}\",label=\"BLOCK L#1\"];26[details=\"{?kind?:?TOKEN?}\",label=\"TOKEN L#1\"];26[details=\"{?kind?:?TOKEN?}\",label=\"{\",highlighting=\"tokenKind\"];25->26[];27[details=\"{?kind?:?IF_STATEMENT?}\",label=\"IF_STATEMENT L#1\"];28[details=\"{?kind?:?TOKEN?}\",label=\"TOKEN L#1\"];28[details=\"{?kind?:?TOKEN?}\",label=\"if\",highlighting=\"tokenKind\"];27->28[];29[details=\"{?kind?:?TOKEN?}\",label=\"TOKEN L#1\"];29[details=\"{?kind?:?TOKEN?}\",label=\"(\",highlighting=\"tokenKind\"];27->29[];30[details=\"{?kind?:?METHOD_INVOCATION?}\",label=\"METHOD_INVOCATION L#1\"];31[details=\"{?kind?:?IDENTIFIER?}\",label=\"IDENTIFIER L#1\"];32[details=\"{?kind?:?TOKEN?}\",label=\"TOKEN L#1\"];32[details=\"{?kind?:?TOKEN?}\",label=\"bar\",highlighting=\"tokenKind\"];31->32[];30->31[];33[details=\"{?kind?:?ARGUMENTS?}\",label=\"ARGUMENTS L#1\"];34[details=\"{?kind?:?TOKEN?}\",label=\"TOKEN L#1\"];34[details=\"{?kind?:?TOKEN?}\",label=\"(\",highlighting=\"tokenKind\"];33->34[];35[details=\"{?kind?:?TOKEN?}\",label=\"TOKEN L#1\"];35[details=\"{?kind?:?TOKEN?}\",label=\")\",highlighting=\"tokenKind\"];33->35[];30->33[];27->30[];36[details=\"{?kind?:?TOKEN?}\",label=\"TOKEN L#1\"];36[details=\"{?kind?:?TOKEN?}\",label=\")\",highlighting=\"tokenKind\"];27->36[];37[details=\"{?kind?:?BLOCK?}\",label=\"BLOCK L#1\"];38[details=\"{?kind?:?TOKEN?}\",label=\"TOKEN L#1\"];38[details=\"{?kind?:?TOKEN?}\",label=\"{\",highlighting=\"tokenKind\"];37->38[];39[details=\"{?kind?:?IF_STATEMENT?}\",label=\"IF_STATEMENT L#1\"];40[details=\"{?kind?:?TOKEN?}\",label=\"TOKEN L#1\"];40[details=\"{?kind?:?TOKEN?}\",label=\"if\",highlighting=\"tokenKind\"];39->40[];41[details=\"{?kind?:?TOKEN?}\",label=\"TOKEN L#1\"];41[details=\"{?kind?:?TOKEN?}\",label=\"(\",highlighting=\"tokenKind\"];39->41[];42[details=\"{?kind?:?IDENTIFIER?}\",label=\"IDENTIFIER L#1\"];43[details=\"{?kind?:?TOKEN?}\",label=\"TOKEN L#1\"];43[details=\"{?kind?:?TOKEN?}\",label=\"b\",highlighting=\"tokenKind\"];42->43[];39->42[];44[details=\"{?kind?:?TOKEN?}\",label=\"TOKEN L#1\"];44[details=\"{?kind?:?TOKEN?}\",label=\")\",highlighting=\"tokenKind\"];39->44[];45[details=\"{?kind?:?BLOCK?}\",label=\"BLOCK L#1\"];46[details=\"{?kind?:?TOKEN?}\",label=\"TOKEN L#1\"];46[details=\"{?kind?:?TOKEN?}\",label=\"{\",highlighting=\"tokenKind\"];45->46[];47[details=\"{?kind?:?RETURN_STATEMENT?}\",label=\"RETURN_STATEMENT L#1\"];48[details=\"{?kind?:?TOKEN?}\",label=\"TOKEN L#1\"];48[details=\"{?kind?:?TOKEN?}\",label=\"return\",highlighting=\"tokenKind\"];47->48[];49[details=\"{?kind?:?NULL_LITERAL?}\",label=\"NULL_LITERAL L#1\"];50[details=\"{?kind?:?TOKEN?}\",label=\"TOKEN L#1\"];50[details=\"{?kind?:?TOKEN?}\",label=\"null\",highlighting=\"tokenKind\"];49->50[];47->49[];51[details=\"{?kind?:?TOKEN?}\",label=\"TOKEN L#1\"];51[details=\"{?kind?:?TOKEN?}\",label=\";\",highlighting=\"tokenKind\"];47->51[];45->47[];52[details=\"{?kind?:?TOKEN?}\",label=\"TOKEN L#1\"];52[details=\"{?kind?:?TOKEN?}\",label=\"}\",highlighting=\"tokenKind\"];45->52[];39->45[];37->39[];53[details=\"{?kind?:?EXPRESSION_STATEMENT?}\",label=\"EXPRESSION_STATEMENT L#1\"];54[details=\"{?kind?:?METHOD_INVOCATION?}\",label=\"METHOD_INVOCATION L#1\"];55[details=\"{?kind?:?MEMBER_SELECT?}\",label=\"MEMBER_SELECT L#1\"];56[details=\"{?kind?:?IDENTIFIER?}\",label=\"IDENTIFIER L#1\"];57[details=\"{?kind?:?TOKEN?}\",label=\"TOKEN L#1\"];57[details=\"{?kind?:?TOKEN?}\",label=\"this\",highlighting=\"tokenKind\"];56->57[];55->56[];58[details=\"{?kind?:?TOKEN?}\",label=\"TOKEN L#1\"];58[details=\"{?kind?:?TOKEN?}\",label=\".\",highlighting=\"tokenKind\"];55->58[];59[details=\"{?kind?:?IDENTIFIER?}\",label=\"IDENTIFIER L#1\"];60[details=\"{?kind?:?TOKEN?}\",label=\"TOKEN L#1\"];60[details=\"{?kind?:?TOKEN?}\",label=\"throwing\",highlighting=\"tokenKind\"];59->60[];55->59[];54->55[];61[details=\"{?kind?:?ARGUMENTS?}\",label=\"ARGUMENTS L#1\"];62[details=\"{?kind?:?TOKEN?}\",label=\"TOKEN L#1\"];62[details=\"{?kind?:?TOKEN?}\",label=\"(\",highlighting=\"tokenKind\"];61->62[];63[details=\"{?kind?:?TOKEN?}\",label=\"TOKEN L#1\"];63[details=\"{?kind?:?TOKEN?}\",label=\")\",highlighting=\"tokenKind\"];61->63[];54->61[];53->54[];64[details=\"{?kind?:?TOKEN?}\",label=\"TOKEN L#1\"];64[details=\"{?kind?:?TOKEN?}\",label=\";\",highlighting=\"tokenKind\"];53->64[];37->53[];65[details=\"{?kind?:?TOKEN?}\",label=\"TOKEN L#1\"];65[details=\"{?kind?:?TOKEN?}\",label=\"}\",highlighting=\"tokenKind\"];37->65[];27->37[];25->27[];66[details=\"{?kind?:?RETURN_STATEMENT?}\",label=\"RETURN_STATEMENT L#1\"];67[details=\"{?kind?:?TOKEN?}\",label=\"TOKEN L#1\"];67[details=\"{?kind?:?TOKEN?}\",label=\"return\",highlighting=\"tokenKind\"];66->67[];68[details=\"{?kind?:?NEW_CLASS?}\",label=\"NEW_CLASS L#1\"];69[details=\"{?kind?:?TOKEN?}\",label=\"TOKEN L#1\"];69[details=\"{?kind?:?TOKEN?}\",label=\"new\",highlighting=\"tokenKind\"];68->69[];70[details=\"{?kind?:?IDENTIFIER?}\",label=\"IDENTIFIER L#1\"];71[details=\"{?kind?:?TOKEN?}\",label=\"TOKEN L#1\"];71[details=\"{?kind?:?TOKEN?}\",label=\"Object\",highlighting=\"tokenKind\"];70->71[];68->70[];72[details=\"{?kind?:?ARGUMENTS?}\",label=\"ARGUMENTS L#1\"];73[details=\"{?kind?:?TOKEN?}\",label=\"TOKEN L#1\"];73[details=\"{?kind?:?TOKEN?}\",label=\"(\",highlighting=\"tokenKind\"];72->73[];74[details=\"{?kind?:?TOKEN?}\",label=\"TOKEN L#1\"];74[details=\"{?kind?:?TOKEN?}\",label=\")\",highlighting=\"tokenKind\"];72->74[];68->72[];66->68[];75[details=\"{?kind?:?TOKEN?}\",label=\"TOKEN L#1\"];75[details=\"{?kind?:?TOKEN?}\",label=\";\",highlighting=\"tokenKind\"];66->75[];25->66[];76[details=\"{?kind?:?TOKEN?}\",label=\"TOKEN L#1\"];76[details=\"{?kind?:?TOKEN?}\",label=\"}\",highlighting=\"tokenKind\"];25->76[];9->25[];1->9[];77[details=\"{?kind?:?METHOD?}\",label=\"METHOD L#1\",highlighting=\"methodKind\"];78[details=\"{?kind?:?MODIFIERS?}\",label=\"MODIFIERS L#1\"];79[details=\"{?kind?:?TOKEN?}\",label=\"TOKEN L#1\"];79[details=\"{?kind?:?TOKEN?}\",label=\"private\",highlighting=\"tokenKind\"];78->79[];77->78[];80[details=\"{?kind?:?TYPE_PARAMETERS?}\",label=\"TYPE_PARAMETERS\"];77->80[];81[details=\"{?kind?:?PRIMITIVE_TYPE?}\",label=\"PRIMITIVE_TYPE L#1\"];82[details=\"{?kind?:?TOKEN?}\",label=\"TOKEN L#1\"];82[details=\"{?kind?:?TOKEN?}\",label=\"boolean\",highlighting=\"tokenKind\"];81->82[];77->81[];83[details=\"{?kind?:?IDENTIFIER?}\",label=\"IDENTIFIER L#1\"];84[details=\"{?kind?:?TOKEN?}\",label=\"TOKEN L#1\"];84[details=\"{?kind?:?TOKEN?}\",label=\"bar\",highlighting=\"tokenKind\"];83->84[];77->83[];85[details=\"{?kind?:?TOKEN?}\",label=\"TOKEN L#1\"];85[details=\"{?kind?:?TOKEN?}\",label=\"(\",highlighting=\"tokenKind\"];77->85[];86[details=\"{?kind?:?TOKEN?}\",label=\"TOKEN L#1\"];86[details=\"{?kind?:?TOKEN?}\",label=\")\",highlighting=\"tokenKind\"];77->86[];87[details=\"{?kind?:?BLOCK?}\",label=\"BLOCK L#1\"];88[details=\"{?kind?:?TOKEN?}\",label=\"TOKEN L#1\"];88[details=\"{?kind?:?TOKEN?}\",label=\"{\",highlighting=\"tokenKind\"];87->88[];89[details=\"{?kind?:?RETURN_STATEMENT?}\",label=\"RETURN_STATEMENT L#1\"];90[details=\"{?kind?:?TOKEN?}\",label=\"TOKEN L#1\"];90[details=\"{?kind?:?TOKEN?}\",label=\"return\",highlighting=\"tokenKind\"];89->90[];91[details=\"{?kind?:?BOOLEAN_LITERAL?}\",label=\"BOOLEAN_LITERAL L#1\"];92[details=\"{?kind?:?TOKEN?}\",label=\"TOKEN L#1\"];92[details=\"{?kind?:?TOKEN?}\",label=\"true\",highlighting=\"tokenKind\"];91->92[];89->91[];93[details=\"{?kind?:?TOKEN?}\",label=\"TOKEN L#1\"];93[details=\"{?kind?:?TOKEN?}\",label=\";\",highlighting=\"tokenKind\"];89->93[];87->89[];94[details=\"{?kind?:?TOKEN?}\",label=\"TOKEN L#1\"];94[details=\"{?kind?:?TOKEN?}\",label=\"}\",highlighting=\"tokenKind\"];87->94[];77->87[];1->77[];95[details=\"{?kind?:?METHOD?}\",label=\"METHOD L#1\",highlighting=\"methodKind\"];96[details=\"{?kind?:?MODIFIERS?}\",label=\"MODIFIERS L#1\"];97[details=\"{?kind?:?TOKEN?}\",label=\"TOKEN L#1\"];97[details=\"{?kind?:?TOKEN?}\",label=\"private\",highlighting=\"tokenKind\"];96->97[];95->96[];98[details=\"{?kind?:?TYPE_PARAMETERS?}\",label=\"TYPE_PARAMETERS\"];95->98[];99[details=\"{?kind?:?IDENTIFIER?}\",label=\"IDENTIFIER L#1\"];100[details=\"{?kind?:?TOKEN?}\",label=\"TOKEN L#1\"];100[details=\"{?kind?:?TOKEN?}\",label=\"Object\",highlighting=\"tokenKind\"];99->100[];95->99[];101[details=\"{?kind?:?IDENTIFIER?}\",label=\"IDENTIFIER L#1\"];102[details=\"{?kind?:?TOKEN?}\",label=\"TOKEN L#1\"];102[details=\"{?kind?:?TOKEN?}\",label=\"throwing\",highlighting=\"tokenKind\"];101->102[];95->101[];103[details=\"{?kind?:?TOKEN?}\",label=\"TOKEN L#1\"];103[details=\"{?kind?:?TOKEN?}\",label=\"(\",highlighting=\"tokenKind\"];95->103[];104[details=\"{?kind?:?TOKEN?}\",label=\"TOKEN L#1\"];104[details=\"{?kind?:?TOKEN?}\",label=\")\",highlighting=\"tokenKind\"];95->104[];105[details=\"{?kind?:?BLOCK?}\",label=\"BLOCK L#1\"];106[details=\"{?kind?:?TOKEN?}\",label=\"TOKEN L#1\"];106[details=\"{?kind?:?TOKEN?}\",label=\"{\",highlighting=\"tokenKind\"];105->106[];107[details=\"{?kind?:?THROW_STATEMENT?}\",label=\"THROW_STATEMENT L#1\"];108[details=\"{?kind?:?TOKEN?}\",label=\"TOKEN L#1\"];108[details=\"{?kind?:?TOKEN?}\",label=\"throw\",highlighting=\"tokenKind\"];107->108[];109[details=\"{?kind?:?NEW_CLASS?}\",label=\"NEW_CLASS L#1\"];110[details=\"{?kind?:?TOKEN?}\",label=\"TOKEN L#1\"];110[details=\"{?kind?:?TOKEN?}\",label=\"new\",highlighting=\"tokenKind\"];109->110[];111[details=\"{?kind?:?IDENTIFIER?}\",label=\"IDENTIFIER L#1\"];112[details=\"{?kind?:?TOKEN?}\",label=\"TOKEN L#1\"];112[details=\"{?kind?:?TOKEN?}\",label=\"IllegalStateException\",highlighting=\"tokenKind\"];111->112[];109->111[];113[details=\"{?kind?:?ARGUMENTS?}\",label=\"ARGUMENTS L#1\"];114[details=\"{?kind?:?TOKEN?}\",label=\"TOKEN L#1\"];114[details=\"{?kind?:?TOKEN?}\",label=\"(\",highlighting=\"tokenKind\"];113->114[];115[details=\"{?kind?:?STRING_LITERAL?}\",label=\"STRING_LITERAL L#1\"];116[details=\"{?kind?:?TOKEN?}\",label=\"TOKEN L#1\"];116[details=\"{?kind?:?TOKEN?}\",label=\"&quot;ise&quest;&quot;\",highlighting=\"tokenKind\"];115->116[];113->115[];117[details=\"{?kind?:?TOKEN?}\",label=\"TOKEN L#1\"];117[details=\"{?kind?:?TOKEN?}\",label=\")\",highlighting=\"tokenKind\"];113->117[];109->113[];107->109[];118[details=\"{?kind?:?TOKEN?}\",label=\"TOKEN L#1\"];118[details=\"{?kind?:?TOKEN?}\",label=\";\",highlighting=\"tokenKind\"];107->118[];105->107[];119[details=\"{?kind?:?TOKEN?}\",label=\"TOKEN L#1\"];119[details=\"{?kind?:?TOKEN?}\",label=\"}\",highlighting=\"tokenKind\"];105->119[];95->105[];1->95[];120[details=\"{?kind?:?TOKEN?}\",label=\"TOKEN L#1\"];120[details=\"{?kind?:?TOKEN?}\",label=\"}\",highlighting=\"tokenKind\"];1->120[];0->1[];121[details=\"{?kind?:?TOKEN?}\",label=\"TOKEN L#1\"];121[details=\"{?kind?:?TOKEN?}\",label=\"\",highlighting=\"tokenKind\"];0->121[];}");
    assertThat(values.get("dotCFG")).isEqualTo("graph CFG {5[label=\"B5 (START)\",highlighting=\"firstNode\"];4[label=\"B4\"];3[label=\"B3\"];2[label=\"B2\"];1[label=\"B1\"];0[label=\"B0 (EXIT)\",highlighting=\"exitNode\"];5->1[label=\"FALSE\"];5->4[label=\"TRUE\"];4->2[label=\"FALSE\"];4->3[label=\"TRUE\"];3->0[label=\"EXIT\"];2->1[];1->0[label=\"EXIT\"];}");
    String dotEG = values.get("dotEG");
    // FIXME: dot graph of EG is not consistent between calls
    assertThat(dotEG).isNotEmpty();
    // check for correctly built yields
    assertThat(dotEG).contains("?methodName?:?bar?");
    assertThat(dotEG).contains("?methodYields?:[{?result?:[?NOT_NULL?,?TRUE?],?resultIndex?:-1,?params?:[]}]");

    assertThat(values.get("errorMessage")).isEmpty();
    assertThat(values.get("errorStackTrace")).isEmpty();
  }

  @Test
  public void missing_default_code_should_still_return_something() {
    String sourceCode = Viewer.fileContent("/public/example/missing.java");
    assertThat(sourceCode).isEqualTo("// Unable to read file at location:\n// \"/public/example/missing.java\"\n");
  }

}
