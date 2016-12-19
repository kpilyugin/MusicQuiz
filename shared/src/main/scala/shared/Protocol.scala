package shared

import music.Track

object Protocol {
  case class ServerMessage(answer: Int,
                           tracks: Seq[Track],
                           scores: Seq[UserScore])

  case class ClientMessage(username: String,
                           genre: String,
                           answer: Int)
}
