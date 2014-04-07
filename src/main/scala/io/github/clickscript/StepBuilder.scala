package io.github.clickscript

import io.gatling.core.session._
import io.gatling.core.Predef._
import io.gatling.http.Predef._
import io.github.clickscript.Predef._

/**
 * Created by hisg085 on 07/04/2014.
 */
case class StepBuilder(name: Expression[String]) {
  def goTo(url: Expression[String]) =
    http(name)
      .get(url)
      .check(saveLastResponse, extractCurrentUri)

  def click(linkSelector: Expression[String], occurence: Int = 0) =
    http(name)
      .get(extractLink(linkSelector, occurence))
      .check(saveLastResponse, extractCurrentUri)

  def form(formSelector: Expression[String], occurence: Int = 0): FormBuilder = FormBuilder(name, formSelector)
  def form: FormBuilder = form("form", 0)

}
