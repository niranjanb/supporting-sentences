package org.allenai.ari.sentences

import org.allenai.extraction.api.JsonProtocol.{PipelineResponse, PipelineRequest}
import org.allenai.ari.models.AriException
import org.allenai.common.Logging
import org.allenai.ari.http._

import spray.json._

import dispatch.url

import scala.concurrent.Await
import scala.concurrent.duration._

object ErmineArilogClient extends App {

  val ermineUrl = "http://ermine.allenai.org:8080/pipeline/ferret-text"
  val client = new ErmineArilogClient(ermineUrl)
  val sentence = "The scavengers and decomposers help move energy through the food chain."
  println(s"Parsing sentence: ${sentence}")
  println(s"Ferret output: ${client.ermineResponse(sentence)}")

}
class ErmineArilogClient (ermineUrl: String) extends Logging {

  def ermineResponse(sentence: String): String = {
    val request = PipelineRequest(Map("text" -> sentence))
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
