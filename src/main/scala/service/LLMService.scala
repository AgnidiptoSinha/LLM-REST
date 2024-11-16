package service

import aws.bedrock.BedrockClient
import aws.lambda.GrpcLambdaClient
import com.typesafe.scalalogging.LazyLogging
import models.{GenerateRequest, GenerateResponse}
//import org.deeplearning4j.nn.multilayer.MultiLayerNetwork
//import predictor.LLMPredictor
import com.typesafe.config.{Config, ConfigFactory}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

// Fallback plan to use the LLM Service in case Bedrock Fails
//class LocalLLMService(modelPath: String, maxTokens: Int)(implicit ec: ExecutionContext) extends LazyLogging {
//
//  // Load model at service startup
//  private val modelTry = LLMPredictor.loadModel(modelPath)
//  modelTry match {
//    case Success(_) => logger.info("Local Model loaded successfully")
//    case Failure(e) => logger.error(s"Failed to load local model: ${e.getMessage}")
//  }
//
//  def generateResponse(query: String): Future[GenerateResponse] = {
//    Future {
//      modelTry match {
//        case Success(model) =>
//          LLMPredictor.generateText(model, query, maxTokens) match {
//            case Success(generatedText) =>
//              logger.info(s"Generated response for query from local model: $query")
//              GenerateResponse(generatedText)
//            case Failure(e) =>
//              logger.error(s"Text generation from local model failed: ${e.getMessage}")
//              throw new RuntimeException(s"Text generation from local model failed: ${e.getMessage}")
//          }
//        case Failure(e) =>
//          logger.error(s"Local Model not available: ${e.getMessage}")
//          throw new RuntimeException(s"Local Model not available: ${e.getMessage}")
//      }
//    }
//  }
//}

//Fallback plan to use Bedrock directly without Lambda in case of failure.
class BedrockLLMService(maxTokens: Int, config: Config)(implicit  ec:ExecutionContext) extends LazyLogging {

  private val bedrockClient = new BedrockClient(config)

  def generateResponse(query: String): Future[GenerateResponse] = {
    Future {
      bedrockClient.generateText(query, maxTokens) match {
        case Success(generatedText) =>
          logger.info(s"Generated response for query: $query")
          GenerateResponse(generatedText)
        case Failure(e) =>
          logger.error(s"Text generation failed: ${e.getMessage}")
          throw new RuntimeException(s"Text generation failed: ${e.getMessage}")
      }
    }
  }

  def shutdown(): Unit = {
    bedrockClient.shutdown()
  }
}

class LambdaLLMService(config: Config)(implicit ec: ExecutionContext) extends LazyLogging {

  private val grpcClient = new GrpcLambdaClient(config)

  def generateResponse(query: String): Future[GenerateResponse] = {
    logger.info(s"Generating response for query: $query")

    grpcClient.invokeLambda(query, config.getInt("llm.model.maxTokens"))
      .map(text => GenerateResponse(text))
      .recover { case e: Exception =>
        logger.error(s"Text generation failed: ${e.getMessage}")
        throw e
      }
  }
  def shutdown(): Unit = {
    grpcClient.shutdown()
  }
}

class LLMService(implicit ec: ExecutionContext) extends LazyLogging {
  private val config = ConfigFactory.load()
  private val modelPath = config.getString("llm.model.path")
  private val maxTokens = config.getInt("llm.model.maxTokens")

  // fallback plan to use Locally trained LLM
//  private val localLLMService = new LocalLLMService(modelPath, maxTokens)
//  def generateLocalResponse(query: String): Future[GenerateResponse] = {
//    localLLMService.generateResponse(query)
//  }

//  // fallback plan to use Bedrock directly
//  private val bedrockLLMService = new BedrockLLMService(maxTokens, config)
//  def generateBedrockResponse(query: String): Future[GenerateResponse] = {
//    bedrockLLMService.generateResponse(query)
//  }

  // main function to use Lambda with grpc
  private val lambdaLLMService = new LambdaLLMService(config)
  def generateResponse(query: String) : Future[GenerateResponse] = {
    lambdaLLMService.generateResponse(query)
  }

  def shutdown(): Unit = {
//    bedrockLLMService.shutdown()
    lambdaLLMService.shutdown()
  }
}