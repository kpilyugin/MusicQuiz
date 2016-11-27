package app

import scala.scalajs.js

object AudioPlayer {
  var audio: js.Dynamic = _

  def play(url: String): Unit = {
    if (audio != null) {
      audio.pause()
    } else {
      audio = js.Dynamic.newInstance(js.Dynamic.global.Audio)()
    }
    audio.src = url
    audio.play()
//    audio.onloadedmetadata = () => println(audio.duration)
  }

  def stop(): Unit = {
    if (audio != null) {
      audio.pause()
    }
  }
}
