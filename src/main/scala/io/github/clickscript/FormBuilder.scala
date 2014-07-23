package io.github.clickscript

import io.gatling.core.session._
import io.gatling.core.Predef._
import io.gatling.http.Predef._
import io.github.clickscript.Predef._

/**
 * Created by hisg085 on 07/04/2014.
 */
case class FormBuilder(stepName: Expression[String],
                       formSelector: Expression[String],
                       occurence: Int = 0,
                       formButton: Option[Expression[String]] = None,
                       userParams: Seq[(Expression[String], Expression[Any])] = Nil) {

  def enterField(name: Expression[String], value: Expression[Any]) = copy(userParams = userParams :+ (name -> value))

  def tickCheckbox(name: Expression[String]) = enterField(name, "on")

  def untickCheckbox(name: Expression[String]) = enterField(name, "")

  private def formButton(button: Expression[String]) = copy(formButton = Some(button))

  def clickPostButton(button: Expression[String]) = formButton(button).post

  def clickGetButton(button: Expression[String]) = formButton(button).get

  def post = http(stepName)
    .post(extractFormUrl(formSelector, occurence, formButton))
    .formParamSeq(extractPrefilledValues(formSelector, occurence, formButton, userParams map (_._1)))
    .formParamSeq { session =>
    for ((key, value) <- userParams;
         k <- key(session): Option[String];
         v <- value(session): Option[Any]) yield (k, v)
  }
    .check(saveLastResponse, extractCurrentUri)

  def get = http(stepName)
    .get(extractFormUrl(formSelector, occurence, formButton))
    .queryParamSeq(extractPrefilledValues(formSelector, occurence, formButton, userParams map (_._1)))
    .queryParamSeq { session =>
    for ((key, value) <- userParams;
         k <- key(session): Option[String];
         v <- value(session): Option[Any]) yield (k, v)
  }
    .check(saveLastResponse, extractCurrentUri)

}
