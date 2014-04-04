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

      it("should return failure if CSS does not match") {
        val sess = prepareSession("""<a class="classA" href="notthisone">A</a>""")
        extractLink(".classB", 1)(sess) shouldBe a [Failure]
      }
    }
  }
}
