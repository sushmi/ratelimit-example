package ss.weekend.domain.userQuota

import java.util.UUID
import java.util.concurrent.TimeUnit

trait UserQuotaRepository {
  def fetchAllUserQuotaRaw(): Vector[UserQuota]
}

trait DefaultUserQuotaRepository extends UserQuotaRepository {
  def fetchAllUserQuotaRaw(): Vector[UserQuota] = {
    //TODO : mock result from datastore
    Vector(
      UserQuota(UUID.fromString("f6dd6a1a-28a1-4918-833e-60f3e2605bbc"), 100, TimeUnit.HOURS),
      UserQuota(UUID.fromString("1c58bcc9-bffe-47a3-ab7e-16aca5b7c78e"), 10, TimeUnit.HOURS),
      UserQuota(UUID.fromString("34644bb7-074e-4543-80f8-7dfc2ea07825"), 20, TimeUnit.HOURS),
      UserQuota(UUID.fromString("bcc591ea-dfc4-4a66-a3fc-5520dab08651"), 30, TimeUnit.HOURS),
      UserQuota(UUID.fromString("a97d2a8b-b92d-4bcd-9cce-b9cc7da1e23c"), 40, TimeUnit.HOURS),
      UserQuota(UUID.fromString("6836c6ae-39c7-4415-8998-d13fda8c6152"), 50, TimeUnit.HOURS),
      UserQuota(UUID.fromString("72c4a65d-9ef2-47fa-927a-4d2240eb8e2f"), 60, TimeUnit.HOURS),
      UserQuota(UUID.fromString("224fe409-d775-4f26-8b9f-e2f15cab1a74"), 2, TimeUnit.SECONDS),
      UserQuota(UUID.fromString("beaaed71-f099-4bcd-88d3-62ee36576c04"), 2, TimeUnit.MINUTES),
      UserQuota(UUID.fromString("ec86c561-2eee-46e2-9128-d3bcd1b89871"), 0, TimeUnit.SECONDS)
    )
  }
}
