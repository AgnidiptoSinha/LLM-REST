package aws.lambda

import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import software.amazon.awssdk.core.SdkBytes
import software.amazon.awssdk.services.lambda.model.InvokeRequest
import software.amazon.awssdk.auth.credentials.{AwsBasicCredentials, StaticCredentialsProvider}
import software.amazon.awssdk.http.nio.netty.{Http2Configuration, NettyNioAsyncHttpClient}
import software.amazon.awssdk.services.lambda.LambdaAsyncClient
import spray.json._
import DefaultJsonProtocol._

import scala.compat.java8.FutureConverters._
import scala.concurrent.{ExecutionContext, Future}

class GrpcLambdaClient(config: Config)(implicit ec: ExecutionContext) extends LazyLogging {

  // Get credentials from config
  private val accessKey = config.getString("aws.credentials.access-key")
  private val secretKey = config.getString("aws.credentials.secret-key")
  private val region = config.getString("aws.region")

  // Create credentials provider
  private val credentials = StaticCredentialsProvider.create(
    AwsBasicCredentials.create(accessKey, secretKey)
  )

  // Configure HTTP/2 client for gRPC
  private val http2Config = Http2Configuration.builder()
    .initialWindowSize(1048576) // 1MB
    .build()

  private val asyncHttpClient = NettyNioAsyncHttpClient.builder()
    .http2Configuration(http2Config)
    .maxConcurrency(100)
    .build()

  // Create async Lambda client with HTTP/2 and credentials
  private val lambdaClient = LambdaAsyncClient.builder()
    .region(software.amazon.awssdk.regions.Region.of(region))
    .credentialsProvider(credentials)
    .httpClient(asyncHttpClient)
    .build()

  private val functionName = config.getString("llm.aws.lambda.function-name")

  def invokeLambda(prompt: String, maxTokens: Int): Future[String] = {
    logger.info(s"Invoking Lambda via gRPC with prompt: $prompt")

    // Create payload
    val payload = JsObject(
      "body" -> JsString(
        JsObject(
          "prompt" -> JsString(prompt),
          "maxTokens" -> JsNumber(maxTokens)
        ).compactPrint
      )
    ).compactPrint

    val request = InvokeRequest.builder()
      .functionName(functionName)
      .payload(SdkBytes.fromUtf8String(payload))
      .build()

    // Convert Java Future to Scala Future
    lambdaClient.invoke(request).toScala.map { response =>
      if (response.statusCode() == 200) {
        val responseJson = response.payload().asUtf8String().parseJson.asJsObject
        val body = responseJson.fields("body").convertTo[String].parseJson.asJsObject
        body.fields("text").convertTo[String]
      } else {
        throw new RuntimeException(s"Lambda invocation failed with status ${response.statusCode()}")
      }
    }.recover {
      case e: Exception =>
        logger.error(s"Error invoking Lambda: ${e.getMessage}", e)
        throw e
    }
  }

  def shutdown(): Unit = {
    asyncHttpClient.close()
    lambdaClient.close()
  }
}