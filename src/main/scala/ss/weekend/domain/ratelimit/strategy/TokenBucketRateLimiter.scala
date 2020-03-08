package ss.weekend.domain.ratelimit.strategy

import java.util.UUID
import java.util.concurrent.{ConcurrentHashMap, TimeUnit}

import ss.weekend.domain.ratelimit.{RateLimitExceededException, RateLimiter}
import ss.weekend.domain.userQuota.{QuotaAllocation, UserQuotaService}
import ss.weekend.ratelimitex.UserRequestContext

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future}

class TokenBucketRateLimiter extends RateLimiter {
  implicit val executionContext = ExecutionContext.global

  type UserID = String
  type PermitsAvailable = Long
  type LastUpdated = Long
  val bucket = new ConcurrentHashMap[UserID, (PermitsAvailable, LastUpdated)]

  private def getRemainingWaitInSeconds(userId: UUID, lastAccessed: LastUpdated): FiniteDuration = {
    //TODO: fix when user not present in cache
    val period = QuotaAllocation.cache.getIfPresent(userId.toString).map(_._2)
      .getOrElse(TimeUnit.SECONDS).toSeconds(1)
    FiniteDuration((period - ((System.nanoTime() - lastAccessed) / 1e9).toLong), TimeUnit.SECONDS)
  }

  override def tryAcquire[T](permits: Int, request: UserRequestContext)(future: Future[T]): Future[T] = {
    val (permitsAvailable, lastUpdated) = getCurrentLimit(request)
    if (permits > permitsAvailable) {
      Future.failed(RateLimitExceededException(getRemainingWaitInSeconds(request.userId, lastUpdated)))
    } else {
      updateCurrentPermits(request.userId, permitsAvailable - permits, lastUpdated)
      future
    }
  }

  private def updateCurrentPermits(userId: UUID, permitsAvailable: PermitsAvailable, lastUpdated: LastUpdated) =
    bucket.put(userId.toString, (permitsAvailable, lastUpdated))

  private def getCurrentLimit(request: UserRequestContext): (PermitsAvailable, LastUpdated) = {
    val now = System.nanoTime()
    val userId = request.userId.toString
    //TODO: maybe not 0 as default, 0 doesn't mean user is blocked or has no limit defined yet
    val (limitPerUnit, timeUnit) = QuotaAllocation.cache.getIfPresent(userId).getOrElse(0L, TimeUnit.SECONDS)
    val (permitsAvailable, lastUpdated) = bucket.getOrDefault(userId, (limitPerUnit, now))

    val rateInSeconds = limitPerUnit.toDouble / timeUnit.toSeconds(1)
    val additionalPermits = (now - lastUpdated ) * rateInSeconds / 1e9
    (Math.min(additionalPermits.toInt + permitsAvailable, limitPerUnit), if(additionalPermits.toInt > 0) now else lastUpdated)
  }
}

