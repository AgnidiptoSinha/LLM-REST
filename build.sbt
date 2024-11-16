ThisBuild / version := "0.1.0-SNAPSHOT"
ThisBuild / scalaVersion := "2.12.18"

lazy val root = (project in file("."))
  .settings(
    name := "LLM-Microservice",
  )

val AkkaVersion = "2.6.20"  // older version compatible with Java 8
val AkkaHttpVersion = "10.2.10"  // older version compatible with Java 8
val dl4jVersion = "1.0.0-beta7"
val nd4jVersion = "1.0.0-beta7"
val jtokkitVersion = "1.1.0"

libraryDependencies ++= Seq(
  // Akka
  "com.typesafe.akka" %% "akka-actor-typed" % AkkaVersion,
  "com.typesafe.akka" %% "akka-stream" % AkkaVersion,
  "com.typesafe.akka" %% "akka-http" % AkkaHttpVersion,

  // JSON handling
  "com.typesafe.akka" %% "akka-http-spray-json" % AkkaHttpVersion,

  // Logging - using older versions
  "ch.qos.logback" % "logback-classic" % "1.2.11",  // older version for Java 8
  "com.typesafe.scala-logging" %% "scala-logging" % "3.9.4",  // older version

  // Configuration
  "com.typesafe" % "config" % "1.4.2",

  // Testing
  "org.scalatest" %% "scalatest" % "3.2.15" % Test,

//  // DL4J and ND4J and JTokkit
//  "org.deeplearning4j" % "deeplearning4j-core" % dl4jVersion,
//  "org.nd4j" % "nd4j-native-platform" % nd4jVersion,
//  "com.knuddels" % "jtokkit" % jtokkitVersion,

  "software.amazon.awssdk" % "bedrock" % "2.21.45",
  "software.amazon.awssdk" % "bedrockruntime" % "2.21.45",

  // Add these for HTTP/2 gRPC support
  "software.amazon.awssdk" % "lambda" % "2.21.45",
  "software.amazon.awssdk" % "netty-nio-client" % "2.21.45",
  "org.scala-lang.modules" %% "scala-java8-compat" % "0.8.0"
)

assembly / assemblyMergeStrategy := {
  case "reference.conf" => MergeStrategy.concat
  case "application.conf" => MergeStrategy.concat
  case PathList("META-INF", xs @ _*) => MergeStrategy.discard
  case x => MergeStrategy.first
}