# Hello Chat
Messaging application written in Scala. Based on Akka actor model and TCP/IP protocol for commmunication.
Eventually, with GUI made in ScalaFX.

### How to run

* You have to set hostname and port of server (by default it is "localhost" and "8888"). 
* When server is running you can connect to it as client.

<p align="center">
  <img src="https://user-images.githubusercontent.com/37248877/59854016-57329b80-9372-11e9-85cb-0497903c616d.png" alt="Connection screen"/>
</p>

### Project capabilities

Client can create new room, join existing room, communicate with people.

<p align="center">
  <img src="https://user-images.githubusercontent.com/37248877/59854506-5b12ed80-9373-11e9-92d0-2cfc4c2c5f8e.png" alt="chatscreen"/>
</p>

### Constraints

* There cannot be two rooms with the same name.
* There cannot be any two users with the same name. 
* User cannot join room that does not exist.

<p align="center">
  <img src="https://user-images.githubusercontent.com/37248877/59854831-f015e680-9373-11e9-99f6-432ee824e3f2.png" alt="create_room constaint"/>
</p>

<p align="center">
  <img src="https://user-images.githubusercontent.com/37248877/59854833-f015e680-9373-11e9-84b0-add2c633dde8.png" alt="create_room constaint"/>
</p>

### Contributors

* Olgierd Królik [olliekrk](https://github.com/olliekrk)
* Przemysław Jabłecki [okarinbro](https://github.com/okarinbro)
