package tapir.examples

import java.io.{File, PrintWriter}

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Route
import akka.stream.ActorMaterializer
import sttp.client._
import sttp.model.Part
import tapir._
import tapir.server.akkahttp._

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

object MultipartFormUploadAkkaServer extends App {
  // the class representing the multipart data
  //
  // parts can be referenced directly; if part metadata is needed, we define the type wrapped with Part[_].
  //
  // note that for binary parts need to be buffered either in-memory or in the filesystem anyway (the whole request
  // has to be read to find out what are the parts), so handling multipart requests in a purely streaming fashion is
  // not possible
  case class UserProfile(name: String, hobby: Option[String], age: Int, photo: Part[File])

  // corresponds to: POST /user/profile [multipart form data with fields name, hobby, age, photo]
  val setProfile: Endpoint[UserProfile, Unit, String, Nothing] =
    endpoint.post.in("user" / "profile").in(multipartBody[UserProfile]).out(stringBody)

  // converting an endpoint to a route (providing server-side logic); extension method comes from imported packages
  val setProfileRoute: Route = setProfile.toRoute { data =>
    Future {
      val response = s"Received: ${data.name} / ${data.hobby} / ${data.age} / ${data.photo.fileName} (${data.photo.body.length()})"
      data.photo.body.delete()
      Right(response)
    }
  }

  // starting the server
  implicit val actorSystem: ActorSystem = ActorSystem()
  import actorSystem.dispatcher
  implicit val materializer: ActorMaterializer = ActorMaterializer()
  val bindAndCheck = Http().bindAndHandle(setProfileRoute, "localhost", 8080).map { _ =>
    val testFile = File.createTempFile("user-123", ".jpg")
    val pw = new PrintWriter(testFile); pw.write("This is not a photo"); pw.close()

    // testing
    implicit val backend: SttpBackend[Identity, Nothing, NothingT] = HttpURLConnectionBackend()
    val result: String = basicRequest
      .response(asStringAlways)
      .get(uri"http://localhost:8080/user/profile")
      .multipartBody(multipart("name", "Frodo"), multipart("hobby", "hiking"), multipart("age", "33"), multipartFile("photo", testFile))
      .send()
      .body
    println("Got result: " + result)

    assert(result == s"Received: Frodo / Some(hiking) / 33 / Some(${testFile.getName}) (19)")
  }

  Await.result(bindAndCheck.transformWith { r =>
    actorSystem.terminate().transform(_ => r)
  }, 1.minute)
}
