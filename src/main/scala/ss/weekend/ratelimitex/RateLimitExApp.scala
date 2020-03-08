package ss.weekend.ratelimitex

import java.util.UUID

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.server.{HttpApp, Route}
import akka.stream.ActorMaterializer
import spray.json.DefaultJsonProtocol._
import spray.json.{JsString, JsValue, RootJsonFormat}
import ss.weekend.domain.ratelimit.strategy.TokenBucketRateLimiter
import ss.weekend.domain.userQuota.{DefaultQuotaService, DefaultUserQuotaRepository, UserQuotaRepository}

import scala.concurrent.Future
import scala.io.StdIn

class Context
final case class UserRequestContext(userId: UUID) extends Context

object RateLimitExApp extends HttpApp with App with RateLimiterDirectives {
  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()

  lazy val systemRoute: Route = get {
    path("ping") {
      complete("pong")
    }
  }

  //init cache - all User Quota
  val useQuotaRepo = new DefaultUserQuotaRepository {}
  val useQuotaService = new DefaultQuotaService {
    override def repository: UserQuotaRepository = useQuotaRepo
  }
  useQuotaService.fetchAllUserQuota()

  implicit object UuidJsonFormat extends RootJsonFormat[UUID] {
    def write(x: UUID) = JsString(x.toString)
    def read(value: JsValue) = value match {
      case JsString(x) => UUID.fromString(x)
      case x           => throw new Exception("Expected UUID as JsString, but got " + x)
    }
  }

  // formats for unmarshalling and marshalling
  implicit val userRequest = jsonFormat1(UserRequestContext)

  def serveRequest(requestContext: UserRequestContext) =
    Future(s"Hi ${requestContext.userId} !!")

  val defaultRateLimiter = new TokenBucketRateLimiter
  lazy val userRoute: Route = post {
    path("anything") {
        entity(as[UserRequestContext]) { implicit request => {
          onCompleteWithRateLimiter(defaultRateLimiter, request)(serveRequest(request)) {
            x => complete(x)
          }
        }
      }
    }
  }

  val bindingFuture = Http().bindAndHandle(routes, "localhost", 8080)

  println(s"Server online at http://localhost:8080/\nPress RETURN to stop...")
  StdIn.readLine() // let it run until user presses return
  bindingFuture
    .flatMap(_.unbind()) // trigger unbinding from the port
    .onComplete(_ => system.terminate()) // and shutdown when done

  override protected def routes = systemRoute ~ userRoute
}
