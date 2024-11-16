//package predictor
//
//import com.knuddels.jtokkit.Encodings
//import com.knuddels.jtokkit.api.{Encoding, EncodingType, IntArrayList, ModelType}
//import org.deeplearning4j.nn.multilayer.MultiLayerNetwork
//import org.deeplearning4j.util.ModelSerializer
//import org.nd4j.linalg.factory.Nd4j
//import org.nd4j.linalg.api.ndarray.INDArray
//import org.nd4j.linalg.ops.transforms.Transforms
//import org.slf4j.LoggerFactory
//
//import scala.util.{Failure, Success, Try}
//import java.io.File
//import scala.collection.mutable.ArrayBuffer
//
//object LLMPredictor {
//  private val logger = LoggerFactory.getLogger(getClass)
//  private val registry = Encodings.newDefaultEncodingRegistry()
//  private val encoding: Encoding = registry.getEncodingForModel(ModelType.GPT_4)
//
//  private val InputSize = 512
//  private val PaddingToken = 0
//  private val MaxTokenValue = 50000  // Limit token values to prevent invalid characters
//  private val Temperature = 0.7f
//
//  def generateText(
//                    model: MultiLayerNetwork,
//                    contextString: String,
//                    maxTokens: Int
//                  ): Try[String] = {
//    Try {
//      logger.info(s"Generating text for context: $contextString")
//
//      // Initial context tokenization
//      val initialTokens = scalaList(encoding.encode(contextString))
//      logger.debug(s"Initial tokens: ${initialTokens.mkString(", ")}")
//
//      val generatedTokens = ArrayBuffer[Int]()
//      var currentTokens = initialTokens
//
//      var tokensGenerated = 0
//      var toContinue = true
//      while (tokensGenerated < maxTokens && toContinue) {
//        // Prepare input
//        val inputTensor = preprocessInput(currentTokens)
//
//        // Get model output
//        val output = model.output(inputTensor)
//
//        // Get next token
//        val nextToken = getNextToken(output)
//        logger.debug(s"Generated token: $nextToken")
//
//        var toContinue = true
//
//        // Validate token
//        if (nextToken < 0 || nextToken >= MaxTokenValue && toContinue) {
//          logger.warn(s"Invalid token generated: $nextToken, skipping")
//          tokensGenerated += 1
//          if (tokensGenerated >= maxTokens) toContinue = false
//        }
//
//        generatedTokens.append(nextToken)
//
//        // Update for next iteration
//        currentTokens = (currentTokens ++ List(nextToken)).takeRight(InputSize)
//        tokensGenerated += 1
//
//        // Try decoding current output to check validity
//        val currentOutput = new String(encoding.decode(asIntArrayList(generatedTokens.toList)))
//        if (currentOutput.matches(".*[.!?]\\s*$")) toContinue = false
//      }
//
//      // Decode final result
//      val generatedText = decodeTokens(generatedTokens.toList)
//      logger.info(s"Generated text: $generatedText")
//
//      formatResponse(contextString, generatedText)
//    }
//  }
//
//  private def preprocessInput(tokens: List[Int]): INDArray = {
//    val inputArray = Nd4j.zeros(1, InputSize)
//    val paddedTokens = if (tokens.length >= InputSize) {
//      tokens.takeRight(InputSize)
//    } else {
//      List.fill(InputSize - tokens.length)(PaddingToken) ++ tokens
//    }
//
//    for (i <- paddedTokens.indices) {
//      inputArray.putScalar(Array(0, i), paddedTokens(i).toFloat)
//    }
//    inputArray
//  }
//
//  private def getNextToken(output: INDArray): Int = {
//    // Apply temperature scaling
//    val scaledOutput = output.div(Temperature)
//
//    // Get probabilities
//    val expValues = Transforms.exp(scaledOutput)
//    val sumExp = expValues.sumNumber().doubleValue()
//    val probs = expValues.div(sumExp)
//
//    // Sample from top K (e.g., top 40) to prevent unlikely tokens
//    val topK = 40
//    val probArray = probs.data().asFloat()
//    val topKIndices = probArray.zipWithIndex
//      .sortBy(-_._1)
//      .take(topK)
//      .map(_._2)
//
//    // Sample from top K
//    val rand = scala.util.Random.nextDouble()
//    var cumSum = 0.0
//    for (idx <- topKIndices) {
//      cumSum += probArray(idx)
//      if (cumSum > rand) {
//        return idx
//      }
//    }
//
//    topKIndices.head // fallback to most likely token
//  }
//
//  private def decodeTokens(tokens: List[Int]): String = {
//    try {
//      val validTokens = tokens.filter(t => t > 0 && t < MaxTokenValue)
//      val tokenArray = asIntArrayList(validTokens)
//      new String(encoding.decode(tokenArray))
//    } catch {
//      case e: Exception =>
//        logger.error(s"Error decoding tokens: ${e.getMessage}")
//        ""
//    }
//  }
//
//  private def formatResponse(context: String, generated: String): String = {
//    val combinedText = (context + " " + generated).trim
//      .replaceAll("\\s+", " ")
//      .replaceAll("\\s*([.,!?])\\s*", "$1 ")
//      .trim
//
//    if (!combinedText.matches(".*[.!?]$")) {
//      combinedText + "."
//    } else {
//      combinedText
//    }
//  }
//
//  def loadModel(modelPath: String): Try[MultiLayerNetwork] = {
//    Try {
//      logger.info(s"Loading model from $modelPath")
//      ModelSerializer.restoreMultiLayerNetwork(new File(modelPath))
//    }
//  }
//
//  private def scalaList(x: IntArrayList): List[Int] = {
//    Iterator.range(0, x.size()).map(x.get).toList
//  }
//
//  private def asIntArrayList(x: List[Int]): IntArrayList = {
//    val arrayList = new IntArrayList()
//    x.foreach(arrayList.add)
//    arrayList
//  }
//}