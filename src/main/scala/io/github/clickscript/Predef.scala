package io.github.clickscript

import io.gatling.core.session.Expression
import io.gatling.core.Predef._
import io.gatling.http.Predef._
import io.gatling.core.check.extractor.css.CssExtractor
import jodd.lagarto.dom._
import io.gatling.core.validation._
import scala.collection.JavaConversions._
import scala.collection.breakOut
import scala.util.control.NonFatal
import com.typesafe.scalalogging.slf4j.Logging

private[clickscript] class Lazy[X](f: => X) {
  lazy val x = f
}

private[clickscript] object Lazy {
  implicit def autoExtract[X](h: Lazy[X]): X = h.x
  def apply[X](x: => X) = new Lazy(x)
}

object Predef extends Logging {
  def clickStep(name: Expression[String]) = StepBuilder(name)

  def exitBrowser = exec({s: Session =>
    s.removeAll(lastResponseVarName, lastUriVarName).success
  }).exec(flushHttpCache).exec(flushCookieJar)

  private[clickscript] val lastResponseVarName = "__clickScript_lastResponse"
  private[clickscript] val lastUriVarName =  "__clickScript_lastUri"

  private[clickscript] val saveLastResponse = {
    bodyString.transform {
      body =>
        Lazy(CssExtractor.DomBuilder.parse(body))
    }.saveAs(lastResponseVarName)
  }

  private[clickscript] implicit def validation2Option[A](v: Validation[A]) = v match {
    case Success(x) => Some(x)
    case Failure(_) => None
  }

  private[clickscript] def extractLink(linkSelector: Expression[String], occurence: Int = 0) = {implicit session: Session =>
    linkSelector(session) flatMap {
      css =>
        exceptionToFailure(s"Could not extract link from $css") {
          for (lastResponse <- session(lastResponseVarName).validate[Lazy[Node]]) yield {
            val selector = new NodeSelector(lastResponse)
            val link = selector.select(css).get(occurence)
            link.getAttribute("href")
          }
        }
    }
  }
  
  private class FunctionNodeFilter(f: Node => Boolean) extends NodeFilter {
    def accept(node: Node) = f(node)
  }
  
  private[clickscript] def findLinkByTextRegex(linkTextRegex: Expression[String], occurence: Int = 0) = {implicit session: Session =>
    linkTextRegex(session) flatMap {
      regex =>
        exceptionToFailure(s"Could not find link with text matching $regex") {
          for (lastResponse <- session(lastResponseVarName).validate[Lazy[Node]]) yield {
            object Filter 
            val selector = new NodeSelector(lastResponse)
            val link = selector.select(new FunctionNodeFilter(
              node => node.getNodeName == "a" && node.getTextContent.matches(regex))
            ).get(occurence)
            link.getAttribute("href")
          }
        }
    }
    
  }

  private def exceptionToFailure[X](msg: String)(f: => Validation[X])(implicit session: Session) = {
    try f
    catch {
      case NonFatal(e) =>
        logger.error(s"$msg in $session", e)
        for (document <- session(lastResponseVarName).asOption[Lazy[Document]]) {
          logger.debug(document.x.getHtml)
        }
        Failure(s"$msg: ${e.getLocalizedMessage}")
    }
  }

  private[clickscript] def extractFormUrl(formSelector: Expression[String], occurence: Int = 0, formButton: Option[Expression[String]] = None) = {implicit session: Session =>
    formSelector(session) flatMap {css =>
      exceptionToFailure(s"Could not extract form URL from $css") {
        session(lastResponseVarName).validate[Lazy[Node]] flatMap {
          lastResponse =>
            val selector = new NodeSelector(lastResponse)
            val form = selector.select(css).get(occurence)
            val action = form.getAttribute("action")
            val buttonAction = for (expr <- formButton;
                                    btnCss <- expr(session);
                                    action = selector.selectFirst(btnCss).getAttribute("formaction")
                                    if action != null) yield action
            val overallAction = buttonAction orElse Option(action)
            overallAction match {
              case Some(a) if a startsWith "?" =>
                session(lastUriVarName).validate[String] map {lastUri =>
                  if (lastUri contains "?") lastUri + "&" + a.substring(1)
                  else lastUri + a
                }
              case Some("") | None => session(lastUriVarName).validate[String]
              case Some(a) => a.success
            }

        }
      }
    }
  }

  private[clickscript] def validValue(x: String) = (x != null) && (x != "")

  private[clickscript] def extractPrefilledValues(formSelector: Expression[String],
                                                  occurence: Int = 0,
                                                  formButton: Option[Expression[String]] = None,
                                                  exclusions: Seq[Expression[String]] = Nil) = {implicit session: Session =>
    val exclusionSet: Set[String] = exclusions.flatMap(ex => ex(session): Option[String])(breakOut)
    formSelector(session) flatMap {
      css =>
        exceptionToFailure(s"Could not extract prefilled values from $css") {
          for (lastResponse <- session(lastResponseVarName).validate[Lazy[Node]]) yield {
            val selector = new NodeSelector(lastResponse)
            val form = selector.select(css).get(occurence)
            val formSelector = new NodeSelector(form)

            val textAndHiddenValues = for (input <- formSelector.select("input[type='text'], input[type='hidden']")
                                           if validValue(input.getAttribute("value"))) yield {
              input.getAttribute("name") -> input.getAttribute("value")
            }

            val radioAndCheckboxValues = for (input <- formSelector.select("input[checked]")) yield {
              input.getAttribute("name") -> (Option(input.getAttribute("value")) getOrElse "on")
            }

            val textAreaValues = for (input <- formSelector.select("textarea[name]")
                                      if validValue(input.getTextContent)) yield {
              input.getAttribute("name") -> input.getTextContent
            }

            val selectValues = for (select <- formSelector.select("select");
                                    selectSelector = new NodeSelector(select);
                                    firstSelected = selectSelector.select("option[selected]").headOption;
                                    first = selectSelector.select("option").headOption;
                                    option <- firstSelected orElse first
                                    if validValue(option.getAttribute("value"))) yield {
              select.getAttribute("name") -> option.getAttribute("value")
            }

            val buttonValue = for (btnExpr <- formButton;
                                   btnCss <- btnExpr(session);
                                   btn = selector.selectFirst(btnCss);
                                   name = btn.getAttribute("name")
                                   if name != null) yield name -> (Option(btn.getAttribute("value")) getOrElse "")

            List.concat(
              textAndHiddenValues,
              radioAndCheckboxValues,
              textAreaValues,
              selectValues,
              buttonValue
            ).filterNot(exclusionSet contains _._1)
          }
        }
    }
  }
  
  private[clickscript] val extractCurrentUri = currentLocation.saveAs(lastUriVarName)
}