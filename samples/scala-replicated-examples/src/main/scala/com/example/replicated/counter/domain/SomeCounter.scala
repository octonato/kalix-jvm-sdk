package com.example.replicated.counter.domain

import com.akkaserverless.scalasdk.replicatedentity.ReplicatedCounter
import com.akkaserverless.scalasdk.replicatedentity.ReplicatedEntity
import com.akkaserverless.scalasdk.replicatedentity.ReplicatedEntityContext
import com.example.replicated.counter
import com.google.protobuf.empty.Empty

// This class was initially generated based on the .proto definition by Akka Serverless tooling.
//
// As long as this file exists it will not be overwritten: you can maintain it yourself,
// or delete it so it is regenerated as needed.

/** A replicated entity. */
class SomeCounter(context: ReplicatedEntityContext) extends AbstractSomeCounter {


  /** Command handler for "Increase". */
  def increase(currentData: ReplicatedCounter, increaseValue: counter.IncreaseValue): ReplicatedEntity.Effect[Empty] =
    effects.error("The command handler for `Increase` is not implemented, yet")

  /** Command handler for "Decrease". */
  def decrease(currentData: ReplicatedCounter, decreaseValue: counter.DecreaseValue): ReplicatedEntity.Effect[Empty] =
    effects.error("The command handler for `Decrease` is not implemented, yet")

  /** Command handler for "Get". */
  def get(currentData: ReplicatedCounter, getValue: counter.GetValue): ReplicatedEntity.Effect[counter.CurrentValue] =
    effects.error("The command handler for `Get` is not implemented, yet")

}