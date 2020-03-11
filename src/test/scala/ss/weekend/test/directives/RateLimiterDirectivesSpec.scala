package ss.weekend.test.directives

import java.util.UUID

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server._
import akka.http.scaladsl.testkit.ScalatestRouteTest
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import ss.weekend.domain.ratelimit.{RateLimitExceededException, RateLimitInvalidUserException, RateLimiter}
import ss.weekend.ratelimitex.{RateLimiterDirectives, UserRequestContext}

import scala.concurrent.Future

class RateLimiterDirectivesSpec extends AnyFlatSpec with RateLimiterDirectives with Matchers with ScalatestRouteTest {

  val invalidUser = UserRequestContext(UUID.fromString("00000000-0000-0000-0000-000000000000"))
  val validUser = UserRequestContext(UUID.fromString("f6dd6a1a-28a1-4918-833e-60f3e2605bbc"))

  val expectedResult = "OK!!"
  def future = Future(expectedResult)

  "Rate Limiter Directives" should "complete request without failure" in {
    def testRoute: Route = get {
      path("user" / Segment) { uuid =>
        onCompleteWithRateLimiter(
          new MockRateLimiter(1),
          UserRequestContext(UUID.fromString(uuid))
        )(future)(x => complete(x))
      }
    }

    Get(s"/user/${validUser.userId.toString}") ~> testRoute ~> check {
      status should ===(StatusCodes.OK)
      responseAs[String] shouldEqual expectedResult
    }
  }

    it should "return TooManyReuqest(429) code if rate limit exceeded" in {
      def testRoute: Route = get {
        path("user" / Segment) { uuid =>
          onCompleteWithRateLimiter(
            new MockRateLimiter(0),
            UserRequestContext(UUID.fromString(uuid))
          )(future)(x => complete(x))
        }
      }

      Get(s"/user/${validUser.userId.toString}") ~> testRoute ~> check {
        status should ===(StatusCodes.TooManyRequests)
      }
    }

    it should "return BadRequest(400) code for invalid tryAcquire parameters" in {
      def testRoute: Route = get {
        path("user" / Segment) { uuid =>
          onCompleteWithRateLimiter(
            new MockRateLimiter(0),
            UserRequestContext(UUID.fromString(uuid))
          )(future)(x => complete(x))
        }
      }
      Get(s"/user/${invalidUser.userId.toString}") ~> testRoute ~> check {
        status should ===(StatusCodes.BadRequest)
      }
    }

}

class MockRateLimiter(limits: Int) extends RateLimiter {
  override def tryAcquire[T](permits: Int, request: UserRequestContext)(future: Future[T]): Future[T] = {
    if(request.userId.toString == "00000000-0000-0000-0000-000000000000") {
      Future.failed(RateLimitInvalidUserException())
    } else if(permits > limits){
      import scala.concurrent.duration._
      Future.failed(RateLimitExceededException(20.seconds))
    } else {
      future
    }
  }
}
