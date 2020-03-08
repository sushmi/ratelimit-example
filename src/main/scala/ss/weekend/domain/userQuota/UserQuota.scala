package ss.weekend.domain.userQuota

import java.util.UUID

import scala.concurrent.duration.TimeUnit

case class UserQuota(userId: UUID, limitPerUnit: Long, timeUnit: TimeUnit)
