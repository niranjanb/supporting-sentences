package org.allenai.ari.sentences

import org.allenai.ari.http._
import org.allenai.extraction.api.JsonProtocol.{ PipelineRequest, PipelineResponse }

import dispatch.url
import spray.json._

import scala.concurrent.Await
import scala.concurrent.duration._
import org.allenai.common.Logging
import org.allenai.ari.models.AriException

class FerretWrapper(ermineUrl: String) extends Logging {


  /** Query Ermine. Return raw response from Ermine.
    */
  private def queryErmine(text: String): String = {
    val request = PipelineRequest(Map("text" -> text))
    val requestJson = request.toJson.compactPrint
    logger.debug(s"Body being sent to ermine: $requestJson")
    val req = url(ermineUrl)
      .setMethod("POST")
      .addHeader("Content-Type", "application/json")
      .setBody(requestJson.getBytes("UTF-8"))

    val httpRequest = AriHttp(req)
    val resp = Await.result(httpRequest, Duration(20, SECONDS))

    resp.getStatusCode() match {
      case 200 =>
        logger.debug(s"Response body from ermine: ${resp.getResponseBody}")
        resp.getResponseBody().parseJson.convertTo[PipelineResponse].output
      case non200 =>
        throw new AriException(s"Ermine service responded ${non200}: ${resp.getStatusText()}",
          None, None)
    }
  }

}

object FerretWrapper extends App{
  val ferret = new FerretWrapper("http://ermine.dev.allenai.org:8080/pipeline/ferret-text")
  val response = ferret.queryErmine("Inactivity causes health issues and also a lack of sleep, excessive alcohol consumption, and neglect of oral hygiene")
  println(s"response $response")
}
