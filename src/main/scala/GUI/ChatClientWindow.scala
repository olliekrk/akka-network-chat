package GUI

import scalafx.application.JFXApp
import scalafx.application.JFXApp.PrimaryStage
import scalafx.scene.Scene
import scalafx.scene.paint.Color._

object ChatClientWindow extends JFXApp {

  stage = new PrimaryStage {
    width = 900
    height = 600
    title = "Akka Network Chat"
    scene = new Scene {
      fill = Black
    }
  }

  val loginDialog = LoginDialog(stage)
}