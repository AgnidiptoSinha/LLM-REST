package models

import spray.json.{DefaultJsonProtocol, RootJsonFormat}

case class GenerateRequest(query: String)
case class GenerateResponse(text: String)
case class WelcomeResponse(message: String)

object JsonProtocol extends DefaultJsonProtocol {
  implicit val generateRequestFormat: RootJsonFormat[GenerateRequest] = jsonFormat1(GenerateRequest)
  implicit val generateResponseFormat: RootJsonFormat[GenerateResponse] = jsonFormat1(GenerateResponse)
  implicit val welcomeResponseFormat: RootJsonFormat[WelcomeResponse] = jsonFormat1(WelcomeResponse)
}