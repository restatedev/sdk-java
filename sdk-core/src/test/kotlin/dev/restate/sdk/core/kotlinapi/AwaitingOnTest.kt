// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.core.kotlinapi

import dev.restate.sdk.core.ProtocolConversation
import dev.restate.sdk.core.ProtocolConversation.combinatorTypes
import dev.restate.sdk.core.ProtocolConversation.completionIds
import dev.restate.sdk.core.ProtocolConversation.sleepCompletion
import dev.restate.sdk.core.generated.protocol.Protocol
import dev.restate.sdk.core.generated.protocolv7.ProtocolV7
import dev.restate.sdk.core.generated.protocolv7.ProtocolV7.CombinatorType
import dev.restate.sdk.core.legacy.ProtoUtils.inputCmd
import dev.restate.sdk.core.legacy.ProtoUtils.outputCmd
import dev.restate.sdk.core.legacy.ProtoUtils.startMessage
import dev.restate.sdk.endpoint.definition.HandlerDefinition
import dev.restate.sdk.endpoint.definition.HandlerType
import dev.restate.sdk.endpoint.definition.ServiceDefinition
import dev.restate.sdk.endpoint.definition.ServiceType
import dev.restate.sdk.kotlin.*
import dev.restate.serde.kotlinx.*
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Dispatchers
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * The Kotlin combinators report the same AwaitingOn trees as the Java ones, see
 * [dev.restate.sdk.core.AwaitingOnTest].
 */
class AwaitingOnTest {

  private fun v7(body: suspend (Context, Unit) -> String): ProtocolConversation =
      ProtocolConversation.v7(
          ServiceDefinition.of(
              "AwaitingOn",
              ServiceType.SERVICE,
              listOf(
                  HandlerDefinition.of(
                      "run",
                      HandlerType.SHARED,
                      jsonSerde<Unit>(),
                      jsonSerde<String>(),
                      HandlerRunner.of(
                          KotlinSerializationSerdeFactory(),
                          HandlerRunner.Options(Dispatchers.Unconfined),
                          body,
                      ),
                  )
              ),
          ),
          "run",
      )

  @Test
  fun awaitAllReportsTreeAndShrinksAfterPartialProgress() {
    v7 { ctx, _ ->
          listOf(ctx.timer(1.seconds), ctx.timer(2.seconds)).awaitAll()
          "done"
        }
        .use { conv ->
          conv.send(startMessage(1), inputCmd())
          conv.expect(Protocol.SleepCommandMessage::class.java)
          conv.expect(Protocol.SleepCommandMessage::class.java)

          val first = conv.expect(ProtocolV7.AwaitingOnMessage::class.java).awaitingOn
          assertThat(completionIds(first)).containsExactlyInAnyOrder(1, 2)
          assertThat(combinatorTypes(first)).contains(CombinatorType.ALL_SUCCEEDED_OR_FIRST_FAILED)

          conv.send(sleepCompletion(1))
          val second = conv.expect(ProtocolV7.AwaitingOnMessage::class.java).awaitingOn
          assertThat(completionIds(second)).containsExactly(2)

          conv.send(sleepCompletion(2))
          assertThat(conv.expect(Protocol.OutputCommandMessage::class.java))
              .isEqualTo(outputCmd("done"))
          conv.expect(Protocol.EndMessage::class.java)
        }
  }

  @Test
  fun selectReportsFirstCompletedTree() {
    v7 { ctx, _ ->
          val first = ctx.timer(1.seconds)
          val second = ctx.timer(2.seconds)
          select {
                first.onAwait { "first" }
                second.onAwait { "second" }
              }
              .await()
        }
        .use { conv ->
          conv.send(startMessage(1), inputCmd())
          conv.expect(Protocol.SleepCommandMessage::class.java)
          conv.expect(Protocol.SleepCommandMessage::class.java)

          val tree = conv.expect(ProtocolV7.AwaitingOnMessage::class.java).awaitingOn
          assertThat(completionIds(tree)).containsExactlyInAnyOrder(1, 2)
          assertThat(combinatorTypes(tree))
              .contains(CombinatorType.FIRST_COMPLETED)
              .doesNotContain(CombinatorType.ALL_SUCCEEDED_OR_FIRST_FAILED)

          conv.send(sleepCompletion(2))
          assertThat(conv.expect(Protocol.OutputCommandMessage::class.java))
              .isEqualTo(outputCmd("second"))
          conv.expect(Protocol.EndMessage::class.java)
        }
  }
}
