package GUI

import GUI.LoginDialog.LoginData
import javafx.application.Platform
import javafx.scene.Node
import javafx.scene.layout.GridPane
import scalafx.geometry.Insets
import scalafx.scene.control.ButtonBar.ButtonData
import scalafx.scene.control.{ButtonType, Dialog, Label, TextField}
import scalafx.stage.Stage


object LoginDialog {

  case class LoginData(username: String, hostname: String)

  // custom login button
  val loginButtonType = new ButtonType("Login In", ButtonData.OKDone)

  def apply(stage: Stage): LoginDialog = new LoginDialog(stage, "", "")
}

class LoginDialog(stage: Stage, var username: String, var hostname: String) {
  val dialog: Dialog[LoginData] = new Dialog[LoginData]() {
    initOwner(stage)
    title = "Login In"
    headerText = "Hello, welcome to the chat!"
  }

  val usernameField: TextField = new TextField() {
    promptText = "Username"
  }
  val hostnameField: TextField = new TextField() {
    promptText = "Hostname/IP"
  }

  // insert buttons to the dialog
  dialog.dialogPane().getButtonTypes addAll(LoginDialog.loginButtonType, ButtonType.Cancel)

  val grid: GridPane = new GridPane() {
    setHgap(10)
    setVgap(10)
    setPadding(Insets(20, 100, 10, 10))

    add(new Label("Username:"), 0, 0)
    add(usernameField, 1, 0)
    add(new Label("Hostname:"), 0, 1)
    add(hostnameField, 1, 1)
  }

  val loginButton: Node = dialog.dialogPane().lookupButton(LoginDialog.loginButtonType)
  loginButton.setDisable(true)

  usernameField.text.onChange { (_, _, newUsername) => loginButton.setDisable(newUsername.trim().isEmpty) }

  dialog.dialogPane().setContent(grid)

  Platform.runLater(() => usernameField.requestFocus())

  dialog.resultConverter = {
    case LoginDialog.loginButtonType => LoginData(usernameField.getText(), hostnameField.getText())
    case _ => null
  }

  dialog.showAndWait() match {
    case Some(LoginData(u, h)) => username = u; hostname = h
    case _ =>
  }
}
