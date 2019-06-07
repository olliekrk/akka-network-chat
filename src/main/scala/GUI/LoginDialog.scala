package GUI

import GUI.LoginDialog.LoginData
import javafx.application.Platform
import javafx.scene.Node
import javafx.scene.layout.GridPane
import scalafx.geometry.Insets
import scalafx.scene.control.ButtonBar.ButtonData
import scalafx.scene.control.{ButtonType, Dialog, Label, TextField}
import scalafx.stage.Stage

import scala.annotation.tailrec


object LoginDialog {

  case class LoginData(username: String, hostname: String, port: Int)

  val loginButtonType = new ButtonType("Sign In", ButtonData.OKDone)

  def apply(stage: Stage): LoginDialog = new LoginDialog(stage, "", "", 0)
}

class LoginDialog(stage: Stage, var username: String, var hostname: String, var port: Int) {
  val dialog: Dialog[LoginData] = new Dialog[LoginData]() {
    initOwner(stage)
    title = "Sign In"
    headerText = "Hello, welcome to the chat!"
    dialogPane().getButtonTypes.addAll(LoginDialog.loginButtonType, ButtonType.Cancel)
  }

  val usernameField: TextField = new TextField() {
    promptText = "Username"
  }
  val hostnameField: TextField = new TextField() {
    promptText = "Hostname/IP"
  }
  val portField: TextField = new TextField() {
    promptText = "Port number"
  }

  val grid: GridPane = new GridPane() {
    setHgap(10)
    setVgap(10)
    setPadding(Insets(20, 100, 10, 10))

    add(new Label("Username:"), 0, 0)
    add(usernameField, 1, 0)
    add(new Label("Hostname:"), 0, 1)
    add(hostnameField, 1, 1)
    add(new Label("Port:"), 0, 2)
    add(portField, 1, 2)
  }

  val loginButton: Node = dialog.dialogPane().lookupButton(LoginDialog.loginButtonType)

  def checkLoginButton(): Unit = {
    val portText = portField.getText.trim
    val usernameWRONG = usernameField.getText.trim.isEmpty
    val hostnameWRONG = hostnameField.getText.trim.isEmpty
    val portWRONG = portText.isEmpty || !portText.forall(_.isDigit) || portText.length != 4

    loginButton.setDisable(usernameWRONG || hostnameWRONG || portWRONG)
  }

  def initializeDialog(): Unit = {
    loginButton.setDisable(true)
    Platform.runLater(() => usernameField.requestFocus())

    usernameField.text.onChange((_, _, _) => checkLoginButton())
    hostnameField.text.onChange((_, _, _) => checkLoginButton())
    portField.text.onChange((_, _, _) => checkLoginButton())

    dialog.dialogPane().setContent(grid)

    dialog.resultConverter = {
      case LoginDialog.loginButtonType => LoginData(usernameField.getText(), hostnameField.getText(), portField.getText().toInt)
      case ButtonType.Cancel => sys.exit(0)
    }

    waitForDialog()
  }

  @tailrec private def waitForDialog(): Unit = {
    dialog.showAndWait() match {
      case Some(LoginData(typedUsername, typedHostname, typedPort)) =>
        username = typedUsername
        hostname = typedHostname
        port = typedPort
      case _ => waitForDialog()
    }
  }
}
