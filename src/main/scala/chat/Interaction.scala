package chat

import akka.actor.{Actor, ActorRef}
case class UserMessage(string: String)

class Interaction  extends Actor {

  import context.system

  val client: ActorRef = system.actorOf(ChatClient.props(Main.serverAddress, self), "client")

  client ! UserStartConnect()

  override def receive: Receive = {
    //starting from
    case UserMessage(name) =>
      println("Got name!" + name)
      if (name != null) {
        client ! CurrentUserName(name)
        context.become({
          case InputMessage(message) =>
            println("input message: " + message)
            client ! InputMessage(message)
        })
      } else {
        println("Wrong name!")
        // just for now
        sys.exit(1)
      }
  }
}

