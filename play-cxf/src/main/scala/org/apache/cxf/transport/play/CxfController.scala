package org.apache.cxf.transport.play

import java.io.{InputStream, OutputStream}
import javax.inject.Inject

import akka.stream.scaladsl.Source
import akka.util.ByteString
import org.apache.cxf.message.{Message, MessageImpl}
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.iteratee.Enumerator
import play.api.libs.streams.Streams
import play.api.mvc._

import scala.collection.JavaConverters._
import scala.concurrent.Promise

class CxfController @Inject() (
  transportFactory: PlayTransportFactory
) extends Controller {

  val maxRequestSize = 1024 * 1024

  def handle(path: String = "") = Action.async(parse.raw(maxRequestSize)) { implicit request =>
    val delayedOutput = new DelayedOutputStream
    val replyPromise: Promise[Message] = Promise.apply()
    dispatchMessage(extractMessage, delayedOutput, replyPromise)

    val resultEnumerator = Enumerator.outputStream { os =>
      delayedOutput.setTarget(os)
    }
    replyPromise.future.map { outMessage =>
      Ok.chunked(
        Source.fromPublisher(Streams.enumeratorToPublisher(resultEnumerator >>> Enumerator.eof))
      ).as(outMessage.get(Message.CONTENT_TYPE).asInstanceOf[String])
    }
  }

  private def extractMessage()(implicit request: Request[RawBuffer]) = {
    val msg: Message = new MessageImpl
    msg.put(Message.HTTP_REQUEST_METHOD, request.method)
    msg.put(Message.REQUEST_URL, request.path)
    msg.put(Message.QUERY_STRING, request.rawQueryString)
    msg.put(Message.PROTOCOL_HEADERS, headersAsJava)
    msg.put(Message.CONTENT_TYPE, request.headers.get(Message.CONTENT_TYPE).orNull)
    msg.put(Message.ACCEPT_CONTENT_TYPE, request.headers.get(Message.ACCEPT_CONTENT_TYPE).orNull)
    msg.put("Remote-Address", request.remoteAddress)

    request.body.asBytes() foreach { arr: ByteString =>
      msg.setContent(classOf[InputStream], arr.iterator.asInputStream)
    }

    msg
  }

  private def endpointAddress()(implicit request: Request[RawBuffer]) = "play://" + request.host + request.path

  private def headersAsJava()(implicit request: Request[RawBuffer]) = {
    request.headers.toMap.mapValues(_.asJava).asJava
  }

  private def dispatchMessage(
    inMessage: Message,
    output: OutputStream,
    replyPromise: Promise[Message]
  )(implicit request: Request[RawBuffer]) {

    val dOpt = Option(transportFactory.getDestination(endpointAddress)).orElse(
      Option(transportFactory.getDestination(request.path))
    )
    dOpt match {
      case Some(destination) =>
        inMessage.put(Message.ENDPOINT_ADDRESS, destination.getFactoryKey)
        destination.dispatchMessage(inMessage, output, replyPromise)
      case _ =>
        replyPromise.failure(
          new IllegalArgumentException(s"Destination not found: [$endpointAddress] ${transportFactory.getDestinationsDebugInfo}")
        )
    }
  }
}