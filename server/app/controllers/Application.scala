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
import shared.{Protocol, User, UserScore}

import scala.collection.mutable
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object Application extends Controller {

  def index = Action { implicit request =>
    Ok(views.html.index())
  }

  def startWs(username: String): WebSocket[Message, Message] = WebSocket.tryAccept[Message] {
    request =>
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
    default ? Reply(message)
  }
}

class QuizRoom extends Actor {
  val (enumerator, channel) = Concurrent.broadcast[Message]
  val users = mutable.Map.empty[String, Int]

  def getScores: Seq[UserScore] = {
    var scores: Seq[UserScore] = Seq()
    users.foreach({ case (user, score) =>
      scores = scores :+ UserScore(user, score)
    })
    scores
  }

  def receive = {
    case Join(username) => {
      if (!users.contains(username)) {
        users.put(username, 0)
      }
      val user = User(username)
      sender ! Connected(user, enumerator)
      self ! Login(username)
    }

    case Quiz(genre) => startQuiz(genre)

    case Login(username) =>
      val msg: Message = Message(username, "", -1, Seq(), getScores)
      channel.push(msg)

    case Reply(message) =>
      val score: Int = users.getOrElse(message.username, 0)
      users.put(message.username, score + message.answer)
      self ! Quiz(message.genre)
  }

  def startQuiz(genre: String) {
    findTracks(genre).onSuccess({
      case tracks: Seq[Track] =>
        val r = scala.util.Random
        val msg: Message = Message("", genre, r.nextInt(5), tracks, getScores)
        channel.push(msg)
    })
  }
}

case class Join(username: String)

case class Quiz(genre: String)

case class Login(username: String)

case class Reply(message: Message)

case class Connected(user: User, enumerator: Enumerator[Protocol.Message])

case class CannotConnect(msg: String)

object JsonFormatters {
  implicit val trackFormat: Format[Track] = Json.format[Track]
  implicit val userFormatter: Format[User] = Json.format[User]
  implicit val scoreFormatter: Format[UserScore] = Json.format[UserScore]
  implicit val messageFormat: Format[Message] = Json.format[Message]
  implicit val messageFormatter: FrameFormatter[Message] = FrameFormatter.jsonFrame[Message]
}