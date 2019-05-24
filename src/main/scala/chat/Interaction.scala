package chat

import akka.actor.{Actor, ActorRef}
import chat.{ChatClient, ChatServer, Interaction, UserMessage}
import chat.Main
case class UserMessage(string: String)

class Interaction  extends Actor{
  import context.system
  val client: ActorRef = system.actorOf(ChatClient.props( Main.serverAddress, self), "client")
  override def receive: Receive = {
    //starting from
    case UserMessage(name) =>
      client ! CurrentUserName(name)

  }
}
