package GUI
import scalafx.Includes._
import scalafx.application.JFXApp
import scalafx.application.JFXApp.PrimaryStage
import scalafx.scene.Scene
import scalafx.scene.control.{Button, Label, ListView, ScrollPane, TextArea, TextField}
import scalafx.scene.input.{KeyCode, KeyEvent}
import scalafx.scene.layout.{BorderPane, HBox, VBox}
import scalafx.scene.text.{Text, TextFlow}

object Main extends JFXApp {

  stage = new PrimaryStage {
    title = "Scala Chat"
    scene = new Scene(600, 300){
      val border = new BorderPane()
      val text = new TextField()
      border.bottom = new HBox(new Button("ADD"), new Button("LEAVE"), text )
      border.left = new ListView(List("default room"))
      val textFlow = new TextFlow()
      border.center = new VBox( textFlow)
      onKeyPressed = (event: KeyEvent) => event.code match{
        case KeyCode.Enter =>
          println(text.text())
          val text1 = new Text(text.text() + "\n")
          textFlow.getChildren().add(text1)
          textFlow.getChildren().removeAll()
          textFlow.setPr
          text.clear()


        case _ => println(" xd ")
      }
      root = border
    }
  }
}
