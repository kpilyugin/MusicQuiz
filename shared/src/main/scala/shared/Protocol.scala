package shared

import music.Track

object Protocol {

  case class Message(username: String,
                     genre: String,
                     answer: Int,
                     tracks: Seq[Track],
                     scores: Seq[UserScore])

  case class ServerMessage(username: String,
                           tracks: Seq[Track],
                           scores: Seq[UserScore])

  case class ClientMessage(genre: String,
                           answer: Int)

}
