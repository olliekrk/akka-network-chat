package chat.handlers

import java.text.SimpleDateFormat
import java.util.Calendar

import akka.actor.{Actor, ActorLogging, Props}
import chat.handlers.ClientHandler._
import scalafx.scene.control.TextArea

import scala.collection.mutable

object ClientHandler {

  sealed trait ClientRequest

  case class ChatMessage(senderName: String, message: String, room: String) extends ClientRequest

  case class ChatNotification(message: String) extends ClientRequest

  def props(chatOutputAreas: mutable.Map[String, TextArea]) = Props(new ClientHandler(chatOutputAreas))

}

class ClientHandler(chatOutputAreas: mutable.Map[String, TextArea]) extends Actor with ActorLogging {
  override def receive: Receive = {
    case ChatMessage(name, message, room) =>
      println("ROOOOOM : " + room)
      val today = Calendar.getInstance().getTime
      // create the date/time formatters
      val minuteFormat = new SimpleDateFormat("mm")
      val hourFormat = new SimpleDateFormat("hh")
      val amPmFormat = new SimpleDateFormat("a")
      val currentHour = hourFormat.format(today)      // 12
      val currentMinute = minuteFormat.format(today)  // 29
      val amOrPm = amPmFormat.format(today) //PM
      chatOutputAreas(room).appendText(s"[$currentHour:$currentMinute $amOrPm] Message from $name:\n\t$message")
      // case ChatNotification(message) => chatOutput.appendText(s"Notification:\n\t$message")
    case _ => //chatOutput.appendText("Unknown message to received")
  }
}
