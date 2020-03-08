package ss.weekend.domain.userQuota

import java.util.UUID

import com.github.blemale.scaffeine.{Cache, Scaffeine}

trait UserQuotaService {
  def fetchAllUserQuota(): Vector[UserQuota]
  def fetchUserQuota(userId: UUID): Option[UserQuota]
}

trait DefaultQuotaService extends UserQuotaService {
  def repository: UserQuotaRepository
  override def fetchAllUserQuota(): Vector[UserQuota] = {
    val usersQuota = repository.fetchAllUserQuotaRaw()
    usersQuota.map { record =>
      QuotaAllocation.cache.put(record.userId.toString, (record.limitPerUnit, record.timeUnit))}
    usersQuota
  }

  override def fetchUserQuota(userId: UUID): Option[UserQuota] = {
    QuotaAllocation.cache.getIfPresent(userId.toString).map {
      r => UserQuota(userId, r._1, r._2)}
  }

}

object QuotaAllocation {
  import scala.concurrent.duration._
  type Identifier = String
  type Limits = Long
  val cache: Cache[Identifier, (Limits, TimeUnit)] =
    Scaffeine()
      .maximumSize(300000)
      .expireAfterWrite(1.day)
      .build[Identifier, (Limits, TimeUnit)]()

}
