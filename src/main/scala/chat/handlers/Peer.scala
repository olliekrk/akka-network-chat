package chat.handlers
import akka.actor.{Actor,ActorRef, ActorLogging, Props}
import akka.io.Tcp._
import akka.io.{IO, Tcp}

object Peer{
  def props(clientId: String, connection: ActorRef): Props = {
    Props(new Peer(clientId, connection))
  }
}

class Peer(clientId: String,
           connection: ActorRef,
           var clientName: String = "Unknown") extends Actor {

  override def receive: Receive = {
    case _ => println("something")
  }

}
