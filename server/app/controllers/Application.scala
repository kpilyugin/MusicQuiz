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
import shared.Protocol.Message

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

  def startWs(username: String): WebSocket = WebSocket.acceptOrResult[Message, Message] {
    request =>
      wsFutureFlow(username).map { io =>
        Right(io)
      }.recover {
        case e => Left(BadRequest(e.toString))
      }
  }

  def wsFutureFlow(username: String): Future[Flow[Message, Message, _]] = {
    val (webSocketOut: ActorRef, webSocketIn: Publisher[Message]) = createWebSocketConnections()
    val userActorFuture = createUserActor(username, webSocketOut)
    userActorFuture.map { userActor =>
      createWebSocketFlow(username, webSocketIn, userActor)
    }
  }

  def createWebSocketConnections(): (ActorRef, Publisher[Message]) = {
    val source: Source[Message, ActorRef] = {
      Source.actorRef[Message](10, OverflowStrategy.dropTail)
    }

    val sink: Sink[Message, Publisher[Message]] = Sink.asPublisher(fanout = false)
    source.toMat(sink)(Keep.both).run()
  }

  def createWebSocketFlow(username: String, webSocketIn: Publisher[Message], userActor: ActorRef): Flow[Message, Message, NotUsed] = {
    val flow = {
      val sink = Sink.actorRef(userActor, akka.actor.Status.Success(()))
      val source = Source.fromPublisher(webSocketIn)
      Flow.fromSinkAndSource(sink, source)
    }

    val flowWatch: Flow[Message, Message, NotUsed] = flow.watchTermination() { (_, termination) =>
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
