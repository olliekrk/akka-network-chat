package chat

import akka.actor.ActorSystem
import akka.serialization._
import akka.util.ByteString

import scala.collection.mutable
import scala.util.Try

object Message {

  val ClientMessage = "ClientMessage"
  val MessageFromOther = "MessageFromOther"
  val LeaveRoom = "LeaveRoom"
  val JoinRoom = "JoinRoom"
  val CreateRoom = "CreateRoom"
  val Unregister = "Unregister"
  val OtherClientMessage = "OtherClientMessage"
  val ChatNotification = "ChatNotification"
  val RoomNotification = "RoomNotification"
  val AcceptCreateRoom = "AcceptCreateRoom"
  val AcceptJoinRoom = "AcceptJoinRoom"

  object MessageRequest {
    def serialize(obj: AnyRef)(implicit system: ActorSystem): Try[Array[Byte]] = {
      val serialization = SerializationExtension(system)
      serialization.serialize(obj)
    }

    def deserialize[T](bytes: Array[Byte], cls: Class[T])(implicit system: ActorSystem): Try[T] = {
      val serialization = SerializationExtension(system)
      serialization.deserialize(bytes, cls)
    }

    def deserializeByteString(bytes: ByteString)(implicit system: ActorSystem): Try[MessageRequest] = {
      MessageRequest.deserialize(bytes.toArray, classOf[MessageRequest])
    }
  }

  class MessageRequest(name: String) extends mutable.HashMap[String, Any] {
    request = name

    def request: Any = {
      this ("requestName")
    }

    def request_=(name: String) {
      this ("requestName") = name
    }

    def serializeByteString(implicit system: ActorSystem): Try[ByteString] = {
      MessageRequest.serialize(this).flatMap((b: Array[Byte]) => {
        Try(ByteString(b))
      })
    }
  }

  def prepareRequest(category: String, content: Map[String, String]): MessageRequest = {
    val request = new MessageRequest(category)
    for ((key, value) <- content) request(key) = value
    request
  }

}
