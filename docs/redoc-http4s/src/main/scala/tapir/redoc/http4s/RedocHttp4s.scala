package tapir.redoc.http4s

import cats.effect.{ContextShift, Sync}
import org.http4s.dsl.Http4sDsl
import org.http4s.headers._
import org.http4s.{Charset, HttpRoutes, MediaType}

import scala.io.Source

/**
  * Usage: add `new RedocHttp4s(title, yaml).routes[F]` to your http4s router. For example:
  * `Router("/docs" -> new RedocHttp4s(yaml).routes[IO])`.
  *
  * @param title       The title of the HTML page.
  * @param yaml        The yaml with the OpenAPI documentation.
  * @param yamlName    The name of the file, through which the yaml documentation will be served. Defaults to `docs.yaml`.
  */
class RedocHttp4s(title: String, yaml: String, yamlName: String = "docs.yaml") {
  private lazy val html = {
    val fileName = "redoc.html"
    val is = getClass.getClassLoader.getResourceAsStream(fileName)
    assert(Option(is).nonEmpty, s"Could not find file ${fileName} on classpath.")
    val rawHtml = Source.fromInputStream(is).mkString
    // very poor man's templating engine
    rawHtml.replaceAllLiterally("{{docsPath}}", yamlName).replaceAllLiterally("{{title}}", title)
  }

  def routes[F[_]: ContextShift: Sync]: HttpRoutes[F] = {
    val dsl = Http4sDsl[F]
    import dsl._

    HttpRoutes.of[F] {
      case req @ GET -> Root if req.pathInfo.endsWith("/") =>
        Ok(html, `Content-Type`(MediaType.text.html, Charset.`UTF-8`))
      // as the url to the yaml file is relative, it is important that there is a trailing slash
      case req @ GET -> Root =>
        val uri = req.uri
        PermanentRedirect(Location(uri.withPath(uri.path.concat("/"))))
      case GET -> Root / `yamlName` =>
        Ok(yaml, `Content-Type`(MediaType.text.yaml, Charset.`UTF-8`))
    }
  }
}
