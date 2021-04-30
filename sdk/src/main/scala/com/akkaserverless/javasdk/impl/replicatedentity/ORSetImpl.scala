/*
 * Copyright 2019 Lightbend Inc.
 */

package com.akkaserverless.javasdk.impl.replicatedentity

import com.akkaserverless.javasdk.replicatedentity.ORSet
import com.akkaserverless.javasdk.impl.AnySupport
import com.akkaserverless.protocol.replicated_entity.{ORSetDelta, ReplicatedEntityDelta}
import com.google.protobuf.any.{Any => ScalaPbAny}

import java.util
import scala.jdk.CollectionConverters._

private[replicatedentity] class ORSetImpl[T](anySupport: AnySupport)
    extends util.AbstractSet[T]
    with InternalReplicatedData
    with ORSet[T] {
  override final val name = "ORSet"
  private val value = new util.HashSet[T]()
  private val added = new util.HashSet[ScalaPbAny]()
  private val removed = new util.HashSet[ScalaPbAny]()
  private var cleared = false

  override def size(): Int = value.size()

  override def isEmpty: Boolean = super.isEmpty

  override def contains(o: Any): Boolean = value.contains(o)

  override def add(e: T): Boolean =
    if (value.contains(e)) {
      false
    } else {
      val encoded = anySupport.encodeScala(e)
      if (removed.contains(encoded)) {
        removed.remove(encoded)
      } else {
        added.add(anySupport.encodeScala(e))
      }
      value.add(e)
    }

  override def remove(o: Any): Boolean =
    if (!value.contains(o)) {
      false
    } else {
      value.remove(o)
      if (value.isEmpty) {
        clear()
      } else {
        val encoded = anySupport.encodeScala(o)
        if (added.contains(encoded)) {
          added.remove(encoded)
        } else {
          removed.add(encoded)
        }
      }
      true
    }

  override def iterator(): util.Iterator[T] = new util.Iterator[T] {
    private val iter = value.iterator()
    private var lastNext: T = _

    override def hasNext: Boolean = iter.hasNext

    override def next(): T = {
      lastNext = iter.next()
      lastNext
    }

    override def remove(): Unit = {
      iter.remove()
      val encoded = anySupport.encodeScala(lastNext)
      if (added.contains(encoded)) {
        added.remove(encoded)
      } else {
        removed.add(encoded)
      }
    }
  }

  override def clear(): Unit = {
    value.clear()
    cleared = true
    removed.clear()
    added.clear()
  }

  override def hasDelta: Boolean = cleared || !added.isEmpty || !removed.isEmpty

  override def delta: ReplicatedEntityDelta.Delta =
    ReplicatedEntityDelta.Delta.Orset(
      ORSetDelta(cleared, removed = removed.asScala.toVector, added = added.asScala.toVector)
    )

  override def resetDelta(): Unit = {
    cleared = false
    added.clear()
    removed.clear()
  }

  override val applyDelta = {
    case ReplicatedEntityDelta.Delta.Orset(ORSetDelta(cleared, removed, added, _)) =>
      if (cleared) {
        value.clear()
      }
      value.removeAll(removed.map(e => anySupport.decode(e).asInstanceOf[T]).asJava)
      value.addAll(added.map(e => anySupport.decode(e).asInstanceOf[T]).asJava)
  }

  override def toString = s"ORSet(${value.asScala.mkString(",")})"
}
