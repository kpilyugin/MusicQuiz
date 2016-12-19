package controllers

import java.util.concurrent.TimeUnit
import javax.inject.{Inject, Named}

import actors.UserParentActor
import akka.NotUsed
import akka.actor.{ActorRef, ActorSystem}
import akka.pattern.ask
import akka.stream.scaladsl.{Flow, Keep, Sink, Source}
import akka.stream.{Materializer, OverflowStrategy}
import akka.util.Timeout
import org.reactivestreams.Publisher
import play.api.mvc._
import shared.Protocol.{ServerMessage, ClientMessage}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}

class Application @Inject()(@Named("userParentActor") userParentActor: ActorRef,
                            @Named("scoresPersisterActor") scoresPersisterActor: ActorRef)
                           (implicit actorSystem: ActorSystem,
                            mat: Materializer,
                            ec: ExecutionContext) extends Controller {
  import format.JsonFormatters._

  def index = Action { implicit request =>
    Ok(views.html.index())
  }

  def startWs(username: String): WebSocket = WebSocket.acceptOrResult[ClientMessage, ServerMessage] {
    request =>
      wsFutureFlow(username).map { io =>
        Right(io)
      }.recover {
        case e => Left(BadRequest(e.toString))
      }
  }

  def wsFutureFlow(username: String): Future[Flow[ClientMessage, ServerMessage, _]] = {
    val (webSocketOut: ActorRef, webSocketIn: Publisher[ServerMessage]) = createWebSocketConnections()
    val userActorFuture = createUserActor(username, webSocketOut)
    userActorFuture.map { userActor =>
      createWebSocketFlow(username, webSocketIn, userActor)
    }
  }

  def createWebSocketConnections(): (ActorRef, Publisher[ServerMessage]) = {
    val source: Source[ServerMessage, ActorRef] = {
      Source.actorRef[ServerMessage](10, OverflowStrategy.dropTail)
    }

    val sink: Sink[ServerMessage, Publisher[ServerMessage]] = Sink.asPublisher(fanout = false)
    source.toMat(sink)(Keep.both).run()
  }

  def createWebSocketFlow(username: String, webSocketIn: Publisher[ServerMessage], userActor: ActorRef):
        Flow[ClientMessage, ServerMessage, NotUsed] = {
    val flow = {
      val sink = Sink.actorRef(userActor, akka.actor.Status.Success(()))
      val source = Source.fromPublisher(webSocketIn)
      Flow.fromSinkAndSource(sink, source)
    }

    val flowWatch: Flow[ClientMessage, ServerMessage, NotUsed] = flow.watchTermination() { (_, termination) =>
      termination.foreach { done =>
        actorSystem.stop(userActor)
      }
      NotUsed
    }
    flowWatch
  }

  def createUserActor(name: String, webSocketOut: ActorRef): Future[ActorRef] = {
    val userActorFuture = {
      implicit val timeout = Timeout(100, TimeUnit.MILLISECONDS)
      (userParentActor ? UserParentActor.Create(name, webSocketOut, scoresPersisterActor)).mapTo[ActorRef]
    }
    userActorFuture
  }
}
