package ss.weekend.domain.ratelimit.strategy

import java.util.UUID
import java.util.concurrent.{ConcurrentHashMap, TimeUnit}

import ss.weekend.domain.ratelimit.{RateLimitInvalidUserException, RateLimitExceededException, RateLimiter}
import ss.weekend.domain.userQuota.QuotaAllocation
import ss.weekend.ratelimitex.UserRequestContext

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future}

/**
 * TokenBucketRateLimiter maintains concurrent hash to maintain the token.
 * It is not suitable for distributed system as it maintains local concurrent map
 * that holds user permit tokens
 */
class TokenBucketRateLimiter extends RateLimiter {
  implicit val executionContext = ExecutionContext.global

  type UserID = String
  type PermitsAvailable = Long
  type LastUpdated = Long
  val bucket = new ConcurrentHashMap[UserID, (PermitsAvailable, LastUpdated)]

  private def getRemainingWaitInSeconds(userId: UUID, lastAccessed: LastUpdated): FiniteDuration = {
    val period = QuotaAllocation.cache.getIfPresent(userId.toString).map(_._2)
      .getOrElse(TimeUnit.SECONDS).toSeconds(1)
    FiniteDuration((period - ((System.nanoTime() - lastAccessed) / 1e9).toLong), TimeUnit.SECONDS)
  }

  private def updateCurrentPermits(userId: UUID, permitsAvailable: PermitsAvailable, lastUpdated: LastUpdated) =
    bucket.put(userId.toString, (permitsAvailable, lastUpdated))

  override def tryAcquire[T](permits: Int, request: UserRequestContext)(future: Future[T]): Future[T] = {
    //TODO move to validate class
    if(permits < 1) {
      return Future.failed(new IllegalArgumentException)
    }
    synchronized {
      val (permitsAvailable, lastUpdated) = getCurrentLimit(request)
      if (permits > permitsAvailable) {
        Future.failed(RateLimitExceededException(getRemainingWaitInSeconds(request.userId, lastUpdated)))
      } else {
        updateCurrentPermits(request.userId, permitsAvailable - permits, lastUpdated)
        future
      }
    }
  }


  private def getCurrentLimit(request: UserRequestContext): (PermitsAvailable, LastUpdated) = {
    val now = System.nanoTime()
    val userId = request.userId.toString
    val (limitPerUnit, timeUnit) = QuotaAllocation.cache.getIfPresent(userId).getOrElse(Long.MinValue, TimeUnit.SECONDS)
    if(limitPerUnit == Long.MinValue) throw RateLimitInvalidUserException()

    val (permitsAvailable, lastUpdated) = bucket.getOrDefault(userId, (limitPerUnit, now))
    val rateInSeconds = limitPerUnit.toDouble / timeUnit.toSeconds(1)
    val additionalPermits = (now - lastUpdated ) * rateInSeconds / 1e9
    (Math.min(additionalPermits.toInt + permitsAvailable, limitPerUnit),
      if(additionalPermits.toInt > 0) now else lastUpdated)
  }
}
