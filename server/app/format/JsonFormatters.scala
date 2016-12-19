package format

import music.Track
import play.api.libs.json._
import play.api.mvc.WebSocket.MessageFlowTransformer
import shared.Protocol.{ClientMessage, ServerMessage}
import shared.{User, UserScore}

object JsonFormatters {
  implicit val trackFormat: Format[Track] = Json.format[Track]
  implicit val userFormatter: Format[User] = Json.format[User]
  implicit val scoreFormatter: Format[UserScore] = Json.format[UserScore]
  implicit val clientMessageFormat: Format[ClientMessage] = Json.format[ClientMessage]
  implicit val serverMessageFormat: Format[ServerMessage] = Json.format[ServerMessage]
  implicit val messageFormatter: MessageFlowTransformer[ClientMessage, ServerMessage] =
    MessageFlowTransformer.jsonMessageFlowTransformer[ClientMessage, ServerMessage]
}