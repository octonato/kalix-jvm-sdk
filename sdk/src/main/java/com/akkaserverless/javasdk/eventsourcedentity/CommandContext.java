/*
 * Copyright 2021 Lightbend Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.akkaserverless.javasdk.eventsourcedentity;

import com.akkaserverless.javasdk.ClientActionContext;
import com.akkaserverless.javasdk.EffectContext;
import com.akkaserverless.javasdk.MetadataContext;

/**
 * An event sourced command context.
 *
 * <p>Methods annotated with {@link CommandHandler} may take this is a parameter. It allows emitting
 * new events in response to a command, along with forwarding the result to other entities, and
 * performing side effects on other entities.
 */
public interface CommandContext
    extends EventSourcedContext, ClientActionContext, EffectContext, MetadataContext {
  /**
   * The current sequence number of events in this entity.
   *
   * @return The current sequence number.
   */
  long sequenceNumber();

  /**
   * The name of the command being executed.
   *
   * @return The name of the command.
   */
  String commandName();

  /**
   * The id of the command being executed.
   *
   * @return The id of the command.
   */
  long commandId();

  /**
   * Emit the given event. The event will be persisted, and the handler of the event defined in the
   * current behavior will immediately be executed to pick it up.
   *
   * @param event The event to emit.
   */
  void emit(Object event);
}
