package ss.weekend.test.domain.ratelimiter

import java.util.UUID

import org.specs2.matcher.ResultMatchers
import org.specs2.mutable.Specification
import org.specs2.specification.core.Env
import ss.weekend.domain.ratelimit.RateLimitExceededException
import ss.weekend.domain.ratelimit.strategy.TokenBucketRateLimiter
import ss.weekend.domain.userQuota.{DefaultQuotaService, DefaultUserQuotaRepository, UserQuotaRepository}
import ss.weekend.ratelimitex.UserRequestContext

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}

class TokenBucketRateLimiterSpec extends Specification with ResultMatchers {

  implicit val executionContext = Env().executionContext
  val rateLimiter = new TokenBucketRateLimiter()
  val userRequest = UserRequestContext(UUID.fromString( "beaaed71-f099-4bcd-88d3-62ee36576c04"))
  "TokenBucketRateLimiter" should {
    //init cache - all User Quota
    val useQuotaRepo = new DefaultUserQuotaRepository {}
    val useQuotaService = new DefaultQuotaService {
      override def repository: UserQuotaRepository = useQuotaRepo
    }
    useQuotaService.fetchAllUserQuota()

    "able to serve request if within quota" in {
      val someMessage = "Request served successfully!"
      val someWork = Future(someMessage)
      val result = Await.result(rateLimiter.tryAcquire(1, userRequest)(someWork), Duration.Inf)
      result === someMessage
    }

    //rate limit exceeded
    "throw rate limit exceeded exception on 3rd task" in {
      rateLimiter.tryAcquire(1, userRequest)(Future("Task 1"))
      rateLimiter.tryAcquire(1, userRequest)(Future("Task 2"))
      val result = rateLimiter.tryAcquire(1, userRequest)(Future("Task 3"))
      Await.result(result, Duration.Inf) must throwA[RateLimitExceededException]

    }

    "rate limit fulfilled after 1 second" in {
      val user = UserRequestContext(UUID.fromString("224fe409-d775-4f26-8b9f-e2f15cab1a74"))
      rateLimiter.tryAcquire(1, user)(Future("Task 1"))
      rateLimiter.tryAcquire(1, user)(Future("Task 2"))
      val result = rateLimiter.tryAcquire(1, user)(Future("Task 3"))
      Await.result(result, Duration.Inf) must throwA[RateLimitExceededException]
      Thread.sleep(1000)
      val result2 = rateLimiter.tryAcquire(1, user)(Future("Task 4"))
      Await.result(result2, Duration.Inf) === "Task 4"
    }
  }




}
