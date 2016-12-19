package actors

import java.util.concurrent.TimeUnit
import javax.inject.Inject

import akka.actor.{Actor, ActorRef}
import akka.event.LoggingReceive
import akka.util.Timeout
import com.google.inject.assistedinject.Assisted
import controllers.TrackFinder._
import music.Track
import play.api.Configuration
import play.api.libs.concurrent.InjectedActorSupport
import play.api.libs.ws.WSClient
import shared.Protocol.{ClientMessage, ServerMessage}
import shared.UserScore

import scala.concurrent.ExecutionContext

class PlayerActor @Inject()(@Assisted("out") out: ActorRef,
                            @Assisted("scoresActor") scoresActor: ActorRef)
                           (wsClient: WSClient,
                            configuration: Configuration,
                            implicit val ec: ExecutionContext) extends Actor {
  implicit val timeout = Timeout(1, TimeUnit.SECONDS)
  var myScores: Seq[UserScore] = Seq()

  override def receive: Receive = {
    case SetScores(scores) =>
      myScores = scores

    case Join(username) =>
      scoresActor ! Update(username, 0)
      out ! ServerMessage(-1, Seq(), myScores)

    case StartQuiz(genre) =>
      findTracks(wsClient, genre).onSuccess({
        case tracks: Seq[Track] =>
          val r = scala.util.Random
          out ! ServerMessage(r.nextInt(5), tracks, myScores)
      })

    case ClientMessage(username, genre, answer) =>
      scoresActor ! Update(username, answer)
      self ! StartQuiz(genre)
  }
}

object PlayerActor {
  trait Factory {
    def apply(@Assisted("out") out: ActorRef, @Assisted("scoresActor") scoresActor: ActorRef): Actor
  }
}

class UserParentActor @Inject()(childFactory: PlayerActor.Factory) extends Actor with InjectedActorSupport {
  import UserParentActor._

  override def receive: Receive = LoggingReceive {
    case Create(username, out, scoresActor) =>
      val child: ActorRef = injectedChild(childFactory(out, scoresActor), s"userActor-$username")
      sender() ! child
      child ! Join(username)
  }
}

object UserParentActor {
  case class Create(id: String, out: ActorRef, scoresActor: ActorRef)
}

case class Join(username: String)
case class StartQuiz(genre: String)
