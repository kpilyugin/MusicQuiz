package controllers

import java.util.concurrent.TimeUnit

import akka.actor.{Actor, Props}
import akka.pattern.ask
import akka.util.Timeout
import controllers.JsonFormatters._
import controllers.TrackFinder._
import music.Track
import play.api.Play.current
import play.api.libs.concurrent.Akka
import play.api.libs.iteratee._
import play.api.libs.json._
import play.api.mvc.WebSocket.FrameFormatter
import play.api.mvc._
import shared.Protocol.Message
import shared.{Protocol, User}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object Application extends Controller {

  def index = Action { implicit request =>
    Ok(views.html.index())
  }

  def chatWS(username: String): WebSocket[Message, Message] = WebSocket.tryAccept[Message] { request =>
    QuizRoom.join(username).map { io =>
      Right(io)
    }
  }
}

object QuizRoom {

  lazy val default = {
    Akka.system.actorOf(Props[QuizRoom])
  }

  implicit val timeout = Timeout(1, TimeUnit.SECONDS)

  def join(username: String): Future[(Iteratee[Message, _], Enumerator[Message])] = {
    (default ? Join(username)).map {
      case Connected(user, enumerator) =>
        val iteratee = Iteratee.foreach[Message] { event =>
          processMessage(event)
        }
        (iteratee, enumerator)
    }
  }

  def processMessage(message: Message): Unit = {
    println("processMessage")
    default ? Start("")
  }
}

class QuizRoom extends Actor {
  val (enumerator, channel) = Concurrent.broadcast[Message]
  var genre: String = _

  def receive = {
    case Join(username) => {
      val user = User(username)
      sender ! Connected(user, enumerator)
      self ! Start(username)
    }

    case Start(genre) => startGame(genre)
  }

  def startGame(genre: String) {
    if (genre != "") {
      this.genre = genre
    }
    findTracks(this.genre).onSuccess({
      case tracks: Seq[Track] =>
        val r = scala.util.Random
        val msg: Message = Message(r.nextInt(5), tracks)
        println(Json.stringify(Json.toJson(msg)))
        channel.push(msg)
    })
  }
}

case class Join(username: String)

case class Start(genre: String)

case class Connected(user: User, enumerator: Enumerator[Protocol.Message])

case class CannotConnect(msg: String)

object JsonFormatters {
  implicit val trackFormat: Format[Track] = Json.format[Track]
  implicit val messageFormat: Format[Message] = Json.format[Message]
  implicit val messageFormatter: FrameFormatter[Message] = FrameFormatter.jsonFrame[Message]
}