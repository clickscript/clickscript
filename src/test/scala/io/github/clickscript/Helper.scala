package io.github.clickscript

import java.nio.charset.StandardCharsets
import java.util

import com.ning.http.client.uri.Uri
import io.gatling.http.response.{Response, StringResponseBody, ResponseBody, HttpResponse}
import com.ning.http.client._
import io.gatling.core.check.CheckBuilder
import io.gatling.http.check.HttpCheck
import io.gatling.core.session.Session
import io.gatling.core.validation.Success
import io.github.clickscript.Predef._

/**
 * Created by hisg085 on 04/04/2014.
 */
object Helper {
  object Status extends HttpResponseStatus(Uri.create("http://localhost/index.html"), null) {
    def getStatusCode: Int = 200
    def getProtocolText: String = "HTTP/1.1"
    def getProtocolMinorVersion: Int = 1
    def getProtocolMajorVersion: Int = 1
    def getProtocolName: String = "HTTP"
    def getStatusText: String = "OK"
    // Not actually used, so we use a stub implementation
    def prepareResponse(headers: HttpResponseHeaders, bodyParts: util.List[HttpResponseBodyPart]) = ???
  }

  def dummyResponse(body: String) = {
    HttpResponse(
      null,
      None,
      Some(Status),
      new FluentCaseInsensitiveStringsMap,
      StringResponseBody(body, StandardCharsets.UTF_8),
      Map.empty,
      body.length,
      StandardCharsets.UTF_8,
      0L,
      1L,
      2L,
      3L
    )
  }

  def dummyHtmlBody(body: String) = dummyResponse(s"""
  |<!DOCTYPE html>
  |<html>
  |  <head>
  |    <title>Hello World</title>
  |  </head>
  |  <body>
  |    $body
  |  </body>
  |</html>
  """.stripMargin)

  def applyCheck(checkBuilder: CheckBuilder[HttpCheck, Response, _, _], body: String, sess: Session = new Session("TestRun", "testUser")) = {
    implicit val cache = scala.collection.mutable.Map.empty[Any, Any]
    val response = dummyHtmlBody(body)
    val success = checkBuilder.build.check(response, sess).map(_.update.get(sess)).asInstanceOf[Success[Session]]
    success.value
  }

  def prepareSession(body: String) = {
    applyCheck(saveLastResponse, body, new Session("TestRun", "testUser").set(lastUriVarName, "http://localhost/index.html"))
  }
}
