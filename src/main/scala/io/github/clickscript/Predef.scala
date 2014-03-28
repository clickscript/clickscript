package io.github.clickscript

import io.gatling.core.session.Expression
import io.gatling.core.Predef._
import io.gatling.http.Predef._
import io.gatling.core.check.extractor.css.CssExtractor
import jodd.lagarto.dom.NodeSelector
import io.gatling.core.validation._
import scala.collection.JavaConversions._
import scala.collection.breakOut

object Predef {
  val lastResponseVarName = "__clickScript_lastResponse"
  val lastUriVarName =  "__clickScript_lastUri"

  val extractAllCss = {
    bodyString.transform {
      bodyOpt =>
        bodyOpt map {
          body =>
            CssExtractor.parse(body)
        }
    }.saveAs(lastResponseVarName)
  }

  implicit def validation2Option[A](v: Validation[A]) = v match {
    case Success(x) => Some(x)
    case Failure(_) => None
  }

  def extractLink(linkSelector: Expression[String], occurence: Int = 0) = {session: Session =>
    for (css <- linkSelector(session);
         lastResponse <- session(lastResponseVarName).validate[NodeSelector]) yield {
      val link = lastResponse.select(css).get(occurence)
      link.getAttribute("href")
    }
  }

  def extractFormUrl(formSelector: Expression[String], formButton: Option[Expression[String]]) = {session: Session =>
    formSelector(session) flatMap {css =>
      session(lastResponseVarName).validate[NodeSelector] flatMap {lastResponse =>
        val form = lastResponse.selectFirst(css)
        val action = form.getAttribute("action")
        val buttonAction = for (expr <- formButton;
                                btnCss <- expr(session);
                                action = lastResponse.selectFirst(btnCss).getAttribute("formaction")
                                if action != null) yield action
        val overallAction = buttonAction orElse Option(action)
        overallAction match {
          case Some(a) if a startsWith "?" => session(lastUriVarName).validate[String] map (_ + a)
          case Some("") | None => session(lastUriVarName).validate[String]
          case Some(a) => a.success
        }
      }
    }
  }

  def extractPrefilledValues(formSelector: Expression[String], formButton: Option[Expression[String]], exclusions: Seq[Expression[String]]) = {session: Session =>
    val exclusionSet: Set[String] = exclusions.flatMap(ex => ex(session): Option[String])(breakOut)
    for (css <- formSelector(session);
         lastResponse <- session(lastResponseVarName).validate[NodeSelector]) yield {
      val form = lastResponse.selectFirst(css)
      val formSelector = new NodeSelector(form)

      val textAndHiddenValues = for (input <- formSelector.select("input[type='text'], input[type='hidden']")
                                     if input.getAttribute("value") != "") yield {
        input.getAttribute("name") -> input.getAttribute("value")
      }

      val radioAndCheckboxValues = for (input <- formSelector.select("input[checked]")
                                        if input.getAttribute("value") != "") yield {
        input.getAttribute("name") -> input.getAttribute("value")
      }

      val textAreaValues = for (input <- formSelector.select("textarea[name]")
                                if input.getTextContent != "") yield {
        input.getAttribute("name") -> input.getTextContent
      }

      val selectValues = for (select <- formSelector.select("select");
                              selectSelector = new NodeSelector(select);
                              firstSelected = selectSelector.select("option[selected]").headOption;
                              first = selectSelector.select("option").headOption;
                              option <- firstSelected orElse first
                              if option.getAttribute("value") != "") yield {
        select.getAttribute("name") -> option.getAttribute("value")
      }

      val buttonValue = for (btnExpr <- formButton;
                             btnCss <- btnExpr(session);
                             btn = lastResponse.selectFirst(btnCss);
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
  
  val extractCurrentUri = currentLocation.saveAs(lastUriVarName)

  def goTo(stepName: Expression[String], url: Expression[String]) =
    http(stepName)
      .get(url)
      .check(extractAllCss, extractCurrentUri)
  
  def click(stepName: Expression[String], linkSelector: Expression[String], occurence: Int = 0) =
    http(stepName)
      .get(extractLink(linkSelector, occurence))
      .check(extractAllCss, extractCurrentUri)

  def submitPost(stepName: Expression[String], formSelector: Expression[String]) =
    SubmitPostBuilder(stepName, formSelector)

  def submitGet(stepName: Expression[String], formSelector: Expression[String]) =
    SubmitGetBuilder(stepName, formSelector)
}

object SubmitPostBuilder {
  implicit def submitPostBuilder2HttpRequestBuilder(builder: SubmitPostBuilder) = builder.toHttpBuilder
}

case class SubmitPostBuilder private[clickscript](stepName: Expression[String],
                                             formSelector: Expression[String],
                                             formButton: Option[Expression[String]] = None,
                                             userParams: Seq[(Expression[String], Expression[Any])] = Nil) {
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
    .check(extractAllCss, extractCurrentUri)
}

object SubmitGetBuilder {
  implicit def submitGetBuilder2HttpRequestBuilder(builder: SubmitGetBuilder) = builder.toHttpBuilder
}

case class SubmitGetBuilder private[clickscript](stepName: Expression[String],
                                                  formSelector: Expression[String],
                                                  formButton: Option[Expression[String]] = None,
                                                  userParams: Seq[(Expression[String], Expression[Any])] = Nil) {
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
    .check(extractAllCss, extractCurrentUri)
}