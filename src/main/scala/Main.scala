
import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import service.RestServer
import service.LLMService
import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.LazyLogging

object Main extends App with LazyLogging {

  val config = ConfigFactory.load()
  val host = config.getString("llm.server.host")
  val port = config.getInt("llm.server.port")

  implicit val system: ActorSystem[Nothing] = ActorSystem(Behaviors.empty, "llm-system")
  implicit val executionContext = system.executionContext

  val llmService = new LLMService()
  val server = new RestServer(llmService)

  server.start(host, port)

  sys.addShutdownHook {
    server.shutdown()
  }
}