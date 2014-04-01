package io.github.clickscript

import io.gatling.core.session.Expression
import io.gatling.core.Predef._
import io.gatling.http.Predef._
import io.gatling.core.check.extractor.css.CssExtractor
import jodd.lagarto.dom.{Node, NodeSelector}
import io.gatling.core.validation._
import scala.collection.JavaConversions._
import scala.collection.breakOut
import scala.util.control.NonFatal

private[clickscript] class Lazy[X](f: => X) {
  lazy val x = f
}

private[clickscript] object Lazy {
  implicit def autoExtract[X](h: Lazy[X]): X = h.x
  def apply[X](x: => X) = new Lazy(x)
}

object Predef {
  def goTo(stepName: Expression[String], url: Expression[String]) =
    http(stepName)
      .get(url)
      .check(saveLastResponse, extractCurrentUri)

  def click(stepName: Expression[String], linkSelector: Expression[String], occurence: Int = 0) =
    http(stepName)
      .get(extractLink(linkSelector, occurence))
      .check(saveLastResponse, extractCurrentUri)

  def submitPost(stepName: Expression[String], formSelector: Expression[String]) =
    SubmitPostBuilder(stepName, formSelector)

  def submitGet(stepName: Expression[String], formSelector: Expression[String]) =
    SubmitGetBuilder(stepName, formSelector)

  def exitBrowser = {s: Session =>
    s.removeAll(lastResponseVarName, lastUriVarName).success
  }

  private[clickscript] val lastResponseVarName = "__clickScript_lastResponse"
  private[clickscript] val lastUriVarName =  "__clickScript_lastUri"

  private[clickscript] val saveLastResponse = {
    bodyString.transform {
      bodyOpt =>
        bodyOpt map {
          body =>
            Lazy(CssExtractor.domBuilder.parse(body))
        }
    }.saveAs(lastResponseVarName)
  }

  private[clickscript] implicit def validation2Option[A](v: Validation[A]) = v match {
    case Success(x) => Some(x)
    case Failure(_) => None
  }

  private[clickscript] def extractLink(linkSelector: Expression[String], occurence: Int = 0) = {session: Session =>
    try {
      for (css <- linkSelector(session);
         lastResponse <- session(lastResponseVarName).validate[Lazy[Node]]) yield {
        val selector = new NodeSelector(lastResponse)
        val link = selector.select(css).get(occurence)
        link.getAttribute("href")
      }
    } catch {
      case NonFatal(e) =>
        Failure(s"Error extracting link address with CSS $linkSelector: ${e.getLocalizedMessage}")
    }
  }

  private[clickscript] def extractFormUrl(formSelector: Expression[String], formButton: Option[Expression[String]]) = {session: Session =>
    formSelector(session) flatMap {css =>
      session(lastResponseVarName).validate[Lazy[Node]] flatMap {
        lastResponse =>
          try {
            val selector = new NodeSelector(lastResponse)
            val form = selector.selectFirst(css)
            val action = form.getAttribute("action")
            val buttonAction = for (expr <- formButton;
                                    btnCss <- expr(session);
                                    action = selector.selectFirst(btnCss).getAttribute("formaction")
                                    if action != null) yield action
            val overallAction = buttonAction orElse Option(action)
            overallAction match {
              case Some(a) if a startsWith "?" => session(lastUriVarName).validate[String] map (_ + a)
              case Some("") | None => session(lastUriVarName).validate[String]
              case Some(a) => a.success
            }
          } catch {
            case NonFatal(e) => Failure(s"Could not extract form URL from $css: ${e.getLocalizedMessage}")
          }
      }
    }
  }

  private[clickscript] def validValue(x: String) = (x != null) && (x != "")

  private[clickscript] def extractPrefilledValues(formSelector: Expression[String], formButton: Option[Expression[String]], exclusions: Seq[Expression[String]]) = {session: Session =>
    val exclusionSet: Set[String] = exclusions.flatMap(ex => ex(session): Option[String])(breakOut)
    for (css <- formSelector(session);
         lastResponse <- session(lastResponseVarName).validate[Lazy[Node]]) yield {
      val selector = new NodeSelector(lastResponse)
      val form = selector.selectFirst(css)
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
  
  private[clickscript] val extractCurrentUri = currentLocation.saveAs(lastUriVarName)
}

object SubmitPostBuilder {
  implicit def submitPostBuilder2HttpRequestBuilder(builder: SubmitPostBuilder) = builder.toHttpBuilder
}

trait Builder[X] {
  def enterField(name: Expression[String], value: Expression[Any]): X
  def tickCheckbox(name: Expression[String]) = enterField(name, "on")
}

case class SubmitPostBuilder private[clickscript](stepName: Expression[String],
                                             formSelector: Expression[String],
                                             formButton: Option[Expression[String]] = None,
                                             userParams: Seq[(Expression[String], Expression[Any])] = Nil) extends Builder[SubmitPostBuilder] {
  import Predef._

  def formButton(btnCss: Expression[String]) = copy(formButton = Option(btnCss))

  def enterField(name: Expression[String], value: Expression[Any]) = copy(userParams = userParams :+ (name -> value))

  def toHttpBuilder = http(stepName)
    .post(extractFormUrl(formSelector, formButton))
    .paramsSeq(extractPrefilledValues(formSelector, formButton, userParams map (_._1)))
    .paramsSeq { session =>
      for ((key, value) <- userParams;
           k <- key(session): Option[String];
           v <- value(session): Option[Any]) yield (k, v)
    }
    .check(saveLastResponse, extractCurrentUri)
}

object SubmitGetBuilder {
  implicit def submitGetBuilder2HttpRequestBuilder(builder: SubmitGetBuilder) = builder.toHttpBuilder
}

case class SubmitGetBuilder private[clickscript](stepName: Expression[String],
                                                  formSelector: Expression[String],
                                                  formButton: Option[Expression[String]] = None,
                                                  userParams: Seq[(Expression[String], Expression[Any])] = Nil) extends Builder[SubmitGetBuilder] {
  import Predef._

  def formButton(btnCss: Expression[String]) = copy(formButton = Option(btnCss))

  def enterField(name: Expression[String], value: Expression[Any]) = copy(userParams = userParams :+ (name -> value))

  def toHttpBuilder = http(stepName)
    .get(extractFormUrl(formSelector, formButton))
    .queryParamsSeq(extractPrefilledValues(formSelector, formButton, userParams map (_._1)))
    .queryParamsSeq { session =>
    for ((key, value) <- userParams;
         k <- key(session): Option[String];
         v <- value(session): Option[Any]) yield (k, v)
    }
    .check(saveLastResponse, extractCurrentUri)
}