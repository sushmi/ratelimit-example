package ss.weekend.test.domain.ratelimiter

import java.util.UUID
import java.util.concurrent.atomic.{AtomicBoolean, AtomicInteger}
import java.util.concurrent.{Callable, Executor, Executors, TimeUnit}

import akka.Done
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers._
import ss.weekend.domain.ratelimit.strategy.TokenBucketRateLimiter
import ss.weekend.domain.ratelimit.{RateLimitExceededException, RateLimitInvalidUserException}
import ss.weekend.domain.userQuota.{DefaultQuotaService, UserQuota, UserQuotaRepository}
import ss.weekend.ratelimitex.UserRequestContext

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

class TokenBucketRateLimiterSpec extends AnyFlatSpec {

  implicit val executionContext = ExecutionContext.global
  val rateLimiter = new TokenBucketRateLimiter()
  val userRequest = UserRequestContext(UUID.fromString("beaaed71-f099-4bcd-88d3-62ee36576c04"))
  //init cache - all User Quota
  val useQuotaService = new MockUserQuotaService()
  useQuotaService.fetchAllUserQuota()

  "TokenBucketRateLimiter" should "able to serve request if within quota" in {
    val someMessage = "Request served successfully!"
    val someWork = Future(someMessage)
    val result = Await.result(rateLimiter.tryAcquire(1, userRequest)(someWork), Duration.Inf)
    result === someMessage
  }

  it should "throw rate limit exceeded exception on 3rd task" in {
    rateLimiter.tryAcquire(1, userRequest)(Future("Task 1"))
    rateLimiter.tryAcquire(1, userRequest)(Future("Task 2"))
    val result = rateLimiter.tryAcquire(1, userRequest)(Future("Task 3"))
    the[RateLimitExceededException] thrownBy Await.result(result, Duration.Inf)
  }

  it should "rate limit fulfilled after 1 second" in {
    val user = UserRequestContext(UUID.fromString("224fe409-d775-4f26-8b9f-e2f15cab1a74"))
    rateLimiter.tryAcquire(1, user)(Future("Task 1"))
    rateLimiter.tryAcquire(1, user)(Future("Task 2"))
    val result = rateLimiter.tryAcquire(1, user)(Future("Task 3"))
    the[RateLimitExceededException] thrownBy Await.result(result, Duration.Inf)
    Thread.sleep(1000)
    val result2 = rateLimiter.tryAcquire(1, user)(Future("Task 4"))
    Await.result(result2, Duration.Inf) === "Task 4"
  }

  it should "fail to acquire for user missing quota" in {
    val user = UserRequestContext(UUID.fromString("f6dd6a1a-28a1-4918-833e-60f3e2605bbc"))
    val thrown = the[RateLimitInvalidUserException] thrownBy {
      rateLimiter.tryAcquire(1, user)(Future("Task 1"))
    }
    thrown.getMessage shouldEqual "Contact administrator. You have no quota assigned."
  }

  it should "fail to acquire invalid permits" in {
    val user = UserRequestContext(UUID.fromString("224fe409-d775-4f26-8b9f-e2f15cab1a74"))
    val result = rateLimiter.tryAcquire(-1, user)(Future("Task 1"))
    the[IllegalArgumentException] thrownBy Await.result(result, Duration.Inf)

    val result0 = rateLimiter.tryAcquire(0, user)(Future("Task 1"))
    the[IllegalArgumentException] thrownBy Await.result(result0, Duration.Inf)
  }

  def testScenario(user: UserRequestContext) = {
    val e = Executors.newFixedThreadPool(5)
    val tasks = for(i <- 0 until 1000) yield {
      e.submit(new Callable[Unit]() {
        override def call() = {
          Await.result(rateLimiter.tryAcquire(1, user)(Future("Task 1")), Duration.Inf)
        }
      })
    }
    e.shutdown()

    var countSuccess = 0
    var countFailure = 0
    for(result <- tasks) yield {
      Try(result.get()) match {
        case Success(_) => countSuccess = countSuccess + 1
        case _ => countFailure = countFailure + 1
      }
    }

    (countSuccess, countFailure)
  }

  it should "allow only defined permits when accessed concurrently" in {
    val user = UserRequestContext(UUID.fromString("3eaaed71-f099-4bcd-88d3-62ee36576c04"))
    val (successCount, failureCount) = testScenario(user)
    successCount shouldEqual 200
    failureCount shouldEqual 800
  }

}

class MockUserQuotaService extends DefaultQuotaService {
  override def repository: UserQuotaRepository = new UserQuotaRepository {
    override def fetchAllUserQuotaRaw(): Vector[UserQuota] = {
      Vector(
        UserQuota(UUID.fromString("224fe409-d775-4f26-8b9f-e2f15cab1a74"), 2, TimeUnit.SECONDS),
        UserQuota(UUID.fromString("beaaed71-f099-4bcd-88d3-62ee36576c04"), 2, TimeUnit.MINUTES),
        UserQuota(UUID.fromString("3eaaed71-f099-4bcd-88d3-62ee36576c04"), 200, TimeUnit.MINUTES)
      )
    }
  }
}

