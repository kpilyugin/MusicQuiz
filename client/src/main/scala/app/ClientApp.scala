package app

import music.Track
import org.scalajs.dom._
import org.scalajs.jquery.{jQuery => $}
import shared.Protocol.Message
import shared.UserScore
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
      span(style := "margin:100px 100px"),
      div(id := "loginForm", `class` := "form-group")(
        div(id := "usernameForm", `class` := "input-group")(
          input(id := "username", `class` := "form-control", `type` := "text", placeholder := "Username")
        ),
        span(style := "margin:0px 5px"),
        button(`class` := "btn btn-default", `type` := "button", onclick := { () =>
          val username = $("#username").value().toString.trim
          if (username != "") {
            $("#usernameForm").removeClass("has-error")
            client = QuizClient.connect(wsBaseUrl, username).map { c =>
              $("#username").value("")
              c
            }
          } else {
            $("#usernameForm").addClass("has-error")
          }
          false
        })("Login")
      ),
      span(style := "margin:0px 10px"),
      div(id := "gameForm", `class` := "form-group hide")(
        label(id := "helloUser"),
        span(style := "margin:0px 5px"),
        label("Genre:"),
        span(style := "margin:0px 5px"),
        select(id := "genre", `class` := "form-control", `type` := "text", placeholder := "Genre")(
          option("Rock"),
          option("Pop"),
          option("Electro"),
          option("Country")
        ),
        span(style := "margin:0px 5px"),
        button(`class` := "btn btn-default", `type` := "button", onclick := { () =>
          client.foreach {
            _.start()
          }
        })("Start game"),
        span(style := "margin:0px 5px"),
        button(`class` := "btn btn-default", `type` := "button", onclick := { () =>
          stop()
        })("Stop")
      )
    )
  }

  def tracksPanel = div(id := "tracksPanel", `class` := "hide")(
    div(`class` := "container")(
      h3("Your score: 0", id := "score"),
      table(`class` := "table table-hover", id := "tracks")
    )
  )

  def scoresPanel = div(id := "scoresPanel", `class` := "hide")(
    div(`class` := "container")(
      h3("Top scores"),
      table(`class` := "table", id := "scores")
    )
  )

  def addTrack(name: String, index: Int) = {
    tbody(
      tr(raw(name), onclick := { () =>
        onClick(index)
      }, id := "track" + index.toString)
    )
  }

  def addScore(idx: Int, score: UserScore) = {
    tbody(
      tr(idx.toString + ") " + score.user + ": " + score.score.toString)
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
    def connect(url: String, username: String): Option[QuizClient] = {
      if (g.window.WebSocket.toString != "undefined") {
        return Some(new QuizClient(url, username))
      }
      None
    }
  }

  class QuizClient(url: String, myUsername: String) {
    var myAnswer: Int = _
    val socket = new WebSocket(url + myUsername)
    socket.onmessage = onMessage _

    def start(): Unit = {
      send(Message(myUsername, genre, 0, Seq(), Seq()))
    }

    def reply(clicked: Int): Unit = {
      AudioPlayer.stop()
      val correct: Boolean = clicked == myAnswer
      if (correct) {
        $("#track" + clicked).css("background-color", "green")
      } else {
        $("#track" + myAnswer).css("background-color", "green")
        $("#track" + clicked).css("background-color", "red")
      }
      send(Message(myUsername, genre, if (myAnswer == clicked) 1 else -1, Seq(), Seq()))
    }

    def onMessage(event: MessageEvent): Unit = {
      println("received message")
      if (AudioPlayer.isPlaying) {
        return
      }
      val msg = read[Message](event.data.toString)

      msg match {
        case Message(username, genre, answer, tracks, scores) =>
          println("message for user " + username)
          if (tracks.isEmpty && username == myUsername) {
            $("#loginForm").addClass("hide")
            $("#gameForm").removeClass("hide")
            $("#helloUser").text("Hello, " + myUsername)
          }

          if (tracks.nonEmpty) {
            $("#tracksPanel").removeClass("hide")

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
          } else {
            $("#tracksPanel").addClass("hide")
          }

          if (scores.nonEmpty) {
            $("#scoresPanel").removeClass("hide")
            val scoresElem = document.getElementById("scores")
            while (scoresElem.firstChild != null) {
              scoresElem.removeChild(scoresElem.firstChild)
            }
            for (i <- scores.indices) {
              val score = scores.apply(i)
              if (score.user.equals(myUsername)) {
                $("#score").text("Your score = " + score.score.toString)
              }
              scoresElem.appendChild(addScore(i + 1, score).render)
            }
          } else {
            $("#scoresPanel").addClass("hide")
          }
      }
    }

    def genre: String = {
      $("#genre").value().toString.trim
    }

    def stop(): Unit = {
      AudioPlayer.stop()
      $("#loginForm").removeClass("hide")
      $("#gameForm").addClass("hide")
      $("#tracksPanel").addClass("hide")
      $("#scoresPanel").addClass("hide")
      val tracksElem = document.getElementById("tracks")
      while (tracksElem.firstChild != null) {
        tracksElem.removeChild(tracksElem.firstChild)
      }
    }

    def send(msg: Message): Unit = {
      socket.send(write[Message](msg))
    }

    def setAnswer(answer: Int): Unit = {
      this.myAnswer = answer
    }

    def close() = socket.close()
  }

  @JSExport
  def play(settings: js.Dynamic) = {
    this.wsBaseUrl = settings.wsBaseUrl.toString

    val content = document.getElementById("content")
    content.appendChild(startPanel.render)
    content.appendChild(tracksPanel.render)
    content.appendChild(scoresPanel.render)
  }
}