package ss.weekend.domain.ratelimit.strategy

import java.util.concurrent.TimeUnit

import ss.weekend.domain.ratelimit.{RateLimitExceededException, RateLimiter}
import ss.weekend.ratelimitex.UserRequestContext

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration.FiniteDuration

case class SingleRateLimiter(limitPerUnit: Long,
                             timeUnit: TimeUnit) extends RateLimiter {
  implicit val executionContext = ExecutionContext.global

  def getRemainingWaitInSeconds(): FiniteDuration = {
    val period = timeUnit.toSeconds(1)
    FiniteDuration((period - ((System.nanoTime() - _lastNanoTime) / 1e9).toLong), TimeUnit.SECONDS)
  }

  override def tryAcquire[T](permits: Int, request: UserRequestContext)(future: Future[T]): Future[T] = {
    refreshCurrentLimit()
    if (permits > _currentAllowedPermits) {
      Future.failed(RateLimitExceededException(getRemainingWaitInSeconds()))
    } else {
      updateCurrentPermits()
      future
    }
  }
  private var _currentAllowedPermits: Long = limitPerUnit
  private var _lastNanoTime: Long = System.nanoTime()

  private def updateCurrentPermits() = synchronized{_currentAllowedPermits = _currentAllowedPermits - 1}

  private def refreshCurrentLimit() = synchronized {
    val now = System.nanoTime()
    val rateInSeconds = limitPerUnit.toDouble / timeUnit.toSeconds(1)
    val additionalPermits = (now - _lastNanoTime ) * rateInSeconds / 1e9
    if(additionalPermits.toInt > 0) {
      _currentAllowedPermits = Math.min(additionalPermits.toInt + _currentAllowedPermits, limitPerUnit)
      _lastNanoTime = now
    }
  }
}
