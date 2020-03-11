package ss.weekend.ratelimitex

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directive1
import akka.http.scaladsl.server.Directives.{extractRequestContext, provide}
import akka.http.scaladsl.server.directives.FutureDirectives
import ss.weekend.domain.ratelimit.{RateLimitExceededException, RateLimitException, RateLimitInvalidUserException, RateLimiter}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Try}

trait RateLimiterDirectives extends FutureDirectives {
  import akka.http.scaladsl.server.directives.RouteDirectives._

  implicit val executionContext: ExecutionContext = ExecutionContext.global

  /**
   * OnCompleteWithRateLimiter directive throws
   * HTTP Error Code 429 on RateLimitExceededException.
   * HTTP Error Code 400 on RateLimitInvalidUserException.
   * @param limiter
   * @param request
   * @param future
   * @tparam T
   * @return
   */
  def onCompleteWithRateLimiter[T](limiter: RateLimiter, request: UserRequestContext)(
    future: => Future[T]): Directive1[Try[T]] =
    onComplete(limiter.tryAcquire(1, request)(future)) flatMap {
      case Failure(ex: RateLimitExceededException) =>
        extractRequestContext.flatMap { ctx =>
          complete(StatusCodes.TooManyRequests, ex.getMessage)
        }
      case Failure(ex: RateLimitInvalidUserException) =>
        extractRequestContext.flatMap { ctx =>
          complete(StatusCodes.BadRequest, ex.getMessage)
        }
      case x => provide(x)
    }

}
