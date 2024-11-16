package service

import akka.actor.typed.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.ExceptionHandler
import akka.http.scaladsl.server.directives.DebuggingDirectives
import akka.http.scaladsl.settings.ServerSettings
import akka.http.scaladsl.settings.ServerSettings.timeoutsShortcut
import models.{GenerateRequest, JsonProtocol, WelcomeResponse}
import service.LLMService
import com.typesafe.scalalogging.LazyLogging
import models.JsonProtocol.generateRequestFormat

import scala.concurrent.duration.DurationInt
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

class RestServer(llmService: LLMService)(implicit system: ActorSystem[Nothing], ec: ExecutionContext)
  extends LazyLogging with SprayJsonSupport{

  // Import the JSON formats
  import models.JsonProtocol._

  implicit def myExceptionHandler: ExceptionHandler =
    ExceptionHandler {
      case ex: RuntimeException =>
        extractUri { uri =>
          logger.error(s"Request to $uri failed with: ${ex.getMessage}")
          complete(StatusCodes.InternalServerError -> s"Request failed: ${ex.getMessage}")
        }
    }

  val route = {
    pathSingleSlash {
      get {
        complete {WelcomeResponse(message= "Server is running! Post to '/generate'")}
      }
    } ~
    path("generate") {
      post {
        withRequestTimeout(2.minutes) {
          entity(as[GenerateRequest]) { request =>
            DebuggingDirectives.logRequest("generate") {
              complete {
                llmService.generateResponse(request.query)
              }
            }
          }
        }
      }
    }
  }

  def start(host: String, port: Int): Future[Http.ServerBinding] = {
    logger.info(s"Starting server at $host:$port")

    val bindingFuture = Http()
      .newServerAt(host, port)
      .bind(route)

    bindingFuture.onComplete {
      case Success(binding) =>
        val address = binding.localAddress
        logger.info(s"Server online at http://${address.getHostString}:${address.getPort}/")
      case Failure(ex) =>
        logger.error(s"Failed to bind server: ${ex.getMessage}")
        system.terminate()
    }

    bindingFuture
  }

  def shutdown(): Unit = {
    logger.info("Shutting down server")
    system.terminate()
  }
}