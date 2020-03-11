package ss.weekend.domain.ratelimit

import ss.weekend.ratelimitex.UserRequestContext

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

trait RateLimiter {
  def tryAcquire[T](permits: Int, request: UserRequestContext)(future: Future[T]): Future[T]
}

trait RateLimitException
case class RateLimitExceededException(remainingDuration: FiniteDuration,
                           message: String = "Rate limit exceeded.")
  extends Exception(s"$message Try again in $remainingDuration.") with RateLimitException

case class RateLimitInvalidUserException(message: String = "Contact administrator. You have no quota assigned.")
  extends Exception(message) with RateLimitException

