package chat

import akka.actor.ActorSystem
import akka.serialization._
import akka.util.ByteString

import scala.collection.mutable
import scala.util.Try

object Message {

  val ClientMessage = "ClientMessage"
  val MessageFromOther = "MessageFromOther"

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

    def request = {
      this("requestName")
    }

    def request_=(name: String) {
      this("requestName") = name
    }
    def serializeByteString(implicit system: ActorSystem): Try[ByteString] = {
      MessageRequest.serialize(this).flatMap((b: Array[Byte]) => {
        Try(ByteString(b))
      })
    }

//    def deserializeByteString(bytes: ByteString)(implicit system: ActorSystem): Try[MessageRequest] = {
//      MessageRequest.deserialize(bytes.toArray, classOf[MessageRequest])
//    }

  }

}