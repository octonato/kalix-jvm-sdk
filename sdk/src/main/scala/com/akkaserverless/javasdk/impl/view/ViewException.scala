/*
 * Copyright 2019 Lightbend Inc.
 */

package com.akkaserverless.javasdk.impl.view

import com.akkaserverless.javasdk.view.HandlerContext

/**
 * INTERNAL API
 */
private[impl] final case class ViewException(viewId: String, commandName: String, message: String)
    extends RuntimeException(message)

/**
 * INTERNAL API
 */
private[impl] object ViewException {
  def apply(message: String): ViewException =
    ViewException(viewId = "", commandName = "", message)

  def apply(context: HandlerContext, message: String): ViewException =
    ViewException(context.viewId, context.commandName, message)

}