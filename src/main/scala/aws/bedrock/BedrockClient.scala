package aws.bedrock

import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import software.amazon.awssdk.auth.credentials.{AwsBasicCredentials, StaticCredentialsProvider}
import software.amazon.awssdk.core.SdkBytes
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelRequest
import spray.json._

import java.nio.charset.StandardCharsets
import scala.util.Try

case class TitanRequest(inputText: String, textGenerationConfig: TitanGenerationConfig)
case class TitanGenerationConfig(maxTokenCount: Int, temperature: Double, topP: Double)
case class TitanResponse(results: List[TitanResult])
case class TitanResult(outputText: String)

object TitanJsonProtocol extends DefaultJsonProtocol {
  implicit val generationConfigFormat: RootJsonFormat[TitanGenerationConfig] = jsonFormat3(TitanGenerationConfig)
  implicit val requestFormat: RootJsonFormat[TitanRequest] = jsonFormat2(TitanRequest)
  implicit val resultFormat: RootJsonFormat[TitanResult] = jsonFormat1(TitanResult)
  implicit val responseFormat: RootJsonFormat[TitanResponse] = jsonFormat1(TitanResponse)
}

class BedrockClient(config: Config) extends LazyLogging {
  import TitanJsonProtocol._

  private val accessKey = config.getString("aws.credentials.access-key")
  private val secretKey = config.getString("aws.credentials.secret-key")
  private val region = Region.of(config.getString("aws.region"))
  private val modelId = "amazon.titan-text-lite-v1"

  private val credentials = StaticCredentialsProvider.create(
    AwsBasicCredentials.create(accessKey, secretKey)
  )

  private val runtimeClient = BedrockRuntimeClient.builder()
    .credentialsProvider(credentials)
    .region(region)
    .build()

  def generateText(prompt: String, maxTokens: Int = 100): Try[String] = {
    Try {
      logger.info(s"Generating text for prompt: $prompt")

      val request = TitanRequest(
        inputText = prompt,
        textGenerationConfig = TitanGenerationConfig(
          maxTokenCount = maxTokens,
          temperature = 0.7,
          topP = 0.9
        )
      )

      val jsonRequest = request.toJson.compactPrint

      val invokeRequest = InvokeModelRequest.builder()
        .modelId(modelId)
        .body(SdkBytes.fromString(jsonRequest, StandardCharsets.UTF_8))
        .build()

      val response = runtimeClient.invokeModel(invokeRequest)
      val responseBody = response.body().asUtf8String() // Using SDK's built-in UTF-8 conversion

      val titanResponse = responseBody.parseJson.convertTo[TitanResponse]

      titanResponse.results.headOption match {
        case Some(result) =>
          logger.info(s"Generated text successfully")
          result.outputText
        case None =>
          throw new RuntimeException("No output text generated")
      }
    }
  }

  def shutdown(): Unit = {
    runtimeClient.close()
  }
}