package actors

import akka.persistence.{PersistentActor, SnapshotOffer}
import shared.UserScore

import scala.collection.mutable

class ScoresActor extends PersistentActor {
  var users: mutable.Map[String, Int] = mutable.Map.empty[String, Int]

  override def persistenceId: String = "playerScores"

  override def receiveRecover: Receive = {
    case SnapshotOffer(_, snapshot: mutable.Map[String, Int]) =>
      users = snapshot
  }

  override def receiveCommand: Receive = {
    case Update(username, change) =>
      update(username, change)
      var scores: Seq[UserScore] = Seq()
      users.foreach({ case (user, score) =>
        scores = scores :+ UserScore(user, score)
      })
      sender() ! SetScores(scores)
      saveSnapshot(users)
  }

  def update(username: String, change: Int): Unit = {
    val score: Int = users.getOrElse(username, 0)
    users.put(username, score + change)
  }
}

case class Update(username: String, change: Int)
case class SetScores(scores: Seq[UserScore])
