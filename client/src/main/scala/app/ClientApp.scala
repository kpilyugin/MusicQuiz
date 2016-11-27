package app

import music.Track
import org.scalajs.dom._
import org.scalajs.jquery.{jQuery => $}
import shared.Protocol.Message
import upickle.default._

import scala.scalajs.js
import scala.scalajs.js.Dynamic.{global => g}
import scala.scalajs.js.annotation.JSExport
import scalatags.JsDom.all.{raw, _}

@JSExport
object ClientApp extends js.JSApp {

  @JSExport
  override def main(): Unit = {
  }

  var wsBaseUrl: String = ""
  var client: Option[QuizClient] = None
  var score: Int = 0

  def startPanel = div(id := "startPanel") {
    form(`class` := "form-inline")(
      div(id := "usernameForm", `class` := "form-group")(
        select(id := "genre", `class` := "form-control", `type` := "text", placeholder := "Genre")(
          option("Rock"),
          option("Pop"),
          option("Electro"),
          option("Country")
        )
      ),
      span(style := "margin:0px 5px"),
      button(`class` := "btn btn-default", `type` := "button", onclick := { () =>
        val input = $("#genre").value().toString.trim
        if (input == "") {
          $("#usernameForm").addClass("has-error")
        } else {
          $("#usernameForm").removeClass("has-error")
          client = QuizClient.connect(wsBaseUrl, input).map { c =>
            $("#loginAs").text(s"Login as: ${c.genre}")
            $("#username").value("")
            c
          }
        }
        false
      })("Start game"),
      span(style := "margin:0px 5px"),
      button(`class` := "btn btn-default", `type` := "button", onclick := { () =>
        stop()
      })("Stop")
    )
  }

  def tracksPanel = div(id := "tracksPanel")(
    div(`class` := "container")(
      h3("Score = 0", id := "score"),
      table(`class` := "table table-hover", id := "tracks")
    )
  )

  def addTrack(name: String, index: Int) = {
    tbody(
      tr(raw(name), onclick := { () =>
        onClick(index)
      }, id := "track" + index.toString)
    )
  }

  def stop(): Unit = {
    client.foreach {
      _.stop()
    }
  }

  def onClick(index: Int): Unit = {
    client.foreach {
      _.reply(index)
    }
  }

  object QuizClient {
    def connect(url: String, genre: String): Option[QuizClient] = {
      if (g.window.WebSocket.toString != "undefined") {
        return Some(new QuizClient(url, genre))
      }
      None
    }

    def receive(event: MessageEvent) = {
      val msg = read[Message](event.data.toString)

      msg match {
        case Message(answer, tracks) => {

          val tracksElem = document.getElementById("tracks")
          while (tracksElem.firstChild != null) {
            tracksElem.removeChild(tracksElem.firstChild)
          }
          for (i <- tracks.indices) {
            val track: Track = tracks.apply(i)
            val name: String = track.artist + " - " + track.name
            tracksElem.appendChild(addTrack(name, i).render)
          }

          val previewUrl: String = tracks.apply(answer).previewUrl
          AudioPlayer.play(previewUrl)
          client.foreach {
            _.setAnswer(answer)
          }
        }
      }
    }
  }

  class QuizClient(url: String, val genre: String) {
    var answer: Int = _
    val socket = new WebSocket(url + genre)
    socket.onmessage = QuizClient.receive _

    def reply(clicked: Int): Unit = {
      AudioPlayer.stop()
      val correct: Boolean = clicked == answer
      if (correct) {
        $("#track" + clicked).css("background-color", "green")
        score += 1
      } else {
        $("#track" + answer).css("background-color", "green")
        $("#track" + clicked).css("background-color", "red")
        score -= 1
      }
      $("#score").text("Score: " + score.toString)
      send(Message(clicked, Seq()))
    }

    def stop(): Unit = {
      AudioPlayer.stop()
      val tracksElem = document.getElementById("tracks")
      while (tracksElem.firstChild != null) {
        tracksElem.removeChild(tracksElem.firstChild)
      }
    }

    def send(msg: Message): Unit = {
      socket.send(write[Message](msg))
    }

    def setAnswer(answer: Int): Unit = {
      this.answer = answer
    }

    def close() = socket.close()
  }

  @JSExport
  def play(settings: js.Dynamic) = {
    this.wsBaseUrl = settings.wsBaseUrl.toString

    val content = document.getElementById("content")
    content.appendChild(startPanel.render)
    content.appendChild(tracksPanel.render)
  }
}