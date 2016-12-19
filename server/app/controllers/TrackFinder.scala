package controllers

import music.Track
import play.api.libs.json.{JsArray, JsValue}
import play.api.libs.ws.{WSClient, WSRequest}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Random

object TrackFinder {
  def findTracks(wsClient: WSClient, genre: String)(implicit ec: ExecutionContext): Future[Seq[Track]] = {
    val offset: Int = scala.util.Random.nextInt(10) * 20
    val url = "https://api.spotify.com/v1/search"
    val requestHolder: WSRequest = wsClient.url(url)
        .withQueryString(
          "q" -> ("genre:" + genre),
          "type" -> "track",
          "limit" -> "20",
          "offset" -> offset.toString)
    requestHolder
        .get().map {
      response => {
        val items: Seq[JsValue] = (response.json \ "tracks" \ "items").as[JsArray].value
        var tracks: Seq[Track] = Seq()
        for (item <- items) {
          val name: String = (item \ "name").as[String]
          val artist: String = ((item \ "artists").as[JsArray].value.head \ "name").as[String]
          val previewUrl: String = (item \ "preview_url").as[String]
          val popularity: Int = (item \ "popularity").as[Int]
          val track: Track = Track(name, artist, previewUrl, popularity)
          tracks = tracks :+ track
        }
        quiz(tracks)
      }
    }
  }

  def quiz(allTracks: Seq[Track]): Seq[Track] = {
    val shuffled: Seq[Track] = new Random().shuffle(allTracks)
    shuffled.slice(0, 5)
  }
}
