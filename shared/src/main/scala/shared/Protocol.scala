package shared

import music.Track

object Protocol {
  case class Message(answer: Int, tracks: Seq[Track])
}
