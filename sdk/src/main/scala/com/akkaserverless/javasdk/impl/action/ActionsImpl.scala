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

package com.akkaserverless.javasdk.impl.action

import java.util.Optional

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.scaladsl.{Sink, Source}
import com.akkaserverless.javasdk
import com.akkaserverless.javasdk.action._
import com.akkaserverless.javasdk.impl._
import com.akkaserverless.javasdk._
import com.akkaserverless.javasdk.impl.reply.ReplySupport
import com.akkaserverless.javasdk.reply.{FailureReply, ForwardReply, MessageReply}
import com.akkaserverless.protocol.action.{ActionCommand, ActionResponse, Actions}
import com.akkaserverless.protocol.component.Failure
import com.google.protobuf.any.{Any => ScalaPbAny}
import com.google.protobuf.{Descriptors, Any => JavaPbAny}
import scala.compat.java8.FutureConverters._
import scala.concurrent.Future

final class ActionService(val factory: ActionFactory,
                          override val descriptor: Descriptors.ServiceDescriptor,
                          val anySupport: AnySupport)
    extends Service {

  override def resolvedMethods: Option[Map[String, ResolvedServiceMethod[_, _]]] =
    factory match {
      case resolved: ResolvedEntityFactory => Some(resolved.resolvedMethods)
      case _ => None
    }

  override final val componentType = Actions.name
}

