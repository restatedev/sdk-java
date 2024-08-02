// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.testservices

import dev.restate.sdk.kotlin.Context
import dev.restate.sdk.kotlin.awakeable
import dev.restate.sdktesting.contracts.AwakeableHolderClient
import dev.restate.sdktesting.contracts.ListObjectClient
import dev.restate.sdktesting.contracts.UpgradeTest
import java.util.*

class UpgradeTestImpl : UpgradeTest {
  // Value should be either "v1" or "v2"
  private fun version(): String {
    return Objects.requireNonNull(System.getenv("E2E_UPGRADETEST_VERSION")).trim { it <= ' ' }
  }

  override suspend fun executeSimple(context: Context): String {
    return version()
  }

  override suspend fun executeComplex(context: Context): String {
    check("v1" == version()) {
      "executeComplex should not be invoked with version different from 1!"
    }

    // In v1 case we create an awakeable, we ask the AwakeableHolderService to hold it, and then we
    // await on it
    val awakeable = context.awakeable<String>()
    AwakeableHolderClient.fromContext(context, "upgrade").send().hold(awakeable.id)
    awakeable.await()

    // Store the result in List service, because this service is invoked with
    // dev.restate.Ingress#Invoke
    ListObjectClient.fromContext(context, "upgrade-test").send().append(version())

    return version()
  }
}
