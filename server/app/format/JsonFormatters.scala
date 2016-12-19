package format

import music.Track
import play.api.libs.json._
import play.api.mvc.WebSocket.MessageFlowTransformer
import shared.Protocol.Message
import shared.{User, UserScore}

object JsonFormatters {
  implicit val trackFormat: Format[Track] = Json.format[Track]
  implicit val userFormatter: Format[User] = Json.format[User]
  implicit val scoreFormatter: Format[UserScore] = Json.format[UserScore]
  implicit val messageFormat: Format[Message] = Json.format[Message]
  implicit val messageFormatter: MessageFlowTransformer[Message, Message] =
    MessageFlowTransformer.jsonMessageFlowTransformer[Message, Message]
}