final class ActionsImpl(_system: ActorSystem, services: Map[String, ActionService], rootContext: Context)
    extends Actions {

  import _system.dispatcher
  implicit val system: ActorSystem = _system

  private object creationContext extends ActionCreationContext {
    override def serviceCallFactory(): ServiceCallFactory = rootContext.serviceCallFactory()
  }

  private def toJavaPbAny(any: Option[ScalaPbAny]) =
    any.fold(JavaPbAny.getDefaultInstance)(ScalaPbAny.toJavaProto)

  private def replyToActionResponse(msg: javasdk.Reply[JavaPbAny]): ActionResponse = {
    val response = msg match {
      case message: MessageReply[JavaPbAny] =>
        ActionResponse.Response.Reply(ReplySupport.asProtocol(message))
      case forward: ForwardReply[JavaPbAny] =>
        ActionResponse.Response.Forward(ReplySupport.asProtocol(forward))
      case failure: FailureReply[JavaPbAny] =>
        ActionResponse.Response.Failure(Failure(description = failure.description()))
      // ie, NoReply
      case _ => ActionResponse.Response.Empty
    }
    ActionResponse(response, ReplySupport.effectsFrom(msg))
  }

  /**
   * Handle a unary command.
   * The input command will contain the service name, command name, request metadata and the command
   * payload. The reply may contain a direct reply, a forward or a failure, and it may contain many
   * side effects.
   */
  override def handleUnary(in: ActionCommand): Future[ActionResponse] =
    services.get(in.serviceName) match {
      case Some(service) =>
        val context = createContext(in)
        service.factory
          .create(creationContext)
          .handleUnary(in.name, MessageEnvelope.of(toJavaPbAny(in.payload), context.metadata()), context)
          .toScala
          .map(replyToActionResponse)
      case None =>
        Future.successful(
          ActionResponse(ActionResponse.Response.Failure(Failure(0, "Unknown service: " + in.serviceName)))
        )
    }

  /**
   * Handle a streamed in command.
   * The first message in will contain the request metadata, including the service name and command
   * name. It will not have an associated payload set. This will be followed by zero to many messages
   * in with a payload, but no service name or command name set.
   * The semantics of stream closure in this protocol map 1:1 with the semantics of gRPC stream closure,
   * that is, when the client closes the stream, the stream is considered half closed, and the server
   * should eventually, but not necessarily immediately, send a response message with a status code and
   * trailers.
   * If however the server sends a response message before the client closes the stream, the stream is
   * completely closed, and the client should handle this and stop sending more messages.
   * Either the client or the server may cancel the stream at any time, cancellation is indicated
   * through an HTTP2 stream RST message.
   */
  override def handleStreamedIn(in: Source[ActionCommand, NotUsed]): Future[ActionResponse] =
    in.prefixAndTail(1)
      .runWith(Sink.head)
      .flatMap {
        case (Nil, _) =>
          Future.successful(
            ActionResponse(
              ActionResponse.Response.Failure(
                Failure(
                  0,
                  "Akka Serverless protocol failure: expected command message with service name and command name, but got empty stream"
                )
              )
            )
          )
        case (Seq(call), messages) =>
          services.get(call.serviceName) match {
            case Some(service) =>
              service.factory
                .create(creationContext)
                .handleStreamedIn(
                  call.name,
                  messages.map { message =>
                    val metadata = new MetadataImpl(message.metadata.map(_.entries.toVector).getOrElse(Nil))
                    MessageEnvelope.of(toJavaPbAny(message.payload), metadata)
                  }.asJava,
                  createContext(call)
                )
                .toScala
                .map(replyToActionResponse)
            case None =>
              Future.successful(
                ActionResponse(ActionResponse.Response.Failure(Failure(0, "Unknown service: " + call.serviceName)))
              )
          }
      }

  /**
   * Handle a streamed out command.
   * The input command will contain the service name, command name, request metadata and the command
   * payload. Zero or more replies may be sent, each containing either a direct reply, a forward or a
   * failure, and each may contain many side effects. The stream to the client will be closed when the
   * this stream is closed, with the same status as this stream is closed with.
   * Either the client or the server may cancel the stream at any time, cancellation is indicated
   * through an HTTP2 stream RST message.
   */
  override def handleStreamedOut(in: ActionCommand): Source[ActionResponse, NotUsed] =
    services.get(in.serviceName) match {
      case Some(service) =>
        val context = createContext(in)
        service.factory
          .create(creationContext)
          .handleStreamedOut(in.name, MessageEnvelope.of(toJavaPbAny(in.payload), context.metadata()), context)
          .asScala
          .map(replyToActionResponse)
      case None =>
        Source.single(ActionResponse(ActionResponse.Response.Failure(Failure(0, "Unknown service: " + in.serviceName))))
    }

  /**
   * Handle a full duplex streamed command.
   * The first message in will contain the request metadata, including the service name and command
   * name. It will not have an associated payload set. This will be followed by zero to many messages
   * in with a payload, but no service name or command name set.
   * Zero or more replies may be sent, each containing either a direct reply, a forward or a failure,
   * and each may contain many side effects.
   * The semantics of stream closure in this protocol map 1:1 with the semantics of gRPC stream closure,
   * that is, when the client closes the stream, the stream is considered half closed, and the server
   * should eventually, but not necessarily immediately, close the streamage with a status code and
   * trailers.
   * If however the server closes the stream with a status code and trailers, the stream is immediately
   * considered completely closed, and no further messages sent by the client will be handled by the
   * server.
   * Either the client or the server may cancel the stream at any time, cancellation is indicated
   * through an HTTP2 stream RST message.
   */
  override def handleStreamed(in: Source[ActionCommand, NotUsed]): Source[ActionResponse, NotUsed] =
    in.prefixAndTail(1)
      .flatMapConcat {
        case (Nil, _) =>
          Source.single(
            ActionResponse(
              ActionResponse.Response.Failure(
                Failure(
                  0,
                  "Akka Serverless protocol failure: expected command message with service name and command name, but got empty stream"
                )
              )
            )
          )
        case (Seq(call), messages) =>
          services.get(call.serviceName) match {
            case Some(service) =>
              service.factory
                .create(creationContext)
                .handleStreamed(
                  call.name,
                  messages.map { message =>
                    val metadata = new MetadataImpl(message.metadata.map(_.entries.toVector).getOrElse(Nil))
                    MessageEnvelope.of(toJavaPbAny(message.payload), metadata)
                  }.asJava,
                  createContext(call)
                )
                .asScala
                .map(replyToActionResponse)
            case None =>
              Source.single(
                ActionResponse(ActionResponse.Response.Failure(Failure(0, "Unknown service: " + call.serviceName)))
              )
          }
      }

  private def createContext(in: ActionCommand): ActionContext = {
    val metadata = new MetadataImpl(in.metadata.map(_.entries.toVector).getOrElse(Nil))
    new ActionContextImpl(metadata)
  }

  class ActionContextImpl(override val metadata: Metadata) extends ActionContext {
    override val serviceCallFactory: ServiceCallFactory = rootContext.serviceCallFactory()

    override def eventSubject(): Optional[String] =
      if (metadata.isCloudEvent)
        metadata.asCloudEvent().subject()
      else
        Optional.empty()
  }
}

case class MessageEnvelopeImpl[T](payload: T, metadata: Metadata) extends MessageEnvelope[T]
