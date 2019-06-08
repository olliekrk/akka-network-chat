package chat

import akka.actor.ActorRef
import chat.Message.MessageRequest

trait RequestSerialization {

  def serializeAndWrite(request: MessageRequest, connectionActor: ActorRef): Unit

  def handleDeserializedRequest(connectionActor: ActorRef, request: MessageRequest): Unit

}
