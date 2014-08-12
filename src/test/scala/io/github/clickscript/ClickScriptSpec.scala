package io.github.clickscript

import org.scalatest.{ShouldMatchers, FunSpec}
import io.github.clickscript.Predef._
import io.gatling.core.Predef._
import io.gatling.http.Predef._
import jodd.lagarto.dom.{NodeSelector, Node}
import io.gatling.core.validation._

/**
 * Created by hisg085 on 04/04/2014.
 */
class ClickScriptSpec extends FunSpec with ShouldMatchers {
  import Helper._

  describe("ClickScript") {
    describe("the saveLastResponse extractor") {
      it("should extract a lazy DOM from a response") {
        val session = applyCheck(saveLastResponse, "<h1>Hello World</h1>")
        val lazyNode = session(lastResponseVarName).as[Lazy[Node]]
        new NodeSelector(lazyNode).selectFirst("h1").getTextContent should equal("Hello World")
      }
    }

    describe("the extractLink method") {
      it("should extract links by CSS selector and occurence") {
        val sess = prepareSession("""<a class="classA" href="notthisone">A</a> <a class="classA" href="http://localhost">B</a>""")
        extractLink(".classA", 1)(sess) should equal(Success("http://localhost"))
      }

      it("should resolve relative links") {
        val sess = prepareSession("""<a class="classA" href="notthisone">A</a> <a class="classA" href="link.html">B</a>""")
        extractLink(".classA", 1)(sess) should equal(Success("http://localhost/link.html"))
      }

      it("should resolve relative links with no path in base URI") {
        val sess = prepareSession("""<a class="classA" href="notthisone">A</a> <a class="classA" href="link.html">B</a>""").set(lastUriVarName, "http://localhost")
        extractLink(".classA", 1)(sess) should equal(Success("http://localhost/link.html"))
      }

      it("should resolve absolute links on the same host") {
        val sess = prepareSession("""<a class="classA" href="notthisone">A</a> <a class="classA" href="/link.html">B</a>""").set(lastUriVarName, "http://localhost/a/directory/index.html")
        extractLink(".classA", 1)(sess) should equal(Success("http://localhost/link.html"))
      }

      it("should return failure if CSS does not match") {
        val sess = prepareSession("""<a class="classA" href="notthisone">A</a>""")
        extractLink(".classB", 1)(sess) shouldBe a [Failure]
      }
    }

    describe("the findLinkByTextRegex method") {
      it("should extract links by text and occurence") {
        val sess = prepareSession("""<a href="notthisone">A</a> <a href="http://localhost">A</a>""")
        findLinkByTextRegex("^A$", 1)(sess) should equal(Success("http://localhost"))
      }

      it("should extract relative links by text") {
        val sess = prepareSession("""<a href="notthisone">A</a> <a href="link.html">A</a>""")
        findLinkByTextRegex("^A$", 1)(sess) should equal(Success("http://localhost/link.html"))
      }

      it("should return failure if CSS does not match") {
        val sess = prepareSession("""<a class="classA" href="notthisone">B</a>""")
        findLinkByTextRegex("^A$", 1)(sess) shouldBe a [Failure]
      }
    }

    describe("the extractPrefilledValues method") {
      it("should select forms by css and number") {
        val sess = prepareSession(
          """
            | <form class="a"></form>
            | <form class="b"></form>
            | <form class="a">
            |   <input type="text" name="text-input" value="Hello">
            | </form>""".stripMargin)
        extractPrefilledValues(".a", 1)(sess) should equal(Success(List("text-input" -> "Hello")))
      }

      it("should fail to find non-existent forms") {
        val sess = prepareSession("<p>Hello World</p>")
        extractPrefilledValues("form")(sess) shouldBe a [Failure]
      }

      it("should extract text values") {
        val sess = prepareSession(
          """
            |<form>
            |  <input type="text" name="input1" value="value1">
            |  <input type="text" name="input2"> <!-- No value, excluded -->
            |  <input type="text" name="input3" value="value3"> <!-- Will be specifically excluded -->
            |</form>
          """.stripMargin)
        extractPrefilledValues("form", exclusions=Seq("input3"))(sess) should equal(Success(List("input1" -> "value1")))
      }

      it("should extract hidden values") {
        val sess = prepareSession(
          """
            |<form>
            |  <input type="hidden" name="input1" value="value1">
            |  <input type="hidden" name="input2">
            |</form>
          """.stripMargin)
        extractPrefilledValues("form")(sess) should equal(Success(List("input1" -> "value1")))
      }

      it("should extract radio and checkbox values") {
        val sess = prepareSession(
          """
            |<form>
            |  <input type="checkbox" name="cb1" checked="checked"> <!-- Should be on -->
            |  <input type="checkbox" name="cb2"> <!-- Should not appear -->
            |  <input type="checkbox" name="cb3" value="yes", checked="checked"> <!-- should be "yes" -->
            |  <input type="checkbox" name="cb4" checked="checked"> <!-- Will be specifically excluded -->
            |  <input type="radio" name="r1" value="yes" checked="checked"> <!-- Should be "yes" -->
            |  <input type="radio" name="r2" value="no"> <!-- Should not appear -->
            |</form>
          """.stripMargin
        )
        extractPrefilledValues("form", exclusions=Seq("cb4"))(sess) should equal(Success(List("cb1" -> "on", "cb3" -> "yes", "r1" -> "yes")))
      }

      it("should extract textarea values") {
        val sess = prepareSession(
          """
            |<form>
            |  <textarea name="ta1">text1</textarea>
            |  <textarea name="ta2">text2</textarea>
            |</form>
          """.stripMargin
        )
        extractPrefilledValues("form", exclusions=Seq("ta2"))(sess) should equal(Success(List("ta1" -> "text1")))
      }

      it("should extract select values") {
        val sess = prepareSession(
          """
            |<form>
            |  <select name="sel1">
            |    <option value="o1">
            |    <option value="o2" selected>
            |  </select>
            |   <select name="sel2">
            |    <option value="o3"> <!-- If none selected, default to first -->
            |    <option value="o4">
            |  </select>
            |  <select name="sel3">
            |    <option value="o5" selected> <!-- Will be specifically excluded -->
            |    <option value="o6">
            |  </select>
            |</form>
          """.stripMargin
        )
        extractPrefilledValues("form", exclusions=Seq("sel3"))(sess) should equal(Success(List("sel1" -> "o2", "sel2" -> "o3")))
      }

      it("should select button value if provided") {
        val sess = prepareSession(
          """
            |<form>
            |  <button id="mybtn" name="btn1" value="yes">
            |  <button id="mybtn2" name="btn2" value="no">
            |</form>
          """.stripMargin
        )
        extractPrefilledValues("form", formButton = Some("#mybtn"))(sess) should equal(Success(List("btn1" -> "yes")))
      }

      it("should select the empty string if buttons have no value value if provided") {
        val sess = prepareSession(
          """
            |<form>
            |  <button id="mybtn" name="btn1">
            |</form>
          """.stripMargin
        )
        extractPrefilledValues("form", formButton = Some("#mybtn"))(sess) should equal(Success(List("btn1" -> "")))
      }

      it("should ignore buttons if no button is specified") {
        val sess = prepareSession(
          """
            |<form>
            |  <button id="mybtn" name="btn1">
            |</form>
          """.stripMargin
        )
        extractPrefilledValues("form")(sess) should equal(Success(Nil))
      }
    }

    describe("the extractFormUrl method") {
      it("should extract actions from forms") {
        val sess = prepareSession(
          """
            |<form class="a" action="/404.html"></form>
            |<form class="b" action="/404.html"></form>
            |<form class="a" action="/"></form>
          """.stripMargin)
        extractFormUrl(".a", 1)(sess) should equal(Success("http://localhost/"))
      }

      it("should fail if the form is not found") {
        val sess = prepareSession("<p>Hello World</p>")
        extractFormUrl("form")(sess) shouldBe a [Failure]
      }

      it("should select the current url of the form has no action") {
        val sess = prepareSession("<form></form>")
        extractFormUrl("form")(sess) should equal(Success("http://localhost/index.html"))
      }

      it("should append actions beginning with a question mark as query strings") {
        val sess = prepareSession("""<form action="?query=yes"></form>""")
        extractFormUrl("form")(sess) should equal(Success("http://localhost/index.html?query=yes"))
      }

      it("should combine query strings") {
        val sess = prepareSession("""<form action="?query=yes"></form>""").set(lastUriVarName, "http://localhost?sess=1")
        extractFormUrl("form")(sess) should equal(Success("http://localhost?sess=1&query=yes"))
      }

      it("should resolve paths") {
        val sess = prepareSession("""<form action="post.html"></form>""")
        extractFormUrl("form")(sess) should equal(Success("http://localhost/post.html"))
      }

      it("should prefer actions from buttons, if a button is specified") {
        val sess = prepareSession(
          """
            |<form action="/404.html">
            |  <button formaction="/index.html">Submit</button>
            |</form>
          """.stripMargin)
        extractFormUrl("form", formButton=Some("button"))(sess) should equal(Success("http://localhost/index.html"))
      }

      it("should use form actions, if a button is specified but has no action") {
        val sess = prepareSession(
          """
            |<form action="/index.html">
            |  <button>Submit</button>
            |</form>
          """.stripMargin)
        extractFormUrl("form", formButton=Some("button"))(sess) should equal(Success("http://localhost/index.html"))
      }
    }
  }
}